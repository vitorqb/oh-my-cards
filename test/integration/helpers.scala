package tests.integration

import play.api.db.Database
import org.scalatest.concurrent.ScalaFutures
import anorm.{SQL}
import play.api.libs.ws.WSClient
import org.scalatest.time.Span
import org.scalatest.time.Millis
import play.api.libs.json.{Json, JsObject}
import play.api.libs.json.JsValue
import play.api.libs.ws.WSResponse

/**
  * Helper class that allows posting a card for testing.
  */
class CardActionsWsHelper(wsClient: WSClient, port: Int, token: String)
    extends ScalaFutures {

  override implicit val patienceConfig: PatienceConfig = {
    new PatienceConfig(scaled(Span(2000, Millis)))
  }

  val url = s"http://localhost:$port/v1/cards"

  def client(url: String = url) =
    wsClient.url(url).withHttpHeaders("Authorization" -> s"Bearer $token")

  def postNewCard(cardData: JsObject): String = {
    (client().post(cardData).futureValue.json \ "id").as[String]
  }

  def postNewCard(title: String, body: String): String = {
    postNewCard(Json.obj("title" -> title, "body" -> body))
  }

  def postCard(cardData: JsObject): Unit = {
    val id = (cardData \ "id").as[String]
    client(url + "/" + id).post(cardData).futureValue
  }

  def deleteCard(id: String): Unit =
    client(url + "/" + id).delete().futureValue

  def getPagedCards(page: Int, pageSize: Int): JsValue =
    client()
      .withQueryStringParameters(
        "page" -> page.toString,
        "pageSize" -> pageSize.toString
      )
      .get()
      .futureValue
      .json

  def getCard(id: String): WSResponse =
    client(url + s"/${id}").get().futureValue

  def getHistory(id: String): JsValue =
    client(url + "/" + id + "/history").get().futureValue.json

}

/**
  * Helper class that provides a valid user token for testing.
  */
class TestTokenProviderSvc(db: Database) extends ScalaFutures {

  private var token: Option[String] = None

  def getToken(): String =
    token match {
      case Some(x) => x
      case None => {
        db.withConnection { implicit c =>
          SQL(
            """INSERT INTO users(id, email, isAdmin) VALUES(1, "test@test.com", FALSE)"""
          ).executeInsert()
          SQL("""
            | INSERT INTO userTokens(userId, token, expirationDateTime, hasBeenInvalidated)
            | VALUES(1, "FOOBARBAZ", "4102444800000", false)
            """.stripMargin).executeInsert()
          token = Some("FOOBARBAZ")
          getToken()
        }
      }
    }
}
