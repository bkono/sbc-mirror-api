package com.solvedbycode.mirror

import scala.collection.JavaConverters._
import com.google.api.services.mirror.model._
import com.google.api.client.auth.oauth2.Credential
import com.google.api.services.mirror.Mirror
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import akka.event.slf4j.Logger
import scala.util.{Success, Try}
import com.google.api.client.googleapis.json.GoogleJsonResponseException

object MirrorClient {
  def apply(credential: Credential) = new MirrorClient(credential)
}

class MirrorClient(credential: Credential) {
  val log = Logger(getClass.getCanonicalName)

  def getMirror(credential: Credential): Mirror = new Mirror.Builder(new NetHttpTransport, new JacksonFactory, credential)
                                                    .setApplicationName("See-Thru Bamboo").build()

  // Contacts
  def insertContact(contact: Contact): Contact = getMirror(credential).contacts().insert(contact).execute()
  def deleteContact(contactId: String) = getMirror(credential).contacts().delete(contactId).execute()
  def insertContact(contactId: String, displayName: String, acceptCommands: Seq[String], imageUrls: Seq[String]): Contact = {
    val contact = new Contact()
    contact.setId(contactId)
    contact.setDisplayName(displayName)
    contact.setAcceptCommands(acceptCommands.map(new Command().setType).asJava)
    contact.setImageUrls(imageUrls.asJava)
    val insertedContact = insertContact(contact)
    log.debug(s"Managed to insert a contact for the accessToken[ ${credential.getAccessToken} ]. Contact has id [ ${insertedContact.getId} ]")
    insertedContact
  }

  // Subscriptions
  def insertSubscription(callbackUrl: String, userId: String, collection: String, operations: Option[Seq[String]]) = {
    log.debug(s"Attempting to subscribe to userId [ $userId ] with callback url [ $callbackUrl ]")
    val subscription = new Subscription()
    subscription.setCollection(collection)
    subscription.setCallbackUrl(callbackUrl)
    subscription.setUserToken(userId)
    if (operations.isDefined) subscription.setOperation(operations.get.asJava)

    Try(Some(getMirror(credential).subscriptions().insert(subscription).execute())) recoverWith {
      case e: GoogleJsonResponseException => {
        log.warn(s"Failed while attempting to subscribe to timeline notifications for userId [ $userId ]. Probably on localhost. ")
        log.warn(e.getDetails.toPrettyString)
        Success(None)
      }
    }
  }

  def insertTimelineSubscription(callbackUrl: String, userId: String, operations: Option[Seq[String]] = None) = insertSubscription(callbackUrl, userId, "timeline", operations)
  def insertLocationsSubscription(callbackUrl: String, userId: String, operations: Option[Seq[String]] = None) = insertSubscription(callbackUrl, userId, "locations", operations)


  // Timeline
  def insertTimelineItem(item: TimelineItem) = getMirror(credential).timeline().insert(item).execute()
  def insertTextTimelineItem(text: String, notificationLevel: String = "DEFAULT"): TimelineItem = {
    val timelineItem: TimelineItem = new TimelineItem
    timelineItem.setText(text)
    timelineItem.setNotification(new NotificationConfig().setLevel(notificationLevel))
    val insertedItem: TimelineItem = insertTimelineItem(timelineItem)
    log.debug(s"Added a timeline item for access token [ ${credential.getAccessToken} ]. TimelineItem has id [ ${insertedItem.getId} ]")
    insertedItem
  }
}