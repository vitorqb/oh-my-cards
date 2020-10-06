package tests.integration

import org.scalatestplus.play.PlaySpec

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span
import org.scalatest.time.Millis

import play.api.db.Database
import play.api.libs.ws.WSClient
import play.api.libs.json.{Json,JsObject}
import play.api.libs.json.JsValue
import v1.admin.testUtils.TestEsClient
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import test.utils.FunctionalTestsTag
import test.utils.TestUtils
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import test.utils.WaitUntil
import org.scalatest.BeforeAndAfterEach
import v1.card.CardElasticClientLike
import v1.card.elasticclient.CardElasticClientImpl


/**
  * Helper class that allows posting a card for testing.
  */
class CardActionsWsHelper(wsClient: WSClient, port: Int, token: String) extends ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = {
    new PatienceConfig(scaled(Span(2000, Millis)))
  }

  val url = s"http://localhost:$port/v1/cards"

  def client(url: String = url) =
    wsClient.url(url).withHttpHeaders("Authorization" -> s"Bearer $token")

  def postCardData(cardData: JsObject): String = {
    (client().post(cardData).futureValue.json \ "id").as[String]
  }

  def postCardData(title: String, body: String): String = {
    postCardData(Json.obj("title" -> title, "body" -> body))
  }

  def getPagedCards(page: Int, pageSize: Int): JsValue =
    client()
      .withQueryStringParameters("page" -> page.toString, "pageSize" -> pageSize.toString)
      .get()
      .futureValue
      .json

  def getCard(id: String): JsValue = client(url + s"/${id}").get().futureValue.json

}


class CardActionsSpec
    extends PlaySpec
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with ScalaFutures
    with TestEsClient
    with WaitUntil {

  var token: String = ""

  override def beforeEach() = {
    cleanIndex()
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
    token = (new TestTokenProviderSvc(app.injector.instanceOf[Database])).getToken()
  }

  /**
    * Overrides the default application to provide the ES client.
    */
  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .overrides(new TestEsFakeModule)
      .overrides(bind[CardElasticClientLike].to[CardElasticClientImpl])
      .build()

  def wsClient: WSClient = app.injector.instanceOf[WSClient]

  def cardActionWsHelper() = new CardActionsWsHelper(wsClient, port, token)

  val index = "cards"

  "test create and get two cards paginated" taggedAs(FunctionalTestsTag) in {
    val cardOneId:   String = cardActionWsHelper.postCardData("Foo1", "Bar1")
    val cardTwoId:   String = cardActionWsHelper.postCardData("Foo2", "Bar2")
    val cardThreeId: String = cardActionWsHelper.postCardData("Foo3", "Bar3")

    refreshIdx(index)

    waitUntil { () =>
      (cardActionWsHelper.getPagedCards(1, 2) \ "items" \ 1 \ "title").asOpt[String].isDefined
    }

    val pagedData = cardActionWsHelper.getPagedCards(1, 2)

    (pagedData \ "page").as[Int] mustEqual 1
      (pagedData \ "pageSize").as[Int] mustEqual 2
    Array("Foo1", "Foo2", "Foo3") must contain ((pagedData \ "items" \ 0 \ "title").as[String])
    Array("Foo1", "Foo2", "Foo3") must contain ((pagedData \ "items" \ 1 \ "title").as[String])
    ((pagedData \ "items" \ 0 \ "title").as[String]
      must not equal
      (pagedData \ "items" \ 1 \ "title").as[String])

  }

  "Card creationg" should {
    "create a card with ref, updatedAt, createdAt" taggedAs(FunctionalTestsTag) in {
      val id = cardActionWsHelper.postCardData("FOO", "BAR")
      refreshIdx(index)
      val card = cardActionWsHelper.getCard(id)

      (card \ "id").as[String] mustEqual id
      (card \ "ref").as[Int] mustEqual 1001
      (card \ "createdAt").as[String].length() > 10
      (card \ "updatedAt").as[String] mustEqual (card \ "createdAt").as[String]
      (card \ "title").as[String] mustEqual "FOO"
      (card \ "body").as[String] mustEqual "BAR"
      (card \ "tags").as[List[String]] mustEqual Seq()
    }
  }
}
