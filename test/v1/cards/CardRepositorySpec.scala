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


class CardRepositorySpec extends PlaySpec with MockitoSugar with ScalaFutures {

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

  }

  "CardRepository.find" should {

    "find two out of tree cards for an user" in {
      test.utils.TestUtils.testDB { db =>
        val userId = "userid"

        val uuidGenerator = mock[UUIDGenerator]
        val repository = new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)

        val user = User(userId, "a@a.a")
        val cardData1 = CardData(Some("id1"), "ONE", "one", List())
        val cardData2 = CardData(Some("id2"), "TWO", "two", List("ANOTHER_TAG", "A_TAG"))
        val cardData3 = CardData(Some("id3"), "THREE", "three", List())
        for (cardData <- Array[CardData](cardData1, cardData2, cardData3)) yield {
          when(uuidGenerator.generate).thenReturn(cardData.id.value)
          repository.create(cardData.copy(id=None), user)
        }

        //Create for other user
        val otherUser = User("bar", "b@b.b@")
        val cardDataOtherUser1 = CardData(Some("id4"), "FOUR", "four", List())
        when(uuidGenerator.generate).thenReturn(cardDataOtherUser1.id.value)
        repository.create(cardDataOtherUser1.copy(id=None), otherUser)

        repository.find(CardListRequest(1, 2, userId)) mustEqual Array(cardData3, cardData2)
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

    "count the number of items for an user" in {
      test.utils.TestUtils.testDB { db =>
        val uuidGenerator = mock[UUIDGenerator]
        val repository =  new CardRepositoryImpl(db, uuidGenerator, new TagsRepository)

        //Create for user
        val user = User("foo", "a@a.a")
        val cardData1 = CardData(Some("id1"), "ONE", "one", List())
        val cardData2 = CardData(Some("id2"), "TWO", "two", List())
        val cardData3 = CardData(Some("id3"), "THREE", "three", List())
        for (cardData <- Array[CardData](cardData1, cardData2, cardData3)) yield {
          when(uuidGenerator.generate).thenReturn(cardData.id.value)
          repository.create(cardData.copy(id=None), user)
        }

        //Create for other user
        val otherUser = User("bar", "b@b.b@")
        val cardDataOtherUser1 = CardData(Some("id4"), "FOUR", "four", List())
        when(uuidGenerator.generate).thenReturn(cardDataOtherUser1.id.value)
        repository.create(cardDataOtherUser1.copy(id=None), otherUser)

        //User three has no cards
        val userThree = User("baz", "c@c.c@")

        repository.countItemsMatching(CardListRequest(0, 0, user.id)) mustEqual 3
        repository.countItemsMatching(CardListRequest(0, 0, otherUser.id)) mustEqual 1
        repository.countItemsMatching(CardListRequest(0, 0, userThree.id)) mustEqual 0
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

}
