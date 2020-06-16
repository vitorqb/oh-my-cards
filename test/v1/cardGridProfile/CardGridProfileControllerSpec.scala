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
import v1.auth.User
import services.UUIDGenerator
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.BeforeAndAfter

sealed trait SharedTestUtils {
  val emptyConfig: CardGridConfigInput = CardGridConfigInput(None, None, None, None, None)
}

class CardGridProfileControllerSpec
    extends PlaySpec
    with LoggedInUserAuthContext
    with GuiceOneAppPerSuite
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfter
    with SharedTestUtils {

  after {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
  }

  override def fakeApplication: Application =
    new GuiceApplicationBuilder().overrides(new LoggedInUserFakeModule).build()

  "Creates a cardGridProfileInput" should {

    def controller = app.injector.instanceOf[CardGridProfileController]


    "successfully create a request and" should {
      val body = Json.obj(
        "name" -> "Foo",
        "config" -> Json.obj("page" -> 1, "pageSize" -> 2, "includeTags" -> List("A"))
      )
      val request = FakeRequest().withJsonBody(body)
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

    "create, get and update a request with a query" in {
      val query = "((tags CONTAINS 'Bar!'"
      val body = Json.obj("name" -> "Bar!", "config" -> Json.obj("query" -> query))
      val postResponse = controller.create(FakeRequest().withJsonBody(body))
      status(postResponse) mustEqual 200
      val getResponse = controller.read("Bar!")(FakeRequest())
      status(getResponse) mustEqual 200
      (contentAsJson(getResponse) \ "name").as[String] mustEqual "Bar!"
      (contentAsJson(getResponse) \ "config" \ "query").as[String] mustEqual query
    }
  }

  "Edit a cardGridProfileInput" should {

    def originalInput = CardGridProfileInput("Foo", emptyConfig)
    def repository = app.injector.instanceOf[CardGridProfileRepository]
    val modifiedJson = Json.obj(
      "name" -> "Foo",
      "config" -> Json.obj("page" -> 1, "pageSize" -> 2, "includeTags" -> List("A"))
    )
    val request = FakeRequest().withJsonBody(modifiedJson)
    def controller = app.injector.instanceOf[CardGridProfileController]

    "update a profile" in {
      repository.create(originalInput, user).futureValue

      val result = controller.update("Foo")(request)

      status(result) mustEqual 200
      contentAsJson(result) mustEqual modifiedJson

      val modifiedInput = repository.readFromName("Foo", user).futureValue.value
      modifiedInput.name mustEqual "Foo"
      modifiedInput.config.page mustEqual Some(1)
      modifiedInput.config.pageSize mustEqual Some(2)
      modifiedInput.config.includeTags mustEqual Some(List("A"))
      modifiedInput.config.excludeTags mustEqual None
    }

    "return 404 if profile belongs to other user" in {
      val otherUser = user.copy(user.id + "A", user.email + "A")
      repository.create(originalInput, otherUser)
      val result = controller.update("FOO")(request)

      status(result) mustEqual 404
    }

  }

  "listNames" should {

    def createProfile(
      input: CardGridProfileInput,
      user: User
    )(
      implicit repository: CardGridProfileRepository
    ): Unit = {
      val future = repository.create(input, user)
      Await.ready(future, 5000 millis)
    }

    val otherUser = User("otherUser", "other@user")
    val uuidGenerator = new UUIDGenerator

    "return a list with names for all profiles for the user " in {
      TestUtils.testDB { implicit db =>
        implicit val repository = new CardGridProfileRepository(db, uuidGenerator)
        val controller = app.injector.instanceOf[CardGridProfileController]

        createProfile(CardGridProfileInput("1", emptyConfig), user)
        createProfile(CardGridProfileInput("2", emptyConfig), user)
        createProfile(CardGridProfileInput("3", emptyConfig), otherUser)

        val request = FakeRequest()
        val result = controller.listNames(request)

        status(result) mustEqual 200
        contentAsJson(result) mustEqual Json.obj("names" -> List("1", "2"))
      }
    }

  }

}

class CardGridProfileInputSpec extends PlaySpec with SharedTestUtils {

  def runWithRequest[A](r: Request[A]): CardGridProfileInput =
    CardGridProfileInput.form.bindFromRequest()(r).get

  def runFromJson[A](x: JsValue): CardGridProfileInput =
    runWithRequest(FakeRequest().withJsonBody(x))

  "Parsing using form" should {

    "All empty" in {
      val body = Json.obj("name" -> "Foo", "config" -> Json.obj())
      runFromJson(body) mustEqual CardGridProfileInput(
        "Foo",
        emptyConfig
      )
    }

    "With page and page size" in {
      val body = Json.obj("name" -> "Foo", "config" -> Json.obj("page" -> 1, "pageSize" -> 2))
      runFromJson(body) mustEqual CardGridProfileInput(
        "Foo",
        emptyConfig.copy(page=Some(1), pageSize=Some(2))
      )
    }

    "With includeTags and excludeTags" in {
      val body = Json.obj(
        "name" -> "Foo",
        "config" -> Json.obj("includeTags" -> List("A"), "excludeTags" -> List("B", "C"))
      )
      runFromJson(body) mustEqual CardGridProfileInput(
        "Foo",
        emptyConfig.copy(includeTags=Some(List("A")), excludeTags=Some(List("B", "C")))
      )
    }

    "With query" in {
      val body = Json.obj("name" -> "foo", "config" -> Json.obj("query" -> "()"))
      runFromJson(body) mustEqual CardGridProfileInput("foo", emptyConfig.copy(query=Some("()")))
    }

  }
}
