package tests.integration

import org.scalatestplus.play.PlaySpec

import org.scalatestplus.play.guice.GuiceOneServerPerTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span
import org.scalatest.time.Millis

import play.api.db.Database
import play.api.libs.ws.WSClient
import play.api.libs.json.{Json,JsObject}
import play.api.libs.json.JsValue


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


class CardActionsSpec extends PlaySpec with GuiceOneServerPerTest with ScalaFutures {

  def wsClient: WSClient = app.injector.instanceOf[WSClient]

  "test create and get two cards paginated" in {
    val token = (new TestTokenProviderSvc(app.injector.instanceOf[Database])).getToken()
    val cardActionWsHelper = new CardActionsWsHelper(wsClient, port, token)

    val cardOneId:   String = cardActionWsHelper.postCardData("Foo1", "Bar1")
    val cardTwoId:   String = cardActionWsHelper.postCardData("Foo2", "Bar2")
    val cardThreeId: String = cardActionWsHelper.postCardData("Foo3", "Bar3")

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
