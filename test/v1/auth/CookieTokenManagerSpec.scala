package v1.auth

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.PlaySpec
import org.mockito.MockitoSugar
import play.api.test.FakeRequest
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Future
import play.api.mvc.Cookie
import org.mockito.ArgumentMatchersSugar
import utils.Base64Converter
import play.api.mvc.Result


class CookieTokenManagerSpec
    extends PlaySpec
    with MockitoSugar
    with ScalaFutures
    with ArgumentMatchersSugar
{

  "extractToken" should {

    "fail to extract a token when no token" in {
      val token = mock[UserToken]
      val tokenEncrypter = mock[TokenEncrypter]
      val userTokenRepository = mock[UserTokenRepository]
      val manager = new CookieTokenManager(userTokenRepository, tokenEncrypter, "foo")
      val request = FakeRequest().withCookies(Cookie("bar", "bar"))

      manager.extractToken(request).futureValue mustEqual None
    }

    "fail to extract if token decryption fail" in {
      val token = mock[UserToken]
      val tokenEncrypter = mock[TokenEncrypter]
      when(tokenEncrypter.decrypt(any)).thenReturn(None)
      val userTokenRepository = mock[UserTokenRepository]
      val manager = new CookieTokenManager(userTokenRepository, tokenEncrypter, "foo")
      val request = FakeRequest().withCookies(Cookie("foo", "foo"))

      manager.extractToken(request).futureValue mustEqual None
    }

    "extract a token when token" in {
      val token = mock[UserToken]
      val tokenEncrypter = mock[TokenEncrypter]
      when(tokenEncrypter.decrypt(any)).thenReturn(Some("token".getBytes()))
      val userTokenRepository = mock[UserTokenRepository]
      when(userTokenRepository.findByTokenValue("token"))
        .thenReturn(Future.successful(Some(token)))
      val manager = new CookieTokenManager(userTokenRepository, tokenEncrypter, "foo")
      val request = FakeRequest().withCookies(Cookie("foo", "foo"))

      manager.extractToken(request).futureValue mustEqual Some(token)
    }

  }

  "setToken" should {
    "set the token in the request, encrypted and converted to base64" in {
      val token = mock[UserToken]
      when(token.token).thenReturn("token")
      val tokenEncrypter = mock[TokenEncrypter]
      when(tokenEncrypter.encrypt(token)).thenReturn("encrypted".getBytes())
      val result = mock[Result]
      val userTokenRepository = mock[UserTokenRepository]
      val manager = new CookieTokenManager(userTokenRepository, tokenEncrypter, "foo")

      manager.setToken(result, token)

      verify(result).withCookies(Cookie("foo", Base64Converter.encodeToString("encrypted")))
    }
  }

}
 
