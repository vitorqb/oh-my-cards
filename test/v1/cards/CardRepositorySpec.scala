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


class CardRepositorySpec extends PlaySpec with MockitoSugar {

  "CardRepository create and get" should {

    "Allow user to create and get a card" in {
      test.utils.TestUtils.testDB { db =>
        val id = "FOO"

        val uuidGenerator = mock[UUIDGenerator]
        when(uuidGenerator.generate).thenReturn(id)

        val user = User("foo", "bar")
        val cardData = CardData(None, "Title", "Body")
        val repository = new CardRepositoryImpl(db, uuidGenerator)

        val createdId = repository.create(cardData, user).get
        createdId mustEqual id
        
        val expectedCreatedCardData = cardData.copy(id=Some(createdId))

        repository.get(createdId, user) mustEqual Some(expectedCreatedCardData)
      }
    }

  }

  "CardRepository.find" should {

    "find two out of tree cards for an user" in {
      test.utils.TestUtils.testDB { db =>
        val userId = "userid"

        val uuidGenerator = mock[UUIDGenerator]
        val repository = new CardRepositoryImpl(db, uuidGenerator)

        val user = User(userId, "a@a.a")
        val cardData1 = CardData(Some("id1"), "ONE", "one")
        val cardData2 = CardData(Some("id2"), "TWO", "two")
        val cardData3 = CardData(Some("id3"), "THREE", "three")
        for (cardData <- Array[CardData](cardData1, cardData2, cardData3)) yield {
          when(uuidGenerator.generate).thenReturn(cardData.id.value)
          repository.create(cardData.copy(id=None), user)
        }

        //Create for other user
        val otherUser = User("bar", "b@b.b@")
        val cardDataOtherUser1 = CardData(Some("id4"), "FOUR", "four")
        when(uuidGenerator.generate).thenReturn(cardDataOtherUser1.id.value)
        repository.create(cardDataOtherUser1.copy(id=None), otherUser)

        repository.find(CardListRequest(1, 2, userId)) mustEqual Array(cardData3, cardData2)
      }
    }

  }

  "CardRepository.countItemsMatching" should {

    "count the number of items for an user" in {
      test.utils.TestUtils.testDB { db =>
        val uuidGenerator = mock[UUIDGenerator]
        val repository =  new CardRepositoryImpl(db, uuidGenerator)

        //Create for user
        val user = User("foo", "a@a.a")
        val cardData1 = CardData(Some("id1"), "ONE", "one")
        val cardData2 = CardData(Some("id2"), "TWO", "two")
        val cardData3 = CardData(Some("id3"), "THREE", "three")
        for (cardData <- Array[CardData](cardData1, cardData2, cardData3)) yield {
          when(uuidGenerator.generate).thenReturn(cardData.id.value)
          repository.create(cardData.copy(id=None), user)
        }

        //Create for other user
        val otherUser = User("bar", "b@b.b@")
        val cardDataOtherUser1 = CardData(Some("id4"), "FOUR", "four")
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

}
