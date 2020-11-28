package v1.auth

import services.MailService
import com.mohiva.play.silhouette.api._
import play.api.mvc._
import scala.concurrent.Future
import com.google.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.Success
import scala.util.Failure
import play.api.libs.json.Json
import utils.Base64Converter
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}

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
  val tokenService: TokenService,
  val cookieTokenManager: CookieTokenManagerLike,
  val clock: SilhouetteClock
)(
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
            encryptedToken = tokenEncrypter.encrypt(token)
            base64EncryptedToken = Base64Converter.encodeToString(encryptedToken)
          } yield {
            Ok(Json.toJson(token)).withCookies(Cookie(AUTH_COOKIE, base64EncryptedToken))
          }
        }
      }
    )
  }

  def recoverTokenFromCookie = silhouette.UserAwareAction.async { implicit request =>
    cookieTokenManager.extractToken(request).map {
      case None  => BadRequest
      case Some(token) if ! token.isValid(clock) => BadRequest
      case Some(token) => Ok(Json.toJson(token))
    }
  }

  def getUser = silhouette.SecuredAction.async { implicit request => Future {
    Ok(Json.toJson(Json.obj("email" -> request.identity.email)))
  }}

}
