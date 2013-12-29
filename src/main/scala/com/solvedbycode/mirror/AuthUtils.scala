package com.solvedbycode.mirror

import java.io.File
import scala.util.Try
import scala.io.Source
import java.util.Properties
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import scala.collection.JavaConverters._
import com.google.api.client.util.store.MemoryDataStoreFactory
import akka.actor.ActorSystem

object AuthUtils {
  val GLASS_SCOPE = Seq("https://www.googleapis.com/auth/glass.timeline ",
                    "https://www.googleapis.com/auth/glass.location ",
                    "https://www.googleapis.com/auth/userinfo.profile")

  def newAuthorizationCodeFlow()(implicit system: ActorSystem) = {
    val propertiesFile = Try(new File(getClass.getResource("/oauth.properties").toURI))
                            .getOrElse(new File("./src/main/resources/oauth.properties"))

    val authProperties = new Properties()
    authProperties.load(Source.fromFile(propertiesFile).bufferedReader())
    val clientId = authProperties.getProperty("client_id")
    val clientSecret = authProperties.getProperty("client_secret")

    new GoogleAuthorizationCodeFlow.Builder(new NetHttpTransport(), new JacksonFactory(), clientId, clientSecret, GLASS_SCOPE.asJavaCollection)
      .setAccessType("offline")
      .setCredentialStore(CredentialStoreProxy())
      .build()
//      .setDataStoreFactory(CredentialDataStoreFactory())
//      .build()
  }
}
