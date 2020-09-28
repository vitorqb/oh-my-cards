package v1.card.cardrepositoryspec

import v1.card._
import org.scalatestplus.play.PlaySpec
import org.mockito.MockitoSugar
import org.mockito.Mockito._
import v1.auth.User
import v1.card.testUtils.ComponentsBuilder
import play.api.db.Database
import scala.util.Success
import java.sql.Connection
import org.mockito.{ ArgumentMatchersSugar }
import v1.card.testUtils.MockDb
import scala.util.Failure
import org.joda.time.DateTime
import v1.card.CardRefGenerator.{CardRefGeneratorLike,CardRefGenerator}
import v1.card.cardrepositorycomponents.CardRepositoryComponents
import test.utils.TestUtils
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import v1.card.tagsrepository.TagsRepository
import v1.admin.testUtils.TestEsClient
import scala.concurrent.ExecutionContext
import test.utils.FunctionalTestsTag
import services.CounterUUIDGenerator
import services.FrozenClock
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.Span
import org.scalatest.time.Millis

class FindResultSpec extends PlaySpec {

  "fromQueryResults" should {
    val cardData1 = CardData("id1", "ONE", "TWO", List("a", "b"), ref=1)
    val cardData2 = CardData("id2", "one", "two", List("A", "B", "D"), ref=2)
    val cardData = List(cardData1, cardData2)
    val idsResult = CardElasticIdFinder.Result(Seq("id2", "id1"), 5)
    val findResult = FindResult.fromQueryResults(cardData, idsResult)

    "have the same countOfids from idsResult" in {
      findResult.countOfItems mustEqual 5
    }

    "sort the sequence of card data by the ids in the idsResult" in {
      findResult.cards mustEqual Seq(cardData2, cardData1)
    }
  }

}

class CardRepositorySpec extends PlaySpec with MockitoSugar with ArgumentMatchersSugar {

  case class TestContext(
    val dataRepo: CardDataRepositoryLike,
    val tagsRepo: TagsRepositoryLike,
    val esClient: CardElasticClient,
    val repo: CardRepositoryLike
  )

  val user = User("A", "A@B.com")
  val now = new DateTime(2000, 1, 1, 0, 0, 0)
  val context = CardCreationContext(user, now, "id", 1)
  val connection = mock[Connection]
  val db = new MockDb {
    override def withTransaction[A](block: Connection => A): A = block(connection)
  }
  val components = ComponentsBuilder().withDb(db).withContext(context).build()

  def testContext(block: TestContext => Any): Any = {
    val dataRepo = mock[CardDataRepositoryLike]
    when(dataRepo.create(any, any)(any)).thenReturn(Success("id"))
    val tagsRepo = mock[TagsRepositoryLike]
    val esClient = mock[CardElasticClient]
    val repo = new CardRepository(dataRepo, tagsRepo, esClient, components)
    TestContext(dataRepo, tagsRepo, esClient, repo)
  }

  "create" should {

    "send create msg to card data repository" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), None)
      c.repo.create(formInput, user)
      verify(c.dataRepo).create(formInput, context)(connection)
    }

    "send create msg to tags repo" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), Some(List("A")))
      c.repo.create(formInput, user)
      verify(c.tagsRepo).create("id", List("A"))(connection)
    }

    "send create data to es client" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), Some(List("A")))
      c.repo.create(formInput, user)
      verify(c.esClient).create(formInput, context)
    }

    "returns created id" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), None)
      c.repo.create(formInput, user).get mustEqual "id"
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
  val now = new DateTime(2000, 1, 1, 0, 0, 0)
  val createContext = CardCreationContext(user, now, "1", 1)
  val baseCardInput = CardFormInput("Title", Some("Body"), Some(List("Tag1", "TagTwo")))
  val baseExpectedCardData = baseCardInput.asCardData("1", Some(now), Some(now), 1)

  case class TestContext(components: CardRepositoryComponentsLike, repo: CardRepositoryLike)

  def testContext(block: TestContext => Any): Any = {
    TestUtils.testDB { db =>
      val components = ComponentsBuilder()
        .withDb(db)
        .withUUIDGenerator(new CounterUUIDGenerator)
        .withRefGenerator(new CardRefGenerator(db))
        .withClock(new FrozenClock(now))
        .build()
      val tagsRepo = new TagsRepository()
      val esClient = new CardElasticClientImpl(client)
      val dataRepo = new CardDataRepository(components, tagsRepo, esClient)
      val repo = new CardRepository(dataRepo, tagsRepo, esClient, components)
      val testContext = TestContext(components, repo)
      try {
        block(testContext)
      } finally {
        cleanIndex(index)
        TestUtils.cleanupDb(db)
      }
    }

  }

  "Functional tests for card creation and deletion" should {

    "create and get a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).get mustEqual "1"
      c.repo.get("1", user).get mustEqual baseExpectedCardData
    }

    "create and find 2 cards" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).get
      c.repo.create(baseCardInput, user).get
      refreshIdx()

      val listRequest = CardListRequest(1, 2, user.id, List(), List(), None, None)
      val response = c.repo.find(listRequest).futureValue

      response.countOfItems mustEqual 2
      response.cards.length mustEqual 2
      response.cards(0) mustEqual baseExpectedCardData.copy(id="1", ref=1)
      response.cards(1) mustEqual baseExpectedCardData.copy(id="2", ref=2)
    }

    "update a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).get
      refreshIdx()

      val newCardData = baseExpectedCardData.copy(title="A", body="B", tags=List())
      c.repo.update(newCardData, user).futureValue

      c.repo.get("1", user).get mustEqual newCardData
    }

    "create and delete a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).get
      refreshIdx()
      c.repo.delete("1", user).futureValue

      c.repo.get("1", user) mustEqual None
    }

    "create three cards and find 2 with search term" in testContext { c =>
      val input_1 = baseCardInput.copy(title="SomeLongWord")
      val input_2 = baseCardInput.copy(title="SomeLongWo")
      val input_3 = baseCardInput.copy(title="Nothing to do with the others")

      c.repo.create(input_1, user)
      c.repo.create(input_2, user)
      c.repo.create(input_3, user)
      refreshIdx()

      val listRequest = CardListRequest(1, 3, user.id, List(), List(), None, Some("SomeLongWord"))
      val response = c.repo.find(listRequest).futureValue

      val expected_1 = baseExpectedCardData.copy(title="SomeLongWord")
      val expected_2 = baseExpectedCardData.copy(id="2", title="SomeLongWo", ref=2)

      response.countOfItems mustEqual 2
      response.cards mustEqual Seq(expected_1, expected_2)
    }

    "get with pagination" in testContext { c =>
      c.repo.create(baseCardInput, user)
      c.repo.create(baseCardInput, user)
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

}
