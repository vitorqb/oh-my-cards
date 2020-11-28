package v1.auth

import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import testutils.silhouettetestutils.SilhouetteInjectorContext
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers
import play.api.test.Helpers._
import play.api.mvc.Cookie
import java.{util => ju}
import org.mockito.MockitoSugar
import org.mockito.ArgumentMatchersSugar
import services.MailService
import scala.concurrent.ExecutionContext
import play.api.libs.json.Json


class AuthControllerSpec
    extends PlaySpec
    with MockitoSugar
    with ArgumentMatchersSugar
{

  "recoverTokenFromCookie" should {
    "recover the token from the cookie" in new Injector() { c =>
      when(c.tokenEncrypter.decrypt("encrypted".getBytes())).thenReturn(Some("token".getBytes()))

      //!!!! TODO base64 encode/decode helper
      val tokenVal = ju.Base64.getEncoder().encode("encrypted".getBytes()).map(_.toChar).mkString
      val cookie = Cookie("OHMYCARDS_AUTH", tokenVal)
      val request = FakeRequest().withCookies(cookie)
      val response = c.controller.recoverTokenFromCookie()(request)
      status(response) mustEqual 200
      contentAsJson(response) mustEqual Json.obj("value" -> "token")
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
    lazy val controller = new AuthController(
      silhouette,
      controllerComponents,
      oneTimePasswordRepository,
      oneTimePasswordProvider,
      oneTimePasswordInfoGenerator,
      tokenEncrypter,
      mailService,
      userService,
      tokenService
    )

    def apply(block: Injector => Any): Any = {
      Helpers.running(app) {
        block(this)
      }
    }
  }
}

