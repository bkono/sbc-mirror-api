package com.solvedbycode.mirror

import akka.actor._
import akka.persistence._
import akka.pattern.ask
import com.google.api.client.auth.oauth2.{Credential, CredentialStore}
import scala.collection._
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.util.Timeout

object CredentialStoreProxy {
  var proxy: Option[CredentialStoreProxy] = None
  def apply()(implicit system: ActorSystem) = {
    if (proxy.isEmpty) proxy = Some(new CredentialStoreProxy())
    proxy.get
  }
}

class CredentialStoreProxy()(implicit system: ActorSystem) extends CredentialStore {
  import CredentialStoreProtocol._
  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val defaultTimeout = Timeout(2 seconds)
  val actor = system.actorOf(Props[CredentialStoreActor], "CredentialStore")

  def load(userId: String, credential: Credential): Boolean = Await.result((actor ? Get(userId)).map {
    case FoundCredential(c) => {
      credential.setAccessToken(c.accessToken)
      credential.setExpirationTimeMilliseconds(c.expirationTimeMillis)
      credential.setRefreshToken(c.refreshToken)
      true
    }
    case CredentialNotFound(userId) => false
  }, 2 seconds)

  def store(userId: String, credential: Credential): Unit = actor ! Store(userId, credential)

  def delete(userId: String, credential: Credential): Unit = actor ! Delete(userId)
}

case class MemoryPersistedCredential(accessToken: String, refreshToken: String, expirationTimeMillis: Long)

object CredentialStoreProtocol {
  implicit def credential2MemoryPersistedCredential(credential: Credential): MemoryPersistedCredential = MemoryPersistedCredential(credential.getAccessToken, credential.getRefreshToken(), credential.getExpirationTimeMilliseconds)

  case class Get(userId: String)
  case class Store(userId: String, credential: MemoryPersistedCredential)
  case class Delete(userId: String)

  case class FoundCredential(credential: MemoryPersistedCredential)
  case class CredentialNotFound(userId: String)


}

class CredentialStoreActor extends EventsourcedProcessor with ActorLogging {
  import CredentialStoreProtocol._

  var store = mutable.Map[String, MemoryPersistedCredential]()

  val receiveReplay: Receive = {
    case Store(userId, credential) => log.debug(s"Replaying a Store message for userId [ ${userId} ]"); store.put(userId, credential)
    case Delete(userId) => log.debug(s"Replaying a Delete message for userId [ ${userId} ]"); store.remove(userId)
    case SnapshotOffer(_, snapshot: mutable.Map[String, MemoryPersistedCredential]) => {
      log.debug("Updating store to match snapshot offer provided during recovery")
      store = snapshot
    }
  }

  val receiveCommand: Receive = {
    case Get(userId) => {
      log.debug(s"attempting to load a credential")
      store.get(userId) match {
        case Some(c) => {
          log.debug("... credential loaded. Returning.")
          sender ! FoundCredential(c)
        }
        case None => log.debug("... failed to load credential."); sender ! CredentialNotFound(userId)
      }
    }
    case e @ Store(userId, credential) => {
      log.debug(s"attempting to store")
      persist(Store(e.userId, credential)) { e =>
        store.put(e.userId, credential)
        log.debug("stored")
      }
    }
    case Delete(userId: String) => store.remove(userId)
  }
}
