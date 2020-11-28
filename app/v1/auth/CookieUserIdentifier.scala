package v1.auth

import play.api.mvc.Request
import play.api.mvc.AnyContent
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.mohiva.play.silhouette.api.util.{Clock => SilhouetteClock}

/**
  * A helper object that knows how to recover an User from a request
  * using tokens. *SHOULD ONLY BE USED WHERE TOKEN-BASED AUTH CAN'T BE USED*.
  * An example are the request for staticfiles.
  */
trait CookieUserIdentifierLike {
  def identifyUser(r: Request[AnyContent]): Future[Option[User]]
}

class CookieUserIdentifier(
  cookieTokenManager: CookieTokenManagerLike,
  clock: SilhouetteClock
)(
  implicit val ec: ExecutionContext
) extends CookieUserIdentifierLike {

  override def identifyUser(r: Request[AnyContent]): Future[Option[User]] = {
    cookieTokenManager
      .extractToken(r)
      .map { _.filter { userToken => userToken.isValid(clock) } }
      .map { _.map { userToken => userToken.user } }
  }
}
