package v1.auth

import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.PlaySpec
import org.mockito.MockitoSugar
import play.api.test.FakeRequest
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Future
import play.api.mvc.Cookie
import org.mockito.ArgumentMatchersSugar


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
      val extractor = new CookieTokenManager(userTokenRepository, tokenEncrypter, "foo")
      val request = FakeRequest().withCookies(Cookie("bar", "bar"))

      extractor.extractToken(request).futureValue mustEqual None
    }

    "fail to extract if token decryption fail" in {
      val token = mock[UserToken]
      val tokenEncrypter = mock[TokenEncrypter]
      when(tokenEncrypter.decrypt(any)).thenReturn(None)
      val userTokenRepository = mock[UserTokenRepository]
      val extractor = new CookieTokenManager(userTokenRepository, tokenEncrypter, "foo")
      val request = FakeRequest().withCookies(Cookie("foo", "foo"))

      extractor.extractToken(request).futureValue mustEqual None
    }

    "extract a token when token" in {
      val token = mock[UserToken]
      val tokenEncrypter = mock[TokenEncrypter]
      when(tokenEncrypter.decrypt(any)).thenReturn(Some("token".getBytes()))
      val userTokenRepository = mock[UserTokenRepository]
      when(userTokenRepository.findByTokenValue("token"))
        .thenReturn(Future.successful(Some(token)))
      val extractor = new CookieTokenManager(userTokenRepository, tokenEncrypter, "foo")
      val request = FakeRequest().withCookies(Cookie("foo", "foo"))

      extractor.extractToken(request).futureValue mustEqual Some(token)
    }

  }

}
