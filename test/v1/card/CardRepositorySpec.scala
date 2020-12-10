package v1.card.cardrepositoryspec

import v1.card._
import org.scalatestplus.play.PlaySpec
import org.mockito.MockitoSugar
import v1.auth.User
import java.sql.Connection
import org.mockito.{ ArgumentMatchersSugar }
import v1.card.testUtils.MockDb
import org.joda.time.DateTime
import test.utils.TestUtils
import v1.card.tagsrepository.TagsRepository
import v1.admin.testUtils.TestEsClient
import scala.concurrent.ExecutionContext
import test.utils.FunctionalTestsTag
import services.CounterUUIDGenerator
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span
import org.scalatest.time.Millis
import v1.card.elasticclient.CardElasticClientImpl
import v1.card.historytracker.CardHistoryTracker
import v1.card.historytracker.HistoricalEventCoreRepository
import v1.card.historytracker.CardUpdateDataRepository

class CardRepositorySpec
    extends PlaySpec
    with MockitoSugar
    with ArgumentMatchersSugar
    with ScalaFutures
{

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  case class TestContext(
    val dataRepo: CardDataRepositoryLike,
    val tagsRepo: TagsRepositoryLike,
    val esClient: CardElasticClientLike,
    val historyRecorder: CardHistoryRecorderLike,
    val repo: CardRepositoryLike
  )

  val user = User("A", "A@B.com")
  val now = new DateTime(2000, 1, 1, 0, 0, 0)
  val context = CardCreationContext(user, now, "id", 1)
  val connection = mock[Connection]
  val db = new MockDb {
    override def withTransaction[A](block: Connection => A): A = block(connection)
    override def withConnection[A](block: Connection => A): A = block(connection)
  }
  def testContext(block: TestContext => Any): Any = {
    val dataRepo = mock[CardDataRepositoryLike]
    val tagsRepo = mock[TagsRepositoryLike]
    val esClient = mock[CardElasticClientLike]
    val historyRecorder = mock[CardHistoryRecorderLike]
    val repo = new CardRepository(dataRepo, tagsRepo, esClient, historyRecorder, db)
    val testContext = TestContext(dataRepo, tagsRepo, esClient, historyRecorder, repo)
    block(testContext)
  }

  "create" should {

    "send create msg to card data repository" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), None)
      c.repo.create(formInput, context).futureValue
      verify(c.dataRepo).create(formInput.toCreateData(), context)(connection)
    }

    "send create msg to tags repo" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), Some(List("A")))
      c.repo.create(formInput, context).futureValue
      verify(c.tagsRepo).create("id", List("A"))(connection)
    }

    "send create data to es client" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), Some(List("A")))
      c.repo.create(formInput, context).futureValue
      verify(c.esClient).create(formInput, context)
    }

    "send create data to history recorder" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), Some(List("A")))
      c.repo.create(formInput, context).futureValue
      verify(c.historyRecorder).registerCreation(context)(connection)
    }

    "returns created id" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), None)
      c.repo.create(formInput, context).futureValue mustEqual "id"
    }

  }

  "delete" should {

    val cardData = CardData("1", "title", "body", List("A"), Some(now), Some(now), 1)
    val context = CardUpdateContext(user, now, cardData)

    "send delete msg to card data repository" in testContext { c =>
      c.repo.delete(cardData, context).futureValue
      verify(c.dataRepo).delete("1", user)(connection)
    }

    "send delete msg to tags repository" in testContext { c =>
      c.repo.delete(cardData, context).futureValue
      verify(c.tagsRepo).delete("1")(connection)
    }

    "send delete msg to es client" in testContext { c =>
      c.repo.delete(cardData, context).futureValue
      verify(c.esClient).delete("1")
    }

    "send delete msg to history tracker" in testContext { c =>
      c.repo.delete(cardData, context).futureValue
      verify(c.historyRecorder).registerDeletion(context)(connection)
    }
  }

  "update" should {

    val oldData = CardData("1", "to", "bo", List("A", "O"), Some(now), Some(now), 1)
    val data = CardData("1", "t", "b", List("A"), Some(now), Some(now), 1)
    val context = CardUpdateContext(user, now, oldData)

    "send update msg to card data repository" in testContext { c =>
      c.repo.update(data, context).futureValue
      verify(c.dataRepo).update(data, context)(connection)
    }

    "send update msg to tags repository" in testContext { c =>
      c.repo.update(data, context).futureValue
      verify(c.tagsRepo).update(data)(connection)
    }

    "send update msg to es client" in testContext { c =>
      c.repo.update(data, context).futureValue
      verify(c.esClient).update(data, context)
    }

    "send update msg to history tracker" in testContext { c =>
      c.repo.update(data, context).futureValue
      verify(c.historyRecorder).registerUpdate(data, context)(connection)
    }
}

}

