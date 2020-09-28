package v1.auth

import com.mohiva.play.silhouette.api.Provider
import com.mohiva.play.silhouette.api.LoginInfo
import play.api.Logger
import scala.concurrent.Future
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import scala.concurrent.ExecutionContextExecutor
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}

/**
  * Custom exception for failures here.
  */
final case class AuthenticationException(
  private val Message: String = "Invalid credentials.",
  private val Cause: Throwable = None.orNull
) extends Exception

/**
  * Provides authentication for a one time password credentials.
  */
class OneTimePasswordProvider @Inject()(
  val oneTimePassInfoRepository: OneTimePasswordInfoRepository,
  val clock: SilhouetteClock)(
  implicit val ec: ExecutionContextExecutor)
    extends Provider {

  private val logger = Logger(getClass)

  override def id = OneTimePasswordProvider.ID

  /**
    * Make sure a LoginInfo is valid.
    */
  def authenticate(loginInfo: LoginInfo, oneTimePassword: String): Future[Try[LoginInfo]] = {
    logger.info("Authenticating LoginInfo " + loginInfo + " and password " + oneTimePassword)
    oneTimePassInfoRepository.find[OneTimePasswordInfo](loginInfo).map {
      case None => {
        Failure(new AuthenticationException)
      }
      case Some(oneTimePasswordInfo: OneTimePasswordInfo)
          if ! oneTimePasswordInfo.isValid(clock)  => {
            Failure(new AuthenticationException)
      }
      case Some(oneTimePasswordInfo: OneTimePasswordInfo)
          if ! oneTimePasswordInfo.matches(oneTimePassword) => {
            Failure(new AuthenticationException)
          }
      case Some(oneTimePasswordInfo: OneTimePasswordInfo) => {
        logger.info("Found valid oneTimePasswordInfo " + oneTimePasswordInfo)
        oneTimePassInfoRepository.update(loginInfo, oneTimePasswordInfo.copy(hasBeenUsed=true))
        Success(loginInfo)
      }
    }
  }

  /**
    * Tries to retrieve a valid LoginInfo given credentials for one time password.
    *  Raises error if can't do it.
    */
  def authenticate(credentials: OneTimePasswordCredentials): Future[Try[LoginInfo]] = {
    logger.info("Getting LoginInfo for credentials: " + credentials)
    val loginInfo = LoginInfo(id, credentials.email)
    authenticate(loginInfo, credentials.oneTimePassword)
  }

}

object OneTimePasswordProvider {

  val ID = "one-time-password"

}
