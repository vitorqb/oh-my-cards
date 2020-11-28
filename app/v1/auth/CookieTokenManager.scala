package v1.auth

import play.api.mvc.Request
import play.api.mvc.AnyContent
import scala.concurrent.Future
import utils.Base64Converter


/**
  * Extracts a Token from a request cookies.
  */
trait CookieTokenManagerLike {
  def extractToken(r: Request[AnyContent]): Future[Option[UserToken]]
}

class CookieTokenManager(
  userTokenRepository: UserTokenRepository,
  tokenEncrypter: TokenEncrypter,
  authCookieName: String,
) extends CookieTokenManagerLike {

  override def extractToken(r: Request[AnyContent]): Future[Option[UserToken]] = {
    r.cookies.get(authCookieName)
      .map { cookie => Base64Converter.decode(cookie.value) }
      .flatMap { encryptedTokenBytes => tokenEncrypter.decrypt(encryptedTokenBytes) }
      .map { tokenBytes => tokenBytes.map(_.toChar).mkString }
      .map { token => userTokenRepository.findByTokenValue(token) } match {
        case None => Future.successful(None)
        case Some(x) => x
      }
  }

}
