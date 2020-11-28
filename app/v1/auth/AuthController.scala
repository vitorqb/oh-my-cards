package v1.auth

import services.MailService
import com.mohiva.play.silhouette.api._
import play.api.mvc._
import play.api.mvc.Request
import scala.concurrent.Future
import com.google.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.Success
import scala.util.Failure
import play.api.libs.json.Json
import java.{util => ju}

/**
  * Wrapper for sending emails with tokens.
  */
private object MailHelper {

  private def subject: String = "Your OhMyCards one time password!"

  private def body(oneTimePassword: String): String = {
    "Your OhMyCards one time password is: " + oneTimePassword
  }

  def sendOneTimePasswordMail(mailService: MailService, oneTimePassword: OneTimePasswordInfo) = {
    mailService.sendSimple(oneTimePassword.email, subject, body(oneTimePassword.oneTimePassword))
    ()
  }

}

/**
  * Represents the user input for a token request.
  */
case class OneTimePasswordInput(email: String)
case class TokenInput(email: String, oneTimePassword: String)

/**
  * Object with form for parsing user inputs.
  */
object Forms {
  import play.api.data.Forms._
  import play.api.data.Form

  val OneTimePasswordInputForm = Form[OneTimePasswordInput](
    mapping(
      "email" -> nonEmptyText
    )(OneTimePasswordInput.apply)(OneTimePasswordInput.unapply)
  )

  val TokenInputForm = Form[TokenInput](
    mapping(
      "email" -> nonEmptyText,
      "oneTimePassword" -> nonEmptyText
    )(TokenInput.apply)(TokenInput.unapply)
  )
}

/**
  * A controller to handle the entire authentication flow.
  */
class AuthController @Inject()(
  val silhouette: Silhouette[DefaultEnv],
  val controllerComponents: ControllerComponents,
  val oneTimePasswordRepository: OneTimePasswordInfoRepository,
  val oneTimePasswordProvider: OneTimePasswordProvider,
  val oneTimePasswordInfoGenerator: OneTimePasswordInfoGenerator,
  val tokenEncrypter: TokenEncrypter,
  val mailService: MailService,
  val userService: UserService,
  val tokenService: TokenService)(
  implicit val ec: ExecutionContext)
    extends BaseController {

  //!!!! TODO READ FROM CONFIG
  private val AUTH_COOKIE = "OHMYCARDS_AUTH"

  def createOneTimePassword = silhouette.UnsecuredAction.async { implicit request =>
    Forms.OneTimePasswordInputForm.bindFromRequest.fold(
      _ => Future.successful(BadRequest("Invalid post data!")),
      oneTimePasswordInput => {
        val loginInfo = LoginInfo(oneTimePasswordProvider.id, oneTimePasswordInput.email)
        val authInfo = oneTimePasswordInfoGenerator.generate(oneTimePasswordInput)
        oneTimePasswordRepository.add[OneTimePasswordInfo](loginInfo, authInfo).map {
          oneTimePasswordInfo: OneTimePasswordInfo =>

          MailHelper.sendOneTimePasswordMail(mailService, oneTimePasswordInfo)
          Ok("")
        }
      }
    )
  }

  def createToken = silhouette.UnsecuredAction.async { implicit request =>
    Forms.TokenInputForm.bindFromRequest.fold(
      _ => Future.successful(BadRequest("Invalid post data!")),
      tokenInput => {
        val credentials = OneTimePasswordCredentials.fromTokenInput(tokenInput)
        oneTimePasswordProvider.authenticate(credentials).flatMap {
          case Failure(e) => Future(Failure(e))
          case Success(loginInfo) => userService.retrieve(loginInfo).flatMap {
            case Some(x) => Future(Success(x))
            case None => userService.add(loginInfo).map(Success(_))
          }
        }.flatMap {
          case Failure(e: AuthenticationException) => Future(BadRequest("Invalid credentials."))
          case Failure(e) => throw e
          case Success(user) => for {
            token <- tokenService.generateTokenForUser(user)
            encryptedToken = ju.Base64.getEncoder.encodeToString(tokenEncrypter.encrypt(token))
          } yield {
            Ok(Json.toJson(token)).withCookies(Cookie(AUTH_COOKIE, encryptedToken))
          }
        }
      }
    )
  }

  def recoverTokenFromCookie = silhouette.UserAwareAction.async { implicit request => Future {
    decryptAuthCookie(request) match {
      case None => BadRequest
      case Some(x) => Ok(Json.toJson(Json.obj("value" -> x)))
    }
  }}

  def getUser = silhouette.SecuredAction.async { implicit request => Future {
    Ok(Json.toJson(Json.obj("email" -> request.identity.email)))
  }}

  //!!!! TODO REMOVE AND USER CookieUserIdentifierLike
  private def decryptAuthCookie[A](r: Request[A]): Option[String] = {
    r.cookies.get(AUTH_COOKIE).map(_.value).map(decodeBase64(_)).flatMap(encryptedToken => {
      tokenEncrypter.decrypt(encryptedToken).map(arrayOfBytesToString(_))
    })
  }

  private def arrayOfBytesToString(a: Array[Byte]): String = a.map(_.toChar).mkString
  private def decodeBase64(x: String): Array[Byte] = ju.Base64.getDecoder.decode(x.getBytes)

}
