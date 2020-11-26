package v1.auth

import play.api.mvc.Request
import play.api.mvc.AnyContent
import scala.concurrent.Future
import java.{util => ju}


/**
  * Extracts a Token from a request cookies.
  */
trait CookieTokenExtractorLike {
  def extractToken(r: Request[AnyContent]): Future[Option[UserToken]]
}

class CookieTokenExtractor(
  userTokenRepository: UserTokenRepository,
  tokenEncrypter: TokenEncrypter,
  authCookieName: String,
) extends CookieTokenExtractorLike {

  override def extractToken(r: Request[AnyContent]): Future[Option[UserToken]] = {
    r.cookies.get(authCookieName)
      .map { cookie => cookie.value }
      .map { cookieValue => cookieValue.getBytes() }
      .map { cookieValueBytes => ju.Base64.getDecoder().decode(cookieValueBytes) }
      .flatMap { encryptedTokenBytes => tokenEncrypter.decrypt(encryptedTokenBytes) }
      .map { tokenBytes => tokenBytes.map(_.toChar).mkString }
      .map { token => userTokenRepository.findByTokenValue(token) } match {
        case None => Future.successful(None)
        case Some(x) => x
      }
  }

}
