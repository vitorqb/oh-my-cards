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
import v1.card.CardElasticClient
import v1.card.CardElasticClientImpl
import test.utils.FunctionalTestsTag
import test.utils.TestUtils
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import test.utils.WaitUntil


/**
  * Helper class that allows posting a card for testing.
  */
class CardActionsWsHelper(wsClient: WSClient, port: Int, token: String) extends ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = {
    new PatienceConfig(scaled(Span(2000, Millis)))
  }

  val url = s"http://localhost:$port/v1/cards"

  def postCardData(cardData: JsObject): String = {
    (wsClient
      .url(url)
      .withHttpHeaders("Authorization" -> s"Bearer $token")
      .post(cardData)
      .futureValue.json \ "id").as[String]
  }

  def postCardData(title: String, body: String): String = {
    postCardData(Json.obj("title" -> title, "body" -> body))
  }

  def getPagedCards(page: Int, pageSize: Int): JsValue = {
    (wsClient
      .url(url)
      .withHttpHeaders("Authorization" -> s"Bearer $token")
      .withQueryStringParameters("page" -> page.toString, "pageSize" -> pageSize.toString)
      .get()
      .futureValue
      .json)
  }

}


class CardActionsSpec
    extends PlaySpec
    with GuiceOneServerPerSuite
    with BeforeAndAfter
    with ScalaFutures
    with TestEsClient
    with WaitUntil {

  after {
    cleanIndex("cards")
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
  }

  /**
    * Overrides the default application to provide the ES client.
    */
  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .overrides(new TestEsFakeModule)
      .overrides(bind[CardElasticClient].to[CardElasticClientImpl])
      .build()

  def wsClient: WSClient = app.injector.instanceOf[WSClient]

  "test create and get two cards paginated" taggedAs(FunctionalTestsTag) in {
    val token = (new TestTokenProviderSvc(app.injector.instanceOf[Database])).getToken()
    val cardActionWsHelper = new CardActionsWsHelper(wsClient, port, token)

    val cardOneId:   String = cardActionWsHelper.postCardData("Foo1", "Bar1")
    val cardTwoId:   String = cardActionWsHelper.postCardData("Foo2", "Bar2")
    val cardThreeId: String = cardActionWsHelper.postCardData("Foo3", "Bar3")

    refreshIdx("cards")

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

}
