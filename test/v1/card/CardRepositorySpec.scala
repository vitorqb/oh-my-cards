package v1.card

import org.scalatest._
import org.scalatestplus.play._
import org.scalatestplus.mockito.MockitoSugar

import org.mockito.Mockito._
import org.mockito.{ ArgumentMatchersSugar }

import scala.concurrent.ExecutionContext
import org.scalatest.concurrent.ScalaFutures
import scala.util.Try
import scala.util.Success
import v1.auth.User
import services.UUIDGenerator
import scala.util.Failure
import play.api.db.Database
import test.utils.StringUtils
import play.api.db.Databases
import test.utils.TestUtils
import services.Clock
import org.joda.time.DateTime
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

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
  val cardListRequest = CardListRequest(0, 10, user.id, List(), List(), None)

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

      verify(cardElastic).create(cardData3.copy(id=None), "id4", date1)
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

    "find two out of tree cards for an user" in testContext { (db, uuidGenerator, repository, _, _, _) =>
      //Create for other user
      val otherUser = User("bar", "b@b.b@")
      val cardDataOtherUser1 = CardData(Some("id4"), "FOUR", "four", List())
      when(uuidGenerator.generate).thenReturn(cardDataOtherUser1.id.value)
      repository.create(cardDataOtherUser1.copy(id=None), otherUser)

      repository.find(cardListRequest.copy(pageSize=2)) mustEqual List(cardData3, cardData2)
    }

    "Filters wanted tags only(simple)" in testContext { (db, uuidGenerator, repository, _, _, _) =>
      repository.find(cardListRequest.copy(tags=List("C"))) mustEqual List(cardData3)
    }

    "Filters wanted tags only (complex)" in testContext { (db, uuidGenerator, repository, _, _, _) =>
      //Extra cards
      //!!!! TODO Move to def
      val extraCard1 = CardData(Some("extraId1"), "_", "-", List("A", "B", "C"))
      when(uuidGenerator.generate).thenReturn(extraCard1.id.value)
      repository.create(extraCard1.copy(id=None), user)

      val extraCard2 = CardData(Some("extraId2"), "_", "-", List("A"))
      when(uuidGenerator.generate).thenReturn(extraCard2.id.value)
      repository.create(extraCard2.copy(id=None), user)

      val extraCard3 = CardData(Some("extraId3"), "_", "-", List("B", "C"))
      when(uuidGenerator.generate).thenReturn(extraCard3.id.value)
      repository.create(extraCard3.copy(id=None), user)

      (repository.find(cardListRequest.copy(tags=List("c", "B")))
        mustEqual
        List(extraCard3, extraCard1))
    }

    "Filters wanted tags (CASE INSENSITIVE)" in testContext { (db, uuidGenerator, repository, _, _, _) =>
      repository.find(cardListRequest.copy(tags=List("d"))) mustEqual List(cardData2)
    }

    "Removes unwanted tags" in testContext { (db, uuidGenerator, repository, _, _, _) =>
      //Adds another card
      val cardData4 = CardData(Some("id4"), "FOUR", "four", List("A_TAG"))
      when(uuidGenerator.generate).thenReturn(cardData4.id.value)
      repository.create(cardData4.copy(id=None), user)

      (repository.find(cardListRequest.copy(tagsNot=List("b")))
        mustEqual
        List(cardData4, cardData3))
    }

    "with tags query" should {

      "select all tags" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = """
            ((tags CONTAINS 'b') 
             OR 
             (tags CONTAINS 'another_tag')
             OR
             (tags CONTAINS 'a_tag')
             OR
             (tags CONTAINS 'a')
             OR
             (tags CONTAINS 'c'))"""
        val result = repository.find(cardListRequest.copy(query=Some(query)))
        result mustEqual List(cardData3, cardData2, cardData1)
      }

      "select only a single tag" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = "((tags CONTAINS 'a') AND (tags CONTAINS 'd'))"
        val result = repository.find(cardListRequest.copy(query=Some(query)))
        result mustEqual List(cardData2)
      }

      "select no tags" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = """
          (((tags CONTAINS 'a') AND (tags CONTAINS 'C'))
           OR
           ((tags CONTAINS 'D') AND (tags CONTAINS 'e')))
        """
        val result = repository.find(cardListRequest.copy(query=Some(query)))
        result mustEqual List()
      }

      "complex crazy query" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = """
          ((((tags CONTAINS 'fooo') OR (tags CONTAINS 'baaar'))
            OR
            ((tags NOT CONTAINS 'c') AND (tags NOT CONTAINS 'd') AND (tags CONTAINS 'b')))
           AND
           ((tags NOT CONTAINS 'basket') OR (tags NOT CONTAINS 'football')))
        """
        val result = repository.find(cardListRequest.copy(query=Some(query)))
        result mustEqual List(cardData1)
      }

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

  "CardRepository.countItemsMatching" should {
    
    "count the number of items for an user and not for other users." in testContext {
      (db, uuidGenerator, repository, _, _, _) =>

      //Creates for other user
      when(uuidGenerator.generate()).thenReturn("otherUserCardId")
      repository.create(CardData(None, "four", "four", List("b")), User("other", "other"))

      repository.countItemsMatching(cardListRequest.copy(tags=List("b"))) mustEqual 2
    }

    "filters by tag" in testContext { (db, uuidGenerator, repository, _, _, _) =>
      repository.countItemsMatching(cardListRequest) mustEqual 3
    }

    "removes by tag" in testContext { (db, uuidGenerator, repository, _, _, _) =>
      val req1 = cardListRequest.copy(tags=List("a", "b"), tagsNot=List("d"))
      repository.countItemsMatching(req1) mustEqual 1

      val req2 = cardListRequest.copy(tags=List("a", "b"), tagsNot=List("c"))
      repository.countItemsMatching(req2) mustEqual 2
    }

    "with tags query" should {

      "all tags" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = """
            ((tags CONTAINS 'b') 
             OR 
             (tags CONTAINS 'another_tag')
             OR
             (tags CONTAINS 'a_tag')
             OR
             (tags CONTAINS 'a')
             OR
             (tags CONTAINS 'c'))
        """
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 3
      }

      "select only a single tag" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = "((tags CONTAINS 'a') AND (tags CONTAINS 'd'))"
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 1
      }

      "select only two tags" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = "((tags CONTAINS 'a') OR (tags CONTAINS 'd'))"
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 2
      }

      "select no tags" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = "((tags CONTAINS 'a') AND (tags NOT CONTAINS 'b'))"
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 0
      }      

      "select no tags (complex)" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = """
          (((tags CONTAINS 'a') AND (tags CONTAINS 'b'))
           AND
           ((tags CONTAINS 'c') OR (tags CONTAINS 'f')))
        """
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 0
      }

      "complex crazy query" in testContext { (db, uuidGenerator, repository, _, _, _) =>
        val query = """
          ((((tags CONTAINS 'fooo') OR (tags CONTAINS 'baaar'))
            OR
            ((tags CONTAINS 'a') AND (tags NOT CONTAINS 'c')))
           AND
           ((tags NOT CONTAINS 'basket') OR (tags NOT CONTAINS 'football')))
        """
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 2
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
