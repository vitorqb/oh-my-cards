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

class CardRepositorySpec extends PlaySpec
    with MockitoSugar
    with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  "CardRepository create and get" should {

    "Allow user to create and get a card without tags nor body" in {
      test.utils.TestUtils.testDB { db =>
        val uuidGenerator = mock[UUIDGenerator]
        when(uuidGenerator.generate).thenReturn("id")

        val user = User("foo", "bar")
        val cardData = CardData(None, "Title", "", List())
        val repository = new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)

        repository.create(cardData, user).get mustEqual "id"
        repository.get("id", user) mustEqual Some(cardData.copy(id=Some("id")))
      }
    }

    "Allow user to create and get a card with tags and body" in {
      test.utils.TestUtils.testDB { db =>
        val uuidGenerator = mock[UUIDGenerator]
        when(uuidGenerator.generate).thenReturn("id")

        val user = User("foo", "bar")
        val cardData = CardData(None, "Title", "Body", List("Tag1", "TagTwo"))
        val repository = new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)

        repository.create(cardData, user).get mustEqual "id"
        repository.get("id", user) mustEqual Some(cardData.copy(id=Some("id")))
      }
    }

    "User creates three cards and get one" in {
      test.utils.TestUtils.testDB { db =>
        val uuidGenerator = mock[UUIDGenerator]
        val user = User("foo", "bar")
        val repository = new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)

        for (cardData <- Seq(CardData(Some("1"), "t", "b", List()),
                             CardData(Some("2"), "t", "b", List()),
                             CardData(Some("3"), "t", "b", List())))
        yield {
          when(uuidGenerator.generate).thenReturn(cardData.id.get)
          repository.create(cardData.copy(id=None), user).get mustEqual cardData.id.get
          repository.get(cardData.id.get, user) mustEqual Some(cardData)
        }
      }
    }

  }

  "CardRepository.find" should {

    val userId = "userid"
    val user = User(userId, "a@a.a")
    val cardData1 = CardData(Some("id1"), "ONE", "one", List("B"))
    val cardData2 = CardData(Some("id2"), "TWO", "two", List("ANOTHER_TAG", "A_TAG"))
    val cardData3 = CardData(Some("id3"), "THREE", "three", List("A", "C"))
    val cardListRequest = CardListRequest(1, 20, userId, List(), List(), None)

    /**
      * Saves all cards fixtures to the db using uuidGenerator and repository.
      */
    def saveCardsFixtures(uuidGenerator: UUIDGenerator, repository: CardRepository) = {
      for (cardData <- Array[CardData](cardData1, cardData2, cardData3)) yield {
        when(uuidGenerator.generate).thenReturn(cardData.id.value)
        repository.create(cardData.copy(id=None), user)
      }
    }

    def doTest(block: (Database, UUIDGenerator, CardRepository) => Any): Any = {
      test.utils.TestUtils.testDB { implicit db =>
        val uuidGenerator = mock[UUIDGenerator]
        val repository = new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)
        saveCardsFixtures(uuidGenerator, repository)
        block(db, uuidGenerator, repository)
      }
    }

    "find two out of tree cards for an user" in doTest { (db, uuidGenerator, repository) =>
      //Create for other user
      val otherUser = User("bar", "b@b.b@")
      val cardDataOtherUser1 = CardData(Some("id4"), "FOUR", "four", List())
      when(uuidGenerator.generate).thenReturn(cardDataOtherUser1.id.value)
      repository.create(cardDataOtherUser1.copy(id=None), otherUser)

      repository.find(cardListRequest.copy(pageSize=2)) mustEqual List(cardData3, cardData2)
    }

    "Filters wanted tags only(simple)" in doTest { (db, uuidGenerator, repository) =>
      repository.find(cardListRequest.copy(tags=List("A_TAG"))) mustEqual List(cardData2)
    }

    "Filters wanted tags only (complex)" in doTest { (db, uuidGenerator, repository) =>
      //Extra cards
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

    "Filters wanted tags (CASE INSENSITIVE)" in doTest { (db, uuidGenerator, repository) =>
      repository.find(cardListRequest.copy(tags=List("a_tag"))) mustEqual List(cardData2)
    }

    "Removes unwanted tags" in doTest { (db, uuidGenerator, repository) =>
      //Adds another card
      val cardData4 = CardData(Some("id4"), "FOUR", "four", List("A_TAG"))
      when(uuidGenerator.generate).thenReturn(cardData4.id.value)
      repository.create(cardData4.copy(id=None), user)

      (repository.find(cardListRequest.copy(tagsNot=List("b", "c")))
        mustEqual
        List(cardData4, cardData2))
    }

    "with tags query" should {

      "select all tags" in doTest { (db, uuidGenerator, repository) =>
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

      "select only a single tag" in doTest { (db, uuidGenerator, repository) =>
        val query = "((tags CONTAINS 'a') AND (tags CONTAINS 'c'))"
        val result = repository.find(cardListRequest.copy(query=Some(query)))
        result mustEqual List(cardData3)
      }

      "select no tags" in doTest { (db, uuidGenerator, repository) =>
        val query = """
          (((tags CONTAINS 'a') AND (tags CONTAINS 'b'))
           OR
           ((tags CONTAINS 'a') AND (tags CONTAINS 'd')))
        """
        val result = repository.find(cardListRequest.copy(query=Some(query)))
        result mustEqual List()
      }

      "complex crazy query" in doTest { (db, uuidGenerator, repository) =>
        val query = """
          ((((tags CONTAINS 'fooo') OR (tags CONTAINS 'baaar'))
            OR
            ((tags NOT CONTAINS 'a') AND (tags NOT CONTAINS 'c') AND (tags CONTAINS 'b')))
           AND
           ((tags NOT CONTAINS 'basket') OR (tags NOT CONTAINS 'football')))
        """
        val result = repository.find(cardListRequest.copy(query=Some(query)))
        result mustEqual List(cardData1)
      }

    }
  }

  "TagsRepository" should {

    "create and get tags for cards" in {
      test.utils.TestUtils.testDB { db =>
        db.withTransaction { implicit c =>
          val tagsRepo = new TagsRepository
          tagsRepo.get("id") mustEqual List()
          tagsRepo.create("id", List("A", "B")) mustEqual ()
          tagsRepo.get("id") mustEqual List("A", "B")
        }
      }
    }

    "create and delete tags for a card" in {
      test.utils.TestUtils.testDB { db =>
        db.withConnection { implicit c =>
          val tagsRepo = new TagsRepository
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
  }

  "CardRepository.countItemsMatching" should {

    val user = User("foo", "a@a.a")
    val cardData1 = CardData(Some("id1"), "ONE", "TWO", List("a", "b"))
    val cardData2 = CardData(Some("id2"), "one", "two", List("A", "B", "D"))
    val cardData3 = CardData(Some("id3"), "THREE", "three", List("C"))
    val cardListRequest = CardListRequest(0, 0, user.id, List(), List(), None)

    /**
      * Stores the cards data in the db.
      */
    def saveCardsFixtures(uuidGenerator: UUIDGenerator, repository: CardRepository) = {
      for (cardData <- Array[CardData](cardData1, cardData2, cardData3)) yield {
        when(uuidGenerator.generate).thenReturn(cardData.id.value)
        repository.create(cardData.copy(id=None), user)
      }
    }

    def doTest(block: (Database, UUIDGenerator, CardRepository) => Any): Any = {
      test.utils.TestUtils.testDB { implicit db =>
        val uuidGenerator = mock[UUIDGenerator]
        val repository = new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)
        saveCardsFixtures(uuidGenerator, repository)
        block(db, uuidGenerator, repository)
      }
    }
    
    "count the number of items for an user and not for other users." in doTest {
      (db, uuidGenerator, repository) =>

      //Creates for other user
      when(uuidGenerator.generate()).thenReturn("otherUserCardId")
      repository.create(CardData(None, "four", "four", List("b")), User("other", "other"))

      repository.countItemsMatching(cardListRequest.copy(tags=List("b"))) mustEqual 2
    }

    "filters by tag" in doTest { (db, uuidGenerator, repository) =>
      repository.countItemsMatching(cardListRequest) mustEqual 3
    }

    "removes by tag" in doTest { (db, uuidGenerator, repository) =>
      val req1 = cardListRequest.copy(tags=List("a", "b"), tagsNot=List("d"))
      repository.countItemsMatching(req1) mustEqual 1

      val req2 = cardListRequest.copy(tags=List("a", "b"), tagsNot=List("c"))
      repository.countItemsMatching(req2) mustEqual 2
    }

    "with tags query" should {

      "all tags" in doTest { (db, uuidGenerator, repository) =>
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

      "select only a single tag" in doTest { (db, uuidGenerator, repository) =>
        val query = "((tags CONTAINS 'a') AND (tags CONTAINS 'd'))"
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 1
      }

      "select only two tags" in doTest { (db, uuidGenerator, repository) =>
        val query = "((tags CONTAINS 'a') OR (tags CONTAINS 'd'))"
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 2
      }

      "select no tags" in doTest { (db, uuidGenerator, repository) =>
        val query = "((tags CONTAINS 'a') AND (tags NOT CONTAINS 'b'))"
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 0
      }      

      "select no tags (complex)" in doTest { (db, uuidGenerator, repository) =>
        val query = """
          (((tags CONTAINS 'a') AND (tags CONTAINS 'b'))
           AND
           ((tags CONTAINS 'c') OR (tags CONTAINS 'f')))
        """
        repository.countItemsMatching(cardListRequest.copy(query=Some(query))) mustEqual 0
      }

      "complex crazy query" in doTest { (db, uuidGenerator, repository) =>
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

    "deletes related tags" in {
      test.utils.TestUtils.testDB { db =>
        db.withConnection { implicit c =>
          val user = User("E", "F")
          val tagsRepo = new TagsRepository
          val repository = new CardRepositoryImpl(db, new UUIDGenerator, tagsRepo)
          val id = repository.create(CardData(None, "A", "B", List("C", "D")), user).get
          tagsRepo.get(id) mustEqual List("C", "D")
          repository.delete(id, user).futureValue
          tagsRepo.get(id) mustEqual List()
        }
      }
    }

    "fail if the card does not exist" in {
      test.utils.TestUtils.testDB { db =>
        val repository = new CardRepositoryImpl(db, mock[UUIDGenerator], new TagsRepository)
        repository.delete("FOO", mock[User]).futureValue mustEqual Failure(new CardDoesNotExist)
      }
    }

    "fail if the card does not exist for a specific user" in {
      test.utils.TestUtils.testDB { db =>
        val uuidGenerator = mock[UUIDGenerator]
        val repository =  new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)

        val user = User("foo", "a@a.a")
        val cardData = CardData(Some("id"), "foo", "bar", List())
        when(uuidGenerator.generate).thenReturn(cardData.id.value)
        repository.create(cardData.copy(id=None), user)

        val otherUser = User("bar", "b@b.b")

        repository.delete("id", otherUser).futureValue mustEqual Failure(new CardDoesNotExist)
      }
    }

    "find and delete card that exists" in {
      test.utils.TestUtils.testDB { db =>
        val uuidGenerator = mock[UUIDGenerator]
        val repository =  new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)

        val user = User("foo", "a@a.a")
        val cardData = CardData(Some("id"), "foo", "bar", List())
        when(uuidGenerator.generate).thenReturn(cardData.id.value)
        repository.create(cardData.copy(id=None), user)

        repository.delete("id", user).futureValue

        repository.get("id", user) mustEqual None
      }
    }

  }

  "CardRepository.update" should {

    "Update a card including the tags" in {
      test.utils.TestUtils.testDB { db =>
        val user = User("W", "W")
        val cardData = CardData(None, "A", "B", List("C"))
        val uuidGenerator = mock[UUIDGenerator]
        when(uuidGenerator.generate()).thenReturn("id")
        val repository = new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)
        repository.create(cardData, user).get

        val newCardData = cardData.copy(id=Some("id"), tags=List("D"))
        repository.update(newCardData, user).futureValue
        repository.get("id", user).get mustEqual newCardData
      }
    }

  }

  "CardRepository.getAllTags" should {

    val user = User("foo", "a@a.a")
    val cardData1 = CardData(Some("id1"), "ONE", "TWO", List("a", "b"))
    val cardData2 = CardData(Some("id2"), "one", "two", List("A", "B", "D"))
    val cardData3 = CardData(Some("id3"), "THREE", "three", List("C"))
    val cardListRequest = CardListRequest(0, 0, user.id, List(), List(), None)

    def saveCardsFixtures(uuidGenerator: UUIDGenerator, repository: CardRepository) = {
      for (cardData <- Array[CardData](cardData1, cardData2, cardData3)) yield {
        when(uuidGenerator.generate).thenReturn(cardData.id.value)
        repository.create(cardData.copy(id=None), user)
      }
    }

    def doTest(block: (Database, UUIDGenerator, CardRepository) => Any): Any = {
      test.utils.TestUtils.testDB { implicit db =>
        val uuidGenerator = mock[UUIDGenerator]
        val repository = new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)
        saveCardsFixtures(uuidGenerator, repository)
        block(db, uuidGenerator, repository)
      }
    }

    "Return all tags for an user" in doTest { (_, _, repository) =>
      repository.getAllTags(user).futureValue must contain allOf ("a", "A", "b", "B", "C", "D")
    }

    "Do not return tags for other users" in doTest { (_, _, repository) =>
      val otherUser = User("other", "other@user")
      repository.getAllTags(otherUser).futureValue mustEqual List()
    }

  }

}
