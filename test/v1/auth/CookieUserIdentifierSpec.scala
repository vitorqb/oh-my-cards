package v1.auth

import org.scalatestplus.play.PlaySpec
import org.mockito.MockitoSugar
import org.joda.time.DateTime
import play.api.test.FakeRequest
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Future
import play.api.mvc.Cookie
import scala.concurrent.ExecutionContext
import org.mockito.ArgumentMatchersSugar
import com.mohiva.play.silhouette.api.util.{Clock => SilhouetteClock}

class CookieUserIdentifierSpec
    extends PlaySpec
    with MockitoSugar
    with ScalaFutures
    with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  "identifyUser" should {

    "identify and return the user" in {
      testContext() { c =>
        when(c.tokenEncrypter.decrypt(any))
          .thenReturn(Some(c.token.token.getBytes()))
        when(c.userTokenRepository.findByTokenValue(c.token.token))
          .thenReturn(Future.successful(Some(c.token)))
        val cookie = Cookie(c.cookieName, "foo")
        val request = FakeRequest().withCookies(cookie)
        c.identifier.identifyUser(request).futureValue.get mustEqual c.user
      }
    }

    "ignores invalid token" in {
      testContext() { c =>
        val token = c.token.copy(hasBeenInvalidated = true)
        when(c.tokenEncrypter.decrypt(any))
          .thenReturn(Some(token.token.getBytes()))
        when(c.userTokenRepository.findByTokenValue(token.token))
          .thenReturn(Future.successful(Some(token)))
        val cookie = Cookie(c.cookieName, "foo")
        val request = FakeRequest().withCookies(cookie)
        c.identifier.identifyUser(request).futureValue mustEqual None
      }
    }

  }

  case class TestContext(
      user: User,
      token: UserToken,
      tokenEncrypter: TokenEncrypter,
      userTokenRepository: UserTokenRepository,
      identifier: CookieUserIdentifierLike,
      cookieName: String
  )

  def testContext()(block: TestContext => Any): Any = {
    val cookieName = "foo"
    val user = User("id", "a@b.c")
    val token =
      UserToken(user, "token", DateTime.parse("2020-01-01T00:00:00"), false)
    val userTokenRepository = mock[UserTokenRepository]
    val tokenEncrypter = mock[TokenEncrypter]
    val clock = mock[SilhouetteClock]
    when(clock.now).thenReturn(token.expirationDateTime.minusDays(1))
    val cookieTokenManager =
      new CookieTokenManager(userTokenRepository, tokenEncrypter, cookieName)
    val identifier = new CookieUserIdentifier(cookieTokenManager, clock)
    val context = TestContext(
      user,
      token,
      tokenEncrypter,
      userTokenRepository,
      identifier,
      cookieName
    )
    block(context)
  }

}
