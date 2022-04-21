package tests.integration

import org.scalatestplus.play.PlaySpec
import test.utils.FunctionalTestsTag
import v1.admin.testUtils.TestEsClient
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.db.Database
import test.utils.TestUtils
import play.api.libs.json._

class CardHistorySpec
    extends PlaySpec
    with GuiceOneServerPerSuite
    with TestEsClient {

  /**
    * Overrides the default application to provide the ES client.
    */
  override def fakeApplication: Application =
    new GuiceApplicationBuilder().overrides(new TestEsFakeModule).build()

  /**
    * Has the context for a test
    */
  case class TestContext(val cardActionsWsHelper: CardActionsWsHelper)
  def testContext(block: TestContext => Any): Any = {
    val db = app.injector.instanceOf[Database]
    val tokenProvider = new TestTokenProviderSvc(db)
    val token = tokenProvider.getToken()
    val wsClient = app.injector.instanceOf[WSClient]
    val cardActionsWsHelper = new CardActionsWsHelper(wsClient, port, token)
    val context = TestContext(cardActionsWsHelper)
    try {
      block(context)
    } finally {
      TestUtils.cleanupDb(db)
      cleanIndex()
    }
  }

  "requesting a cards history" should {

    "return history after create, update and delete" taggedAs (FunctionalTestsTag) in testContext {
      c =>
        val id = c.cardActionsWsHelper.postNewCard("Old Title", "Old body")
        val data = c.cardActionsWsHelper.getCard(id).json
        val newData = data
          .transform(
            __.json.update((__ \ "title").json.put(JsString("New Title")))
          )
          .get
          .transform(
            __.json.update((__ \ "body").json.put(JsString("New body")))
          )
          .get
          .transform(
            __.json.update((__ \ "tags").json.put(JsArray(Seq(JsString("A")))))
          )
          .get
        c.cardActionsWsHelper.postCard(newData)
        c.cardActionsWsHelper.deleteCard(id)

        val history = c.cardActionsWsHelper.getHistory(id)
        (history \ "history" \ 0 \ "eventType").as[String] mustEqual "creation"
        (history \ "history" \ 1 \ "eventType").as[String] mustEqual "update"
        (history \ "history" \ 2 \ "eventType").as[String] mustEqual "deletion"

        (history \ "history" \ 1 \ "fieldUpdates" \ 0 \ "fieldName")
          .as[String] mustEqual "title"
        (history \ "history" \ 1 \ "fieldUpdates" \ 0 \ "fieldType")
          .as[String] mustEqual "string"
        (history \ "history" \ 1 \ "fieldUpdates" \ 0 \ "oldValue")
          .as[String] mustEqual "Old Title"
        (history \ "history" \ 1 \ "fieldUpdates" \ 0 \ "newValue")
          .as[String] mustEqual "New Title"

        (history \ "history" \ 1 \ "fieldUpdates" \ 1 \ "fieldName")
          .as[String] mustEqual "body"
        (history \ "history" \ 1 \ "fieldUpdates" \ 1 \ "fieldType")
          .as[String] mustEqual "string"
        (history \ "history" \ 1 \ "fieldUpdates" \ 1 \ "oldValue")
          .as[String] mustEqual "Old body"
        (history \ "history" \ 1 \ "fieldUpdates" \ 1 \ "newValue")
          .as[String] mustEqual "New body"

        (history \ "history" \ 1 \ "fieldUpdates" \ 2 \ "fieldName")
          .as[String] mustEqual "tags"
        (history \ "history" \ 1 \ "fieldUpdates" \ 2 \ "fieldType")
          .as[String] mustEqual "tags"
        (history \ "history" \ 1 \ "fieldUpdates" \ 2 \ "oldValue")
          .as[Seq[String]] mustEqual Seq()
        (history \ "history" \ 1 \ "fieldUpdates" \ 2 \ "newValue")
          .as[Seq[String]] mustEqual Seq("A")
    }

  }

}
