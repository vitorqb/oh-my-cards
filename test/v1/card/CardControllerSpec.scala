package v1.card

import org.scalatestplus.play._
import play.api.test.Helpers._
import play.api.test.FakeRequest
import play.api.libs.json.JsValue
import test.utils._
import v1.auth.User
import play.api.libs.json.Json
import play.api.db.Database
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import scala.concurrent.Future
import play.api.mvc.Result
import v1.card.elasticclient.CardElasticClientMock
import v1.card.historytracker.CardCreation
import v1.card.historytracker.CardUpdate
import org.joda.time.DateTime
import v1.card.historytracker.StringFieldUpdate
import v1.card.historytracker.TagsFieldUpdate
import play.api.mvc.ControllerComponents
import org.mockito.MockitoSugar
import v1.card.historytrackerhandler.HistoryTrackerHandlerLike
import scala.concurrent.ExecutionContext
import v1.auth.SilhouetteEnvWrapper
import v1.card.historytrackerhandler.CardHistoryResource
import v1.card.repository.CardData


trait CardListRequestParserTestUtils extends JsonUtils {

  implicit class EnrichedResult[T](private val x: Either[CardListRequestInput, JsValue]) {
    def isBadWithJsKey(k: String) = x match {
      case Left(_) => false
      case Right(x: JsValue) => x hasKey k
      case Right(x) => false
    }
  }

}

class CardFormInputSpec extends PlaySpec {
  "Parsing json to CardFormInput" should {
    "fail if tag has spaces" in {
      val inputJson = Json.obj("title" -> "foo", "tags" -> Seq("tag with spaces"))
      val form = CardFormInput.form.bind(inputJson)
      form.errors.length mustEqual 1
      form.errors.head.key mustEqual "tags"
      form.errors.head.message mustEqual "Can not contain spaces"
    }
  }
}

class CardControllerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with LoggedInUserAuthContext
    with BeforeAndAfter
    with ScalaFutures
    with MockitoSugar {

  var controller: CardController = null
  var db: Database = null

  override def fakeApplication: Application =
    new GuiceApplicationBuilder().overrides(new LoggedInUserFakeModule).build()

  before {
    controller = app.injector.instanceOf[CardController]
    db = app.injector.instanceOf[Database]
  }

  after { TestUtils.cleanupDb(db) }


  def createCard() = {
    val body = Json.obj("title" -> "Title", "body" -> "Body", "tags" -> List("Tag1", "Tag2"))
    val request = FakeRequest().withJsonBody(body)
    val response = controller.create()(request)
    Await.ready(response, 1000 millis)
    response
  }

  "list" should {

    def runQuery(tagsQuery: String, searchTerm: Option[String] = None): Future[Result] = {
      var query = s"/foo?page=1&pageSize=2"
      if (!tagsQuery.isEmpty()) { query += s"&query=${tagsQuery}" }
      if (!searchTerm.isEmpty) { query += s"&searchTerm=${searchTerm.get}" }
      controller.list()(FakeRequest("GET", query))
    }

    "return a list of elements based on ES matched ids" in {

      //First, creates a card
      val createdCardId = (contentAsJson(createCard()) \ "id").as[String]

      //Mock ES to return it
      CardElasticClientMock.withIdsFound(Seq(createdCardId), 1) {

        //Make a query and see that the response contains the card
        val query = "((tags CONTAINS 'Tag1') AND (tags NOT CONTAINS 'Tag2'))"
        val response = runQuery(query)
        val responseObj = contentAsJson(response)
          (responseObj \ "page").as[Int] mustEqual 1
          (responseObj \ "pageSize").as[Int] mustEqual 2
          (responseObj \ "countOfItems" ).as[Int] mustEqual 1

      }
    }

    "Filter out if there is no match" in  {
      createCard()
      CardElasticClientMock.withIdsFound(Seq(), 0) {
        val responseObj = contentAsJson(runQuery("", Some("FOME VERY CRAZY THING")))
          (responseObj \ "page").as[Int] mustEqual 1
          (responseObj \ "pageSize").as[Int] mustEqual 2
          (responseObj \ "countOfItems" ).as[Int] mustEqual 0
      }

    }
  }

  "getMetadata" should {

    def runGetMetadata() = {
      val request = FakeRequest()
      controller.getMetadata()(request)
    }

    "returns the metadata for cards" in {

      //Creates a card with a couple of tags
      createCard()

      //Runs the request
      val response = runGetMetadata()

      //Expects the two tags to be on the response
      status(response) mustEqual 200
      contentAsJson(response) mustEqual Json.obj("tags" -> Seq("Tag1", "Tag2"))
    }

  }

  "getHistory" should {

    val datetime = DateTime.parse("2021-10-10T12:00:00Z")
    val createEvent = CardCreation(datetime, "1", user.id)
    val bodyUpdate = StringFieldUpdate("body", "old", "new")
    val tagUpdate = TagsFieldUpdate("tags", List("A"), List("B"))
    val fieldUpdates = Seq(bodyUpdate, tagUpdate)
    val updateEvent = CardUpdate(datetime, "1", user.id, fieldUpdates)
    val historyResource = CardHistoryResource(Seq(createEvent, updateEvent))
    val cardData = CardData("1", "title", "body", List(), None, None, 1)
    val cardResource = CardResource.fromCardData(cardData)

    case class TestContext(
      historyTrackerHandler: HistoryTrackerHandlerLike,
      cardResourceHandler: CardResourceHandlerLike,
      controller: CardController
    )

    def testContext(block: TestContext => Any): Any = {
      val historyTrackerHandler = mock[HistoryTrackerHandlerLike]
      when(historyTrackerHandler.get("1")).thenReturn(Future.successful(historyResource))
      val cardResourceHandler = mock[CardResourceHandlerLike]
      when(cardResourceHandler.get("1", user)).thenReturn(Future.successful(Some(cardResource)))
      val controller = new CardController(
        app.injector.instanceOf[ControllerComponents],
        cardResourceHandler,
        app.injector.instanceOf[SilhouetteEnvWrapper].silhouette,
        historyTrackerHandler
      )(
        app.injector.instanceOf[ExecutionContext]
      )
      block(TestContext(historyTrackerHandler, cardResourceHandler, controller))
    }

    "returns the history from the history tracker handler" in testContext { c =>
      val request = FakeRequest()
      val response = c.controller.getHistory("1")(request)
      status(response) mustEqual 200
      contentAsJson(response) mustEqual Json.obj(
        "history" -> Seq(
          Json.obj(
            "datetime" -> "2021-10-10T12:00:00.000Z",
            "eventType" -> "creation"
          ),
          Json.obj(
            "datetime" -> "2021-10-10T12:00:00.000Z",
            "eventType" -> "update",
            "fieldUpdates" -> Seq(
              Json.obj(
                "fieldName" -> "body",
                "fieldType" -> "string",
                "oldValue" -> "old",
                "newValue" -> "new"
              ),
              Json.obj(
                "fieldName" -> "tags",
                "fieldType" -> "tags",
                "oldValue" -> Seq("A"),
                "newValue" -> Seq("B")
              )
            )
          )
        )
      )

    }

    "return 404 if other user" in testContext { c =>
      val request = FakeRequest()
      reset(c.cardResourceHandler)
      when(c.cardResourceHandler.get("1", user)).thenReturn(Future.successful(None))
      val response = c.controller.getHistory("1")(request)
      status(response) mustEqual 404
    }

  }

}

