package com.solvedbycode.mirror

/* Everything below is pending final implementation. Generics issues are likely posing a problem. Current implementation
 * fails when the Get call returns a None, because the cred was never stored in the first place */

import akka.actor.{ActorRef, Props, Actor, ActorLogging}
import akka.pattern.ask
import com.google.api.client.util.store.{MemoryDataStoreFactory, DataStoreFactory, DataStore}
import java.{io, util}
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import scala.collection.mutable
import scala.concurrent.duration._
import scala.collection.JavaConverters._

import com.solvedbycode.bambooglassware.helpers.Actors
import akka.util.Timeout
import scala.concurrent.Await
import akka.event.Logging


object CredentialDataStoreFactory {
  val instance: CredentialDataStoreFactory = new CredentialDataStoreFactory
  def apply() = instance
}

class CredentialDataStoreFactory extends DataStoreFactory {
  val dataStores = mutable.Map[String, AnyRef]()

  def getDataStore[V <: io.Serializable](id: String): DataStore[V] = {
    //log.debug(s"Getting a data store. Current map is [ ${dataStores.map(ds => s"${ds._1} -> ${ds._2}")} ]")
    dataStores.getOrElseUpdate(id, CredentialDataStoreActorProxy[V](this)).asInstanceOf[DataStore[V]]
  }
}

object CredentialDataStoreActorProxy {
  def apply[V <: io.Serializable](factory: CredentialDataStoreFactory)(implicit system: ActorSystem) = {
    val actor = system.actorOf(Props(new CredentialDataStoreActor[V]))
    new CredentialDataStoreActorProxy[V](actor, factory)
  }
}

class CredentialDataStoreActorProxy[V <: io.Serializable](val actor: ActorRef, val factory: CredentialDataStoreFactory) extends DataStore[V] with SbcLogging {
  import CredentialDataStoreProtocol._
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val defaultTimeout = Timeout(2 seconds)

  def getDataStoreFactory: DataStoreFactory = factory

  def getId: String =  Await.result((actor ? GetId).mapTo[Id].map(_.value), 2 seconds)

  def size(): Int = Await.result((actor ? GetSize).mapTo[Size].map(_.size), 2 seconds)

  def isEmpty: Boolean = Await.result((actor ? IsEmpty).map {
    case NotEmpty => false
    case Empty => true
  }, 2 seconds)

  def containsKey(key: String): Boolean = Await.result((actor ? ContainsKey(key)).map {
    case KeyFound(key) => true
    case KeyNotFound(key) => false
  }, 2 seconds)

  def containsValue(value: V): Boolean = Await.result((actor ? ContainsValue(value)).map {
    case ValueFound(v) => true
    case ValueNotFound(v) => false
  }, 2 seconds)

  def keySet(): util.Set[String] = Await.result((actor ? GetKeySet).mapTo[KeySet].map(_.keys.asJava), 2 seconds)

  def values(): util.Collection[V] = Await.result((actor ? GetValues).mapTo[Values[V]].map(_.values.asJavaCollection), 2 seconds)

  def get(key: String): V = {
    log.debug(s"Coming into the get call with the key [ ${key} ]")
     val result = Await.result((actor ? Get(key)).mapTo[Value[V]], 2 seconds)
     log.debug(s"... was able to get a Value back, its string form is [ ${result.toString} ] and the value is [ ${result.value} ]")
     result.value.getOrElse(null.asInstanceOf[V])
  }

  def set(key: String, value: V): DataStore[V] = Await.result((actor ? Set(key, value)).mapTo[RecordSet].map(x => this), 2 seconds)

  def clear(): DataStore[V] = Await.result((actor ? Clear).map{
    case DataStoreCleared => this
    case _ => throw new Throwable("Couldn't clear the data store")
  }, 2 seconds)

  def delete(key: String): DataStore[V] = Await.result((actor ? Delete(key)).mapTo[RecordDeleted].map(x => this), 2 seconds)
}

object CredentialDataStoreProtocol {
  case object GetId
  case object IsEmpty
  case object GetSize
  case class ContainsKey(key: String)
  case class ContainsValue[V <: io.Serializable](value: V)
  case object GetKeySet
  case object GetValues
  case class Get(key: String)
  case class Set[V <: io.Serializable](key: String, value: V)
  case object Clear
  case class Delete(key: String)

  case class Size(size: Int)
  case class Id(value: String)
  case object Empty
  case object NotEmpty
  case class KeyFound(key: String)
  case class KeyNotFound(key: String)
  case class ValueFound[V <: io.Serializable](value: V)
  case class ValueNotFound[V <: io.Serializable](value: V)
  case class KeySet(keys: collection.Set[String])
  case class Values[V <: io.Serializable](values: collection.Seq[V])
  case class Value[V <: io.Serializable](value: Option[V])
  case object DataStoreCleared
  case class RecordDeleted(key: String)
  case class RecordSet(key: String)
}

class CredentialDataStoreActor[V <: io.Serializable] extends Actor with ActorLogging {
  import CredentialDataStoreProtocol._

  val credentialMap = mutable.Map[String, V]()

  def receive = {
    case GetId => sender ! context.self.path
    case IsEmpty => if (credentialMap.isEmpty) sender ! Empty else sender ! NotEmpty
    case GetSize => sender ! Size(credentialMap.size)
    case ContainsKey(key) => if (credentialMap.contains(key)) sender ! KeyFound(key) else sender ! KeyNotFound(key)
    case ContainsValue(value) => if (credentialMap.values.exists(_ == value)) sender ! ValueFound(value) else sender ! ValueNotFound(value)
    case GetKeySet => sender ! KeySet(credentialMap.keySet)
    case GetValues => sender ! Values(credentialMap.values.toSeq)
    case Get(key) => sender ! Value(credentialMap.get(key))
    case Set(key, value: V) => (credentialMap.put(key, value)); sender ! RecordSet(key)
    case Clear => credentialMap.clear(); sender ! DataStoreCleared
    case Delete(key) => if (credentialMap.remove(key).isDefined) sender ! RecordDeleted(key)
    case _ => throw new NotImplementedException
  }
}
