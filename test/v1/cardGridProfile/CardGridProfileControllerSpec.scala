package v1.cardGridProfile

import org.scalatestplus.play._
import com.mohiva.play.silhouette.test._
import play.api.test.{ FakeRequest }
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.libs.json.JsValue
import play.api.test.Helpers._
import play.api.inject.guice.GuiceApplicationBuilder
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import org.mockito.Mockito._
import org.mockito.MockitoSugar
import play.api.i18n.MessagesProvider
import play.api.data.Form
import org.scalatest.PrivateMethodTester.PrivateMethod
import scala.concurrent.Future
import play.api.mvc.Result
import test.utils._
import play.api.db.Database
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class CardGridProfileControllerSpec
    extends PlaySpec
    with LoggedInUserAuthContext
    with GuiceOneAppPerSuite
    with MockitoSugar
    with ScalaFutures {

  override def fakeApplication: Application =
    new GuiceApplicationBuilder().overrides(new LoggedInUserFakeModule).build()

  "Creates a cardGridProfileInput" should {

    "successfully create a request and" should {
      val body = Json.obj(
        "name" -> "Foo",
        "config" -> Json.obj("page" -> 1, "pageSize" -> 2, "includeTags" -> List("A"))
      )
      val db = app.injector.instanceOf[Database]
      TestUtils.cleanupDb(db)
      val request = FakeRequest().withJsonBody(body)
      val controller = app.injector.instanceOf[CardGridProfileController]
      val response = controller.create(request)
      Await.ready(response, 5000 millis)
      val getRequest = FakeRequest()
      val getResponse = controller.read("Foo")(getRequest)
      Await.ready(getResponse, 5000 millis)

      "create request returns 200" in { status(response) mustBe 200 }
      "create request returns the json in the body" in { contentAsJson(response) mustEqual body }
      "get created resource returns 200" in { status(getResponse) mustEqual 200 }
      "get created retsource returns body" in { contentAsJson(getResponse) mustEqual body }
    }

    "return the form errors as json" in new WithImplicitMessageProvider {
      val controller = app.injector.instanceOf[CardGridProfileController]
      val request = FakeRequest().withJsonBody(Json.obj("foo" -> "bar"))
      val result = controller.create(request)
      status(result) mustEqual 400
      contentAsJson(result) mustEqual Json.obj("name" -> List("This field is required"))
    }
  }

}

class CardGridProfileInputSpec extends PlaySpec {

  def runWithRequest[A](r: Request[A]): CardGridProfileInput =
    CardGridProfileInput.form.bindFromRequest()(r).get

  def runFromJson[A](x: JsValue): CardGridProfileInput =
    runWithRequest(FakeRequest().withJsonBody(x))

  "Parsing using form" should {

    "All empty" in {
      val body = Json.obj("name" -> "Foo", "config" -> Json.obj())
      runFromJson(body) mustEqual CardGridProfileInput(
        "Foo",
        CardGridConfigInput(None, None, None, None)
      )
    }

    "With page and page size" in {
      val body = Json.obj("name" -> "Foo", "config" -> Json.obj("page" -> 1, "pageSize" -> 2))
      runFromJson(body) mustEqual CardGridProfileInput(
        "Foo",
        CardGridConfigInput(Some(1), Some(2), None, None)
      )
    }

    "With includeTags and excludeTags" in {
      val body = Json.obj(
        "name" -> "Foo",
        "config" -> Json.obj("includeTags" -> List("A"), "excludeTags" -> List("B", "C"))
      )
      runFromJson(body) mustEqual CardGridProfileInput(
        "Foo",
        CardGridConfigInput(None, None, Some(List("A")), Some(List("B", "C")))
      )
    }

  }
}
