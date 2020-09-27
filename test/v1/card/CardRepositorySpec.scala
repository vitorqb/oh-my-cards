package v1.card

import scala.language.reflectiveCalls

import org.scalatest._
import org.scalatestplus.play._
import org.scalatestplus.mockito.MockitoSugar

import org.mockito.Mockito._

import scala.concurrent.ExecutionContext
import org.scalatest.concurrent.ScalaFutures
import v1.auth.User
import services.UUIDGenerator
import scala.util.Failure
import play.api.db.Database
import test.utils.TestUtils
import services.Clock
import org.joda.time.DateTime
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import scala.util.Success

import v1.card.testUtils._
import v1.card.CardRefGenerator.CardRefGenerator
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import v1.card.cardrepositorycomponents.CardRepositoryComponents

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

class CardRepositorySpec extends PlaySpec
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = ExecutionContext.global
  var db: Database = null

  override def beforeAll(): Unit = {
    println("STARTING DB")
    db = TestUtils.getDb()
  }

  override def afterAll(): Unit = {
    println("CLOSING DB")
    db.shutdown()
  }

  val cardFixtures = new CardFixtureRepository() {
    def f1 = CardFixture(
      "id1",
      CardFormInput("ONE", Some("TWO"), Some(List("a", "b"))),
      new DateTime(2000, 1, 1, 1, 1, 1)
    )

    def f2 = CardFixture(
      "id2",
      CardFormInput("one", Some("two"), Some(List("A", "B", "D"))),
      new DateTime(2000, 1, 1, 1, 1, 1)
    )

    def f3 = CardFixture(
      "id3",
      CardFormInput("THREE", Some("three"), Some(List("C"))),
      new DateTime(2000, 1, 1, 1, 1, 1)
    )

    def allFixtures = Seq(f1, f2, f3)
  }

  /**
    * Initializes and provides context for the tests.
    */
  def testContext(block: TestContext => Any): Any = {
    val tagsRepo = new TagsRepository
    val cardElasticClient = mock[CardElasticClient]
    val components = ComponentsBuilder(db).build()
    val repository = new CardRepository(components, tagsRepo, cardElasticClient)
    val testContext = TestContext(
      components,
      repository,
      tagsRepo,
      cardElasticClient,
      cardFixtures,
      new User("userId", "user@email.com")
    )
    try {
      block(testContext)
    } finally {
      TestUtils.cleanupDb(db)
    }
  }

  //
  //Tests
  //
  "CardRepository create and get" should {

    val datetime = new DateTime(2000, 1, 1, 2, 2, 2)
    val baseCardInput = CardFormInput("Title", Some("Body"), Some(List("Tag1", "TagTwo")))
    val baseExpectedCardData = baseCardInput.asCardData("id", Some(datetime), Some(datetime), 1)

    "Allow user to create and get a card without tags nor body" in testContext { c =>
      val cardInput = baseCardInput.copy(body=None, tags=None)
      c.createCardInDb(cardInput, "id", datetime) mustEqual "id"
      c.cardRepo.get("id", c.user).get mustEqual baseExpectedCardData.copy(tags=List(), body="")
    }

    "Allow user to create and get a card with tags and body" in testContext { c =>
      c.createCardInDb(baseCardInput, "id", datetime) mustEqual "id"
      c.cardRepo.get("id", c.user).get mustEqual baseExpectedCardData
    }

    "User gets three cards" in testContext { c =>
      c.saveCardsToDb()
      val f1 = cardFixtures.f1
      val f2 = cardFixtures.f2
      val f3 = cardFixtures.f3

      c.cardRepo.get(f1.id, c.user).get.title mustEqual f1.formInput.title
      c.cardRepo.get(f2.id, c.user).get.body mustEqual f2.formInput.body.get
      c.cardRepo.get(f3.id, c.user).get.tags mustEqual f3.formInput.tags.get
    }

    "elasticSearchClient is called for each card" in testContext { c =>
      c.createCardInDb(baseCardInput, "id4", datetime) mustEqual "id4"
      verify(c.cardElasticClient).create(baseCardInput, "id4", datetime, c.user)
    }

    "the createdAt is recorded properly" in testContext { c =>
      val datetime = DateTime.parse("2020-11-23T00:00:00")
      c.createCardInDb(baseCardInput, "id", datetime) mustEqual "id"
      c.cardRepo.get("id", c.user).get.createdAt mustEqual Some(datetime)
    }
  }

  "CardRepository.find" should {

    def ids(c: TestContext) = Seq(cardFixtures.f3.id, cardFixtures.f1.id)
    val count = 5
    def cardListRequest(c: TestContext): CardListRequest =
      CardListRequest(0, 10, c.user.id, List(), List(), None)

    def mockEsResult(c: TestContext) = {
      val esResult = CardElasticIdFinder.Result(ids(c), count)
      when(c.cardElasticClient.findIds(cardListRequest(c))).thenReturn(Future.successful(esResult))
    }

    "query db to bring matching cards from ES and returns in correct order" in testContext { c =>
      c.saveCardsToDb()
      mockEsResult(c)

      //Note: the order must be equal to the order in `ids`! That's part of the test.
      val result = c.cardRepo.find(cardListRequest(c)).futureValue
      val expectedCardData3 = cardFixtures.f3.formInput.asCardData(
        cardFixtures.f3.id,
        Some(cardFixtures.f3.datetime),
        Some(cardFixtures.f3.datetime),
        3
      )
      val expectedCardData1 = cardFixtures.f1.formInput.asCardData(
        cardFixtures.f1.id,
        Some(cardFixtures.f1.datetime),
        Some(cardFixtures.f1.datetime),
        1
      )
      val expectedResult = FindResult(Seq(expectedCardData3, expectedCardData1), count)
      result mustEqual expectedResult
    }

  }

  "TagsRepository" should {

    "create and get tags for cards" in testContext { c =>
      db.withTransaction { implicit t =>
        c.tagsRepo.get("id") mustEqual List()
        c.tagsRepo.create("id", List("A", "B")) mustEqual ()
        c.tagsRepo.get("id") mustEqual List("A", "B")
      }
    }

    "create and delete tags for a card" in testContext { c =>
      db.withConnection { implicit t =>
        c.tagsRepo.delete("id1")
        c.tagsRepo.delete("id2")

        c.tagsRepo.get("id1") mustEqual List()
        c.tagsRepo.get("id2") mustEqual List()

        c.tagsRepo.create("id1", List("Bar", "Foo"))
        c.tagsRepo.create("id2", List("Baz", "Buz"))

        c.tagsRepo.get("id1") mustEqual List("Bar", "Foo")
        c.tagsRepo.get("id2") mustEqual List("Baz", "Buz")

        c.tagsRepo.delete("id1")

        c.tagsRepo.get("id1") mustEqual List()
        c.tagsRepo.get("id2") mustEqual List("Baz", "Buz")
      }
    }
  }

  "CardRepository.delete" should {

    "deletes related tags" in testContext { c =>
        c.saveCardsToDb()
        val id = cardFixtures.f1.id

      db.withConnection { implicit con =>
        c.tagsRepo.get(id) mustEqual List("a", "b")
        c.cardRepo.delete(id, c.user).futureValue
        c.tagsRepo.get(id) mustEqual List()
      }
    }

    "fail if the card does not exist" in testContext { c =>
      c.cardRepo.delete("FOO", mock[User]).futureValue mustEqual Failure(new CardDoesNotExist)
    }

    "fail if the card does not exist for a specific user" in testContext { c =>
      c.saveCardsToDb()
      val id = cardFixtures.f1.id
      val otherUser = User("bar", "b@b.b")

      c.cardRepo.get(id, c.user).get.id mustEqual id
      c.cardRepo.delete(id, otherUser).futureValue mustEqual Failure(new CardDoesNotExist)
    }

    "find and delete card that exists" in testContext { c =>
      c.saveCardsToDb()
      val id = cardFixtures.f2.id

      c.cardRepo.delete(id, c.user).futureValue mustEqual Success(())
      c.cardRepo.get(id, c.user) mustEqual None
    }

    "calls elastic client to delete the entry" in testContext { c =>
      c.saveCardsToDb()
      Await.ready(c.cardRepo.delete(cardFixtures.f1.id, c.user), 5000 millis)
      verify(c.cardElasticClient).delete(cardFixtures.f1.id)
    }

  }

  "CardRepository.update" should {

    val datetime = new DateTime(2000, 1, 1, 1, 1, 1)
    val laterDatetime = datetime.plusDays(1)

    "Update a card including the tags" in testContext { c =>
      val fixture = cardFixtures.f1
      val id = c.createCardInDb(fixture)
      val cardData = c.cardRepo.get(id, c.user).get
      val newCardData = cardData.copy(
        tags=List("D"),
        createdAt=Some(datetime),
        updatedAt=Some(laterDatetime)
      )
      when(c.components.clock.now()).thenReturn(laterDatetime)

      c.cardRepo.update(newCardData, c.user).futureValue

      c.cardRepo.get(id, c.user).get mustEqual newCardData
    }

    "set's the updatedAt" in testContext { c =>
      val fixture = cardFixtures.f2
      c.createCardInDb(fixture)
      val cardData = c.cardRepo.get(fixture.id, c.user).get
      when(c.components.clock.now()).thenReturn(laterDatetime)

      Await.ready(c.cardRepo.update(cardData, c.user), 5000 millis)

      c.cardRepo.get(fixture.id, c.user).get.updatedAt.get mustEqual laterDatetime

    }

  }

  "CardRepository.getAllTags" should {

    "Return all tags for an user" in testContext { c =>
      c.saveCardsToDb()
      c.cardRepo.getAllTags(c.user).futureValue must contain allOf ("a", "A", "b", "B", "C", "D")
    }

    "Do not return tags for other users" in testContext { c =>
      val otherUser = User("other", "other@user")
      c.cardRepo.getAllTags(otherUser).futureValue mustEqual List()
    }

  }

}
