package v1.card

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

  //
  //Fixtures
  //
  val date1 = new DateTime(2000, 1, 1, 1, 1, 1)
  val userId = "foo"
  val user = User(userId, "a@a.a")
  val cardData1 = CardData(Some("id1"), "ONE", "TWO", List("a", "b"))
  val cardData2 = CardData(Some("id2"), "one", "two", List("A", "B", "D"))
  val cardData3 = CardData(Some("id3"), "THREE", "three", List("C"))
  val cardListRequest: CardListRequest = CardListRequest(0, 10, user.id, List(), List(), None)

  /**
    * Stores the cards fixtures in the db...
    */
  def saveCardsFixtures(uuidGenerator: UUIDGenerator, repository: CardRepository) = {
    for (cardData <- Array[CardData](cardData1, cardData2, cardData3)) yield {
      when(uuidGenerator.generate).thenReturn(cardData.id.value)
      repository.create(cardData.copy(id=None), user)
    }
  }

  /**
    * Initializes and provides context for the tests.
    */
  def testContext(
    block: (
      Database,
      UUIDGenerator,
      CardRepository,
      TagsRepository,
      CardElasticClient,
      Clock
    ) => Any
  ): Any = {
    val uuidGenerator = mock[UUIDGenerator]
    val tagsRepo = new TagsRepository
    val cardElasticClient = mock[CardElasticClient]
    val clock = mock[Clock]
    val repository = new CardRepository(db, uuidGenerator, tagsRepo, cardElasticClient, clock)
    saveCardsFixtures(uuidGenerator, repository)
    try {
      block(db, uuidGenerator, repository, tagsRepo, cardElasticClient, clock)
    } finally {
      TestUtils.cleanupDb(db)
    }
  }

  //
  //Tests
  //
  "CardRepository create and get" should {

    "Allow user to create and get a card without tags nor body" in testContext {
      (db, uuidGenerator, repository, _, _, _) =>

      val cardData = CardData(None, "Title", "", List())
      when(uuidGenerator.generate).thenReturn("id")

      repository.create(cardData, user).get mustEqual "id"
      repository.get("id", user) mustEqual Some(cardData.copy(id=Some("id")))
    }

    "Allow user to create and get a card with tags and body" in testContext {
      (db, uuidGenerator, repository, _, _, _) =>

      val cardData = CardData(None, "Title", "Body", List("Tag1", "TagTwo"))
      when(uuidGenerator.generate).thenReturn("id")

      repository.create(cardData, user).get mustEqual "id"
      repository.get("id", user) mustEqual Some(cardData.copy(id=Some("id")))
    }

    "User creates three cards and get one" in testContext {
      (db, uuidGenerator, repository, _, _, _) =>

      for (cardData <- Seq(CardData(Some("1"), "t", "b", List()),
        CardData(Some("2"), "t", "b", List()),
        CardData(Some("3"), "t", "b", List())))
      yield {
        when(uuidGenerator.generate).thenReturn(cardData.id.get)
        repository.create(cardData.copy(id=None), user).get mustEqual cardData.id.get
        repository.get(cardData.id.get, user) mustEqual Some(cardData)
      }
    }

    "elasticSearchClient is called for each card" in testContext {
      (_, uuidGenerator, repository, _, cardElastic, clock) =>

      when(clock.now()).thenReturn(date1)
      when(uuidGenerator.generate()).thenReturn("id4")

      repository.create(cardData3.copy(id=None), user)

      verify(cardElastic).create(cardData3.copy(id=None), "id4", date1, user)
    }

    "the createdAt is recorded properly" in testContext {
      (_, uuidGenerator, repository, _, _, clock) =>

      val id = "some-id"
      when(uuidGenerator.generate()).thenReturn(id)
      val data = cardData1.copy(id=None)
      when(clock.now).thenReturn(date1)

      repository.create(data, user)

      val expected = data.copy(id=Some(id), updatedAt=Some(date1), createdAt=Some(date1))
      val result = repository.get(id, user).get
      result mustEqual expected
    }
  }

  "CardRepository.find" should {

    val ids = Seq(cardData1.id.get, cardData3.id.get)
    val count = 5

    def mockEsResult(cardElasticClient: CardElasticClient) = {
      val esResult = CardElasticIdFinder.Result(ids, count)
      when(cardElasticClient.findIds(cardListRequest)).thenReturn(Future.successful(esResult))
    }

    "query db to bring matching cards from ES" in testContext {
      (_, _, repository, _, cardElasticClient, _) =>
      mockEsResult(cardElasticClient)

      repository.find(cardListRequest).futureValue.cards mustEqual Seq(cardData1, cardData3)
    }

    "mirror the count from the es results" in testContext {
      (_, _, repository, _, cardElasticClient, _) =>
      mockEsResult(cardElasticClient)

      repository.find(cardListRequest).futureValue.countOfItems mustEqual count
    }

  }

  "TagsRepository" should {

    "create and get tags for cards" in testContext { (_, _, _, tagsRepo, _, _) =>
      db.withTransaction { implicit c =>
        tagsRepo.get("id") mustEqual List()
        tagsRepo.create("id", List("A", "B")) mustEqual ()
        tagsRepo.get("id") mustEqual List("A", "B")
      }
    }

    "create and delete tags for a card" in testContext { (_, _, _, tagsRepo, _, _) =>
      db.withConnection { implicit c =>
        tagsRepo.delete("id1")
        tagsRepo.delete("id2")

        tagsRepo.get("id1") mustEqual List()
        tagsRepo.get("id2") mustEqual List()

        tagsRepo.create("id1", List("Bar", "Foo"))
        tagsRepo.create("id2", List("Baz", "Buz"))

        tagsRepo.get("id1") mustEqual List("Bar", "Foo")
        tagsRepo.get("id2") mustEqual List("Baz", "Buz")

        tagsRepo.delete("id1")

        tagsRepo.get("id1") mustEqual List()
        tagsRepo.get("id2") mustEqual List("Baz", "Buz")
      }
    }
  }

  "CardRepository.delete" should {

    "deletes related tags" in testContext { (db, uuidGenerator, repository, tagsRepo, _, _) =>
      db.withConnection { implicit c =>
        val id = "SDAJKSJDNA"
        when(uuidGenerator.generate()).thenReturn(id)
        repository.create(CardData(None, "A", "B", List("C", "D")), user)

        tagsRepo.get(id) mustEqual List("C", "D")
        repository.delete(id, user).futureValue
        tagsRepo.get(id) mustEqual List()
      }
    }

    "fail if the card does not exist" in testContext { (db, uuidGenerator, repository, tagsRepo, _, _) =>
      repository.delete("FOO", mock[User]).futureValue mustEqual Failure(new CardDoesNotExist)
    }

    "fail if the card does not exist for a specific user" in testContext {
      (db, uuidGenerator, repository, tagsRepo, _, _) =>

      val cardData = CardData(Some("id"), "foo", "bar", List())
      when(uuidGenerator.generate).thenReturn(cardData.id.value)
      repository.create(cardData.copy(id=None), user)

      val otherUser = User("bar", "b@b.b")

      repository.delete("id", otherUser).futureValue mustEqual Failure(new CardDoesNotExist)
    }

    "find and delete card that exists" in testContext {
      (db, uuidGenerator, repository, tagsRepo, _, _) =>

      val cardData = CardData(Some("id"), "foo", "bar", List())
      when(uuidGenerator.generate).thenReturn(cardData.id.value)
      repository.create(cardData.copy(id=None), user)

      repository.delete("id", user).futureValue

      repository.get("id", user) mustEqual None
    }

    "calls elastic client to delete the entry" in testContext {
      (_, _, repository, _, elasticClient, _) =>
      Await.ready(repository.delete(cardData1.id.get, user), 5000 millis)
      verify(elasticClient).delete(cardData1.id.get)
    }

  }

  "CardRepository.update" should {

    "Update a card including the tags" in testContext {
      (db, uuidGenerator, repository, tagsRepo, _, _) =>

      val cardData = CardData(None, "A", "B", List("C"))
      when(uuidGenerator.generate()).thenReturn("id")
      repository.create(cardData, user).get

      val newCardData = cardData.copy(id=Some("id"), tags=List("D"))
      repository.update(newCardData, user).futureValue
      repository.get("id", user).get mustEqual newCardData
    }

    "set's the updatedAt" in testContext {
      (_, _, repository, _, _, clock) =>

      when(clock.now()).thenReturn(date1)

      Await.ready(repository.update(cardData1, user), 5000 millis)

      repository.get(cardData1.id.get, user).get.updatedAt.get mustEqual date1

    }

  }

  "CardRepository.getAllTags" should {

    "Return all tags for an user" in testContext { (_, _, repository, _, _, _) =>
      repository.getAllTags(user).futureValue must contain allOf ("a", "A", "b", "B", "C", "D")
    }

    "Do not return tags for other users" in testContext { (_, _, repository, _, _, _) =>
      val otherUser = User("other", "other@user")
      repository.getAllTags(otherUser).futureValue mustEqual List()
    }

  }

}