class CardListRequestInputSpec extends PlaySpec {

  "ToCardListRequest" should {
    val user = User("A", "B")

    "Without tags" in {
      (CardListRequestInput(10, 20, None, None, None).toCardListRequest(user)
        mustEqual
        CardListRequest(10, 20, "A", List(), List(), None))
    }

    "With tags" in {
      (CardListRequestInput(10, 20, Some("foo,bar, baz"), None, None).toCardListRequest(user)
        mustEqual
        CardListRequest(10, 20, "A", List("foo", "bar", "baz"), List(), None))
    }

    "With tagsNot" in {
      (CardListRequestInput(10, 20, Some("foo,bar, baz"), Some("B,C"), None)
        .toCardListRequest(user)
        mustEqual
        CardListRequest(10, 20, "A", List("foo", "bar", "baz"), List("B", "C"), None))
    }

    "With query" in {
      (CardListRequestInput(10, 20, Some("foo,bar, baz"), Some("B,C"), Some("FOO"))
        .toCardListRequest(user)
        mustEqual
        CardListRequest(10, 20, "A", List("foo", "bar", "baz"), List("B", "C"), Some("FOO")))
    }

  }

}

class CardListRequestParserSpec
    extends PlaySpec
    with CardListRequestParserTestUtils
    with WithImplicitMessageProvider{

  val pageVal = 2
  val page = Some(pageVal.toString)
  val pageSizeVal = 5
  val pageSize = Some(pageSizeVal.toString)

  "CardListRequestParser.parse with implicit request" should {

    "Base" in {
      implicit val request = FakeRequest("GET", "/foo?page=1&pageSize=2")
      (CardListRequestParser.parse()
        mustEqual
        Left(CardListRequestInput(1,2,None,None,None)))
    }

    "Return error if page is missing" in {
      implicit val request = FakeRequest("GET", "/foo?pageSize=1")
      CardListRequestParser.parse().isBadWithJsKey("page") mustEqual true
    }

    "Return error if pageSize is missing" in {
      implicit val request = FakeRequest("GET", "/foo?page=1")
      CardListRequestParser.parse().isBadWithJsKey("pageSize") mustEqual true
    }

    "Return error if page is not a number" in {
      implicit val request = FakeRequest("GET", "/foo?page=aaa&pageSize=2")
      CardListRequestParser.parse().isBadWithJsKey("page") mustEqual true
    }

    "Returns success if all good" in {
      implicit val request = FakeRequest("GET", "/foo?page=2&pageSize=2")
      CardListRequestParser.parse() mustEqual Left(CardListRequestInput(2,2,None,None,None))
    }

    "With tags work fine" in {
      implicit val request = FakeRequest("GET", "/foo?page=2&pageSize=2&tags=foo,bar")
      (CardListRequestParser.parse()
        mustEqual
        Left(CardListRequestInput(2,2,Some("foo,bar"),None,None)))
    }

    "With tagsNot work fine" in {
      implicit val request = FakeRequest("GET", "/foo?page=2&pageSize=2&tags=foo,bar&tagsNot=a")
      (CardListRequestParser.parse()
        mustEqual
        Left(CardListRequestInput(2,2,Some("foo,bar"),Some("a"),None)))
    }

    "With query work fine" in {
      implicit val request = FakeRequest("GET", "/foo?page=2&pageSize=2&query=FOO")
      (CardListRequestParser.parse()
        mustEqual
        Left(CardListRequestInput(2,2,None,None,Some("FOO"))))
    }

  }

}

