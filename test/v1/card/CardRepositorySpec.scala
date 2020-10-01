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
import scala.concurrent.Future
import v1.card.elasticclient.CardElasticClientImpl

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
  val components = ComponentsBuilder().withDb(db).withContext(context).build()

  def testContext(block: TestContext => Any): Any = {
    val dataRepo = mock[CardDataRepositoryLike]
    val tagsRepo = mock[TagsRepositoryLike]
    val esClient = mock[CardElasticClientLike]
    val repo = new CardRepository(dataRepo, tagsRepo, esClient, components)
    val testContext = TestContext(dataRepo, tagsRepo, esClient, repo)
    block(testContext)
  }

  "create" should {

    "send create msg to card data repository" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), None)
      c.repo.create(formInput, user).futureValue
      verify(c.dataRepo).create(formInput, context)(connection)
    }

    "send create msg to tags repo" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), Some(List("A")))
      c.repo.create(formInput, user).futureValue
      verify(c.tagsRepo).create("id", List("A"))(connection)
    }

    "send create data to es client" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), Some(List("A")))
      c.repo.create(formInput, user).futureValue
      verify(c.esClient).create(formInput, context)
    }

    "returns created id" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), None)
      c.repo.create(formInput, user).futureValue mustEqual "id"
    }

  }

  "delete" should {
    
    "send delete msg to card data repository" in testContext { c =>
      c.repo.delete("1", user).futureValue
      verify(c.dataRepo).delete("1", user)(connection)
    }

    "send delete msg to tags repository" in testContext { c =>
      c.repo.delete("1", user).futureValue
      verify(c.tagsRepo).delete("1")(connection)
    }

    "send delete msg to es client" in testContext { c =>
      c.repo.delete("1", user).futureValue
      verify(c.esClient).delete("1")
    }

  }

  "update" should {

    val data = CardData("1", "t", "b", List("A"), Some(now), Some(now), 1)
    val context = CardUpdateContext(user, now)

    "send update msg to card data repository" in testContext { c =>
      c.repo.update(data, user).futureValue
      verify(c.dataRepo).update(data, context)(connection)
    }

    "send update msg to tags repository" in testContext { c =>
      c.repo.update(data, user).futureValue
      verify(c.tagsRepo).update(data)(connection)
    }

    "send update msg to es client" in testContext { c =>
      c.repo.update(data, user).futureValue
      verify(c.esClient).update(data, context)
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
      val dataRepo = new CardDataRepository
      val repo = new CardRepository(dataRepo, tagsRepo, esClient, components)
      val testContext = TestContext(components, repo)
      try {
        TestUtils.cleanupDb(db)
        block(testContext)
      } finally {
        cleanIndex(index)
        TestUtils.cleanupDb(db)
      }
    }

  }

  "Functional tests for card creation and deletion" should {

    "create and get c card without tag not body" taggedAs(FunctionalTestsTag) in testContext { c =>
      val input = baseCardInput.copy(tags=None, body=None)
      c.repo.create(input, user).futureValue mustEqual "1"
      val expectedData = baseExpectedCardData.copy(body="", tags=List())
      c.repo.get("1", user).futureValue mustEqual Some(expectedData)
    }

    "create and get a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).futureValue mustEqual "1"
      c.repo.get("1", user).futureValue mustEqual Some(baseExpectedCardData)
      c.repo.get("1", otherUser).futureValue mustEqual None
    }

    "create and find 2 cards" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).futureValue
      c.repo.create(baseCardInput, user).futureValue
      refreshIdx()

      val listRequest = CardListRequest(1, 2, user.id, List(), List(), None, None)
      val response = c.repo.find(listRequest).futureValue

      response.countOfItems mustEqual 2
      response.cards.length mustEqual 2
      response.cards(0) mustEqual baseExpectedCardData.copy(id="1", ref=1)
      response.cards(1) mustEqual baseExpectedCardData.copy(id="2", ref=2)
    }

    "update a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).futureValue
      refreshIdx()

      val newCardData = baseExpectedCardData.copy(title="A", body="B", tags=List())
      c.repo.update(newCardData, user).futureValue

      c.repo.get("1", user).futureValue mustEqual Some(newCardData)
    }

    "create and delete a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).futureValue
      refreshIdx()
      c.repo.delete("1", user).futureValue

      c.repo.get("1", user).futureValue mustEqual None
    }

    "deletes a card that does not exist" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.delete("1", user).failed.futureValue mustBe a[CardDoesNotExist]
    }

    "deletes a card from other user" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).futureValue
      refreshIdx()
      c.repo.delete("1", otherUser).failed.futureValue mustBe a[CardDoesNotExist]
    }

    "create three cards and find 2 with search term" taggedAs(FunctionalTestsTag) in testContext { c =>
      val input_1 = baseCardInput.copy(title="SomeLongWord")
      val input_2 = baseCardInput.copy(title="SomeLongWo")
      val input_3 = baseCardInput.copy(title="Nothing to do with the others")

      c.repo.create(input_1, user).futureValue
      c.repo.create(input_2, user).futureValue
      c.repo.create(input_3, user).futureValue
      refreshIdx()

      val listRequest = CardListRequest(1, 3, user.id, List(), List(), None, Some("SomeLongWord"))
      val response = c.repo.find(listRequest).futureValue

      val expected_1 = baseExpectedCardData.copy(title="SomeLongWord")
      val expected_2 = baseExpectedCardData.copy(id="2", title="SomeLongWo", ref=2)

      response.countOfItems mustEqual 2
      response.cards mustEqual Seq(expected_1, expected_2)
    }

    "get with pagination" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.repo.create(baseCardInput, user).futureValue
      c.repo.create(baseCardInput, user).futureValue
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
      val cardOne = CardFormInput("Title", Some("Body"), Some(List("Tag1", "Tag2")))
      val cardTwo = CardFormInput("Title", Some("Body"), Some(List("Tag2", "Tag3")))
      c.repo.create(cardOne, user).futureValue
      c.repo.create(cardTwo, user).futureValue
      val findRequest = CardListRequest(1, 10, user.id, List("Tag3"), List(), None)
      refreshIdx()

      val result = c.repo.find(findRequest).futureValue

      val cardTwoData = cardTwo.asCardData("2", Some(now), Some(now), 2)
      val expResult = FindResult(Seq(cardTwoData), 1)
      result mustEqual expResult
    }

    "Match by search term returning in order" in testContext { c =>
      val cardOne = CardFormInput("Title", Some("Body"), Some(List("Tag1", "Tag2")))
      val cardTwo = cardOne.copy(title="Titlee")
      val cardThree = cardTwo.copy(title="Tilleee")
      c.repo.create(cardOne, user).futureValue
      c.repo.create(cardTwo, user).futureValue
      c.repo.create(cardThree, user).futureValue
      refreshIdx()

      val findRequest = CardListRequest(1, 10, user.id, List(), List(), None, Some("Titleee"))
      val result = c.repo.find(findRequest).futureValue

      val cardOneData = cardOne.asCardData("1", Some(now), Some(now), 1)
      val cardTwoData = cardTwo.asCardData("2", Some(now), Some(now), 2)
      val cardThreeData = cardThree.asCardData("3", Some(now), Some(now), 3)
      val expResult = FindResult(Seq(cardThreeData, cardTwoData, cardOneData), 3)
      result mustEqual expResult
    }
  }

}