class CardRepositoryIntegrationSpec
    extends PlaySpec
    with TestEsClient
    with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  override implicit def patienceConfig = new PatienceConfig(Span(1000, Millis))

  val index = "cards"
  val user = User("A", "A@B.com")
  val otherUser = User("otherUser", "other@user.com")
  val now = new DateTime(2000, 1, 1, 0, 0, 0)
  val baseCreateContext = CardCreationContext(user, now, "1", 1)
  val baseCardInput = CardFormInput("Title", Some("Body"), Some(List("Tag1", "TagTwo")))
  val baseExpectedCardData = baseCardInput.asCardData(baseCreateContext)
  val baseUpdateContext = CardUpdateContext(user, now, baseExpectedCardData)

  case class TestContext(repo: CardRepositoryLike)

  def testContext(block: TestContext => Any): Any = {
    TestUtils.testDB { db =>
      val tagsRepo = new TagsRepository()
      val esClient = new CardElasticClientImpl(client)
      val dataRepo = new CardDataRepository
      //!!!! TODO Use builder
      val historyRecorder = new CardHistoryTracker(
        new CounterUUIDGenerator,
        new HistoricalEventCoreRepository,
        new CardUpdateDataRepository(new CounterUUIDGenerator)
      )
      val repo = new CardRepository(dataRepo, tagsRepo, esClient, historyRecorder, db)
      val testContext = TestContext(repo)
      try {
        block(testContext)
      } finally {
        cleanIndex()
      }
    }

  }

  "Functional tests for card creation and deletion" should {

    "create and get c card without tag not body" taggedAs(FunctionalTestsTag) in testContext { c =>
      val input = baseCardInput.copy(tags=None, body=None)
      c.repo.create(input, baseCreateContext).futureValue mustEqual "1"
      val expectedData = baseExpectedCardData.copy(body="", tags=List())
      c.repo.get("1", user).futureValue mustEqual Some(expectedData)
    }

    "create and get a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, baseCreateContext).futureValue mustEqual "1"
      c.repo.get("1", user).futureValue mustEqual Some(baseExpectedCardData)
      c.repo.get("1", otherUser).futureValue mustEqual None
    }

    "create and find 2 cards" taggedAs(FunctionalTestsTag) in testContext { c =>
      val input1 = baseCardInput
      val context1 = baseCreateContext
      val data1 = input1.asCardData(context1)
      val input2 = baseCardInput.copy(title="input2")
      val context2 = baseCreateContext.copy(id="2", ref=2)
      val data2 = input2.asCardData(context2)
      c.repo.create(input1, context1).futureValue
      c.repo.create(input2, context2).futureValue
      refreshIdx()

      val listRequest = CardListRequest(1, 2, user.id, List(), List(), None, None)
      val response = c.repo.find(listRequest).futureValue

      response.countOfItems mustEqual 2
      response.cards.length mustEqual 2
      response.cards(0) mustEqual data1
      response.cards(1) mustEqual data2
    }

    "update a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, baseCreateContext).futureValue
      refreshIdx()

      val oldCardData = baseExpectedCardData
      val newCardData = baseExpectedCardData.copy(title="A", body="B", tags=List())
      val context = CardUpdateContext(user, now, oldCardData)
      c.repo.update(newCardData, context).futureValue

      c.repo.get("1", user).futureValue mustEqual Some(newCardData)
    }

    "create and delete a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, baseCreateContext).futureValue
      refreshIdx()
      c.repo.delete(baseExpectedCardData, baseUpdateContext).futureValue

      c.repo.get("1", user).futureValue mustEqual None
    }

    "deletes a card that does not exist" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.delete(baseExpectedCardData, baseUpdateContext).failed.futureValue mustBe a[CardDoesNotExist]
    }

    "deletes a card from other user" taggedAs(FunctionalTestsTag) in testContext { c =>
      val updateContext = baseUpdateContext.copy(user=otherUser)
      c.repo.create(baseCardInput, baseCreateContext).futureValue
      refreshIdx()
      c.repo.delete(baseExpectedCardData, updateContext).failed.futureValue mustBe a[CardDoesNotExist]
    }

    "create three cards and find 2 with search term" taggedAs(FunctionalTestsTag) in testContext { c =>
      val input_1 = baseCardInput.copy(title="SomeLongWord")
      val context_1 = baseCreateContext.copy(id="1", ref=1)
      val data_1 = input_1.asCardData(context_1)
      val input_2 = baseCardInput.copy(title="SomeLongWo")
      val context_2 = baseCreateContext.copy(id="2", ref=2)
      val data_2 = input_2.asCardData(context_2)
      val input_3 = baseCardInput.copy(title="Nothing to do with the others")
      val context_3 = baseCreateContext.copy(id="3", ref=3)
      val data_3 = input_3.asCardData(context_3)

      c.repo.create(input_1, context_1).futureValue
      c.repo.create(input_2, context_2).futureValue
      c.repo.create(input_3, context_3).futureValue
      refreshIdx()

      val listRequest = CardListRequest(1, 3, user.id, List(), List(), None, Some("SomeLongWord"))
      val response = c.repo.find(listRequest).futureValue

      response.countOfItems mustEqual 2
      response.cards mustEqual Seq(data_1, data_2)
    }

    "get with pagination" taggedAs(FunctionalTestsTag) in testContext { c =>
      val input1 = baseCardInput
      val context1 = baseCreateContext

      val input2 = baseCardInput.copy(title="input2")
      val context2 = baseCreateContext.copy(id="2", ref=2)

      c.repo.create(input1, context1).futureValue
      c.repo.create(input2, context2).futureValue
      refreshIdx()

      val listRequest1 = CardListRequest(1, 1, user.id, List(), List(), None, None)
      val response1 = c.repo.find(listRequest1).futureValue
      response1.countOfItems mustEqual 2
      response1.cards.length mustEqual 1

      val listRequest2 = CardListRequest(2, 1, user.id, List(), List(), None, None)
      val response2 = c.repo.find(listRequest2).futureValue
      response2.countOfItems mustEqual 2
      response2.cards.length mustEqual 1
    }
  }

  "Functional tests for find" should {

    "Match by tag" taggedAs(FunctionalTestsTag) in testContext { c =>
      val card1 = CardFormInput("Title", Some("Body"), Some(List("Tag1", "Tag2")))
      val context1 = CardCreationContext(user, now, "1", 1)

      val card2 = CardFormInput("Title", Some("Body"), Some(List("Tag2", "Tag3")))
      val context2 = CardCreationContext(user, now, "2", 2)

      c.repo.create(card1, context1).futureValue
      c.repo.create(card2, context2).futureValue
      val findRequest = CardListRequest(1, 10, user.id, List("Tag3"), List(), None)
      refreshIdx()

      val result = c.repo.find(findRequest).futureValue

      val expResult = FindResult(Seq(card2.asCardData(context2)), 1)
      result mustEqual expResult
    }

    "Match by search term returning in order" taggedAs(FunctionalTestsTag) in testContext { c =>
      val card1 = CardFormInput("Title", Some("Body"), Some(List("Tag1", "Tag2")))
      val context1 = CardCreationContext(user, now, "1", 1)

      val card2 = card1.copy(title="Titlee")
      val context2 = context1.copy(id="2", ref=2)

      val card3 = card2.copy(title="Tilleee")
      val context3 = context2.copy(id="3", ref=3)

      c.repo.create(card1, context1).futureValue
      c.repo.create(card2, context2).futureValue
      c.repo.create(card3, context3).futureValue
      refreshIdx()

      val findRequest = CardListRequest(1, 10, user.id, List(), List(), None, Some("Titleee"))
      val result = c.repo.find(findRequest).futureValue

      val cardOneData = card1.asCardData(context1)
      val cardTwoData = card2.asCardData(context2)
      val cardThreeData = card3.asCardData(context3)
      val expResult = FindResult(Seq(cardThreeData, cardTwoData, cardOneData), 3)
      result mustEqual expResult
    }
  }

}
