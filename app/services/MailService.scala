package services

import javax.inject.Inject

import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future,ExecutionContext}
import play.api.libs.ws.{WSAuthScheme,WSClient}
import play.api.Configuration
import play.api.Logger

/**
  * Service to send emails.
  */
class MailService @Inject()(implicit ec: ExecutionContext, ws: WSClient, conf: Configuration) {

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
