package services

import javax.inject.Inject

import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future,ExecutionContext}
import play.api.libs.ws.{WSAuthScheme,WSClient}
import play.api.Configuration
import play.api.Logger
import com.google.inject.Provides

trait MailService {

  def sendSimple(to: String, subject: String, body: String): Unit

}

/**
  * Service to send emails.
  */
class MailServiceImpl @Inject()(
  ws: WSClient,
  conf: Configuration)(
  implicit ec: ExecutionContext) extends MailService {

  private val logger = Logger(getClass)

  /**
    * Sends the authentication to an email.
    */
  def sendSimple(to: String, subject: String, body: String): Unit = {
    val url = conf.get[String]("sendgrid.url")
    val from = conf.get[String]("sendgrid.from")
    val key = conf.get[String]("sendgrid.key")
    logger.info("Sending email with url=" + url + ", from=" + from + ", body=" + body)
    ws.url(url)
      .withAuth("api", key, WSAuthScheme.BASIC)
      .post(Map("from" -> from, "to" -> to, "subject" -> subject, "text" -> body))
    ()
  }
}

/**
  * Fake service for testing purposes.
  */
class MailServiceFakeImpl @Inject()(conf: Configuration) {

  private val logger = Logger(getClass)

  /**
    * Sends the authentication to an email.
    */
  def sendSimple(to: String, subject: String, body: String): Unit = {
    val url = conf.get[String]("sendgrid.url")
    val from = conf.get[String]("sendgrid.from")
    val key = conf.get[String]("sendgrid.key")
    logger.info("PRETENDING to send email with url=" + url + ", from=" + from + ", body=" + body)
    ()
  }

}
