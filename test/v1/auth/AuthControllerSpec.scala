package v1.auth

import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import testutils.silhouettetestutils.SilhouetteInjectorContext
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers
import play.api.test.Helpers._
import play.api.mvc.Cookie
import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchersSugar
import services.MailService
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}
import play.api.libs.json.Json


class AuthControllerSpec
    extends PlaySpec
    with MockitoSugar
    with ArgumentMatchersSugar
{

  "recoverTokenFromCookie" should {
    "recover the token from the cookie" in new Injector() { c =>
      when(c.token.isValid(c.clock)).thenReturn(true)
      when(c.token.token).thenReturn("token")
      when(c.cookieTokenManager.extractToken(any)).thenReturn(Future.successful(Some(token)))
      val cookie = Cookie("OHMYCARDS_AUTH", "foo")
      val request = FakeRequest().withCookies(cookie)

      val response = c.controller.recoverTokenFromCookie()(request)

      status(response) mustEqual 200
      contentAsJson(response) mustEqual Json.obj("value" -> "token")
    }
    "fail if invalid cookie" in new Injector() { c =>
      when(c.token.isValid(c.clock)).thenReturn(false)
      when(c.cookieTokenManager.extractToken(any)).thenReturn(Future.successful(Some(token)))
      val cookie = Cookie("OHMYCARDS_AUTH", "foo")
      val request = FakeRequest().withCookies(cookie)

      val response = c.controller.recoverTokenFromCookie()(request)

      status(response) mustEqual 400
    }
  }

  class Injector() {

    val silhouetteInjector = new SilhouetteInjectorContext {}

    lazy val app = new GuiceApplicationBuilder()
      .overrides(new silhouetteInjector.GuiceModule)
      .build()

    lazy implicit val ec = ExecutionContext.global
    lazy val silhouette = silhouetteInjector.silhouette(app)
    lazy val controllerComponents = Helpers.stubControllerComponents()
    lazy val oneTimePasswordRepository = mock[OneTimePasswordInfoRepository]
    lazy val oneTimePasswordProvider = mock[OneTimePasswordProvider]
    lazy val oneTimePasswordInfoGenerator = mock[OneTimePasswordInfoGenerator]
    lazy val tokenEncrypter = mock[TokenEncrypter]
    lazy val mailService = mock[MailService]
    lazy val userService = mock[UserService]
    lazy val tokenService = mock[TokenService]
    lazy val cookieTokenManager = mock[CookieTokenManagerLike]
    lazy val clock = mock[SilhouetteClock]
    lazy val token = mock[UserToken]
    lazy val controller = new AuthController(
      silhouette,
      controllerComponents,
      oneTimePasswordRepository,
      oneTimePasswordProvider,
      oneTimePasswordInfoGenerator,
      tokenEncrypter,
      mailService,
      userService,
      tokenService,
      cookieTokenManager,
      clock
    )

    def apply(block: Injector => Any): Any = {
      Helpers.running(app) {
        block(this)
      }
    }
  }
}

