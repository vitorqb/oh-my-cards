package v1.auth

import com.mohiva.play.silhouette.api.RequestProvider
import com.mohiva.play.silhouette.api.LoginInfo
import play.api.mvc.Request
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.google.inject.Inject
import services.Clock


class BearerTokenRequestProvider @Inject()(
  userTokenRepository: UserTokenRepository,
  clock: Clock)(
  implicit ec: ExecutionContext)
    extends RequestProvider {

  override def id = BearerTokenRequestProvider.ID

  def authenticate[B](request: Request[B]): Future[Option[LoginInfo]] = {
    BearerTokenRequestProvider.extractTokenValue(request) match {
      case Some(tokenValue) => userTokenRepository.findByTokenValue(tokenValue).map {
        case Some(userToken) if userToken.isValid(clock) => Some(userToken.toLoginInfo(id))
        case _ => None
      }
      case None => Future{ None }
    }
  }
}

object BearerTokenRequestProvider {

  val ID = "bearer-token"

  private val tokenRegex = """^Bearer (.*)$""".r

  /**
    * Extracts the token value from the request.
    */
  def extractTokenValue[B](request: Request[B]): Option[String] = {
    request.headers.get("Authorization").flatMap {
      case tokenRegex(x) => Some(x)
      case _             => None
    }
  }
}
