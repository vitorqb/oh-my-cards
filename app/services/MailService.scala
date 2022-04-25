package services

import javax.inject.Inject

import scala.concurrent.ExecutionContext
import play.api.libs.ws.{WSAuthScheme, WSClient}
import play.api.Configuration
import play.api.Logger
import play.api.libs.json.Json
import utils.FileWritterLike

trait MailService {

  def sendSimple(to: String, subject: String, body: String): Unit

}

/**
  * Service to send emails using MailGun.
  */
class MailGunMailServiceImpl @Inject() (ws: WSClient, conf: Configuration)(
    implicit ec: ExecutionContext
) extends MailService {

  private val logger = Logger(getClass)

  /**
    * Sends the authentication to an email.
    */
  def sendSimple(to: String, subject: String, body: String): Unit = {
    val url = conf.get[String]("mailgun.url")
    val from = conf.get[String]("mailgun.from")
    val key = conf.get[String]("mailgun.key")
    logger.info(
      "Sending email with url=" + url + ", from=" + from + ", body=" + body
    )
    ws.url(url)
      .withAuth("api", key, WSAuthScheme.BASIC)
      .post(
        Map("from" -> from, "to" -> to, "subject" -> subject, "text" -> body)
      )
    ()
  }
}

/**
  * Service to send emails using SendGrid.
  */
class SendgridMailServiceImpl @Inject() (ws: WSClient, conf: Configuration)(
    implicit ec: ExecutionContext
) extends MailService {

  private val logger = Logger(getClass)

  /**
    * Sends the authentication to an email.
    */
  def sendSimple(to: String, subject: String, body: String): Unit = {
    val url = conf.get[String]("sendgrid.url")
    val from = conf.get[String]("sendgrid.from")
    val key = conf.get[String]("sendgrid.key")
    val data = Json.obj(
      "personalizations" -> Seq(Map("to" -> Seq(Map("email" -> to)))),
      "from" -> Map("email" -> from),
      "subject" -> subject,
      "content" -> Seq(
        Map(
          "type" -> "text/plain",
          "value" -> body
        )
      )
    )
    logger.info(s"Sending email: $data")
    ws.url(url).withHttpHeaders(("Authorization", s"Bearer $key")).post(data)
    ()
  }

}

/**
  * Fake service for testing purposes.
  */
class MailServiceFakeImpl @Inject() (
    conf: Configuration,
    writter: FileWritterLike
) extends MailService {

  private val logger = Logger(getClass)

  /**
    * Sends the authentication to an email.
    */
  def sendSimple(to: String, subject: String, body: String): Unit = {
    val url = conf.get[String]("mailgun.url")
    val from = conf.get[String]("mailgun.from")
    val key = conf.get[String]("mailgun.key")
    logger.info(
      "PRETENDING to send email with url=" + url + ", from=" + from + ", body=" + body
    )
    writter.write(".email", body)
    ()
  }

}
