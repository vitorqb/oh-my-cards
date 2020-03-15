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
import scala.util.Failure
import scala.concurrent.Future
import services.UUIDGenerator

class CardResourceSpec extends PlaySpec {

  "asCardData" should {

    "convert to a card data" in {
      (CardResource("id", "link", "title", "body").asCardData mustEqual
        CardData(Some("id"), "title", "body"))
    }

  }

}

class CardResourceHandlerSpec
    extends PlaySpec
    with MockitoSugar
    with ScalaFutures
    with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  "CardResourceHandlerSpec.create" should {

    "Delegate to repository.create" in {
      val cardFormInput = CardFormInput("foo", "bar")
      val cardData = CardData(None, "foo", "bar")
      val createdCardDataId = "1"
      val createdCardData = cardData.copy(id=Some(createdCardDataId))
      val user = mock[User]

      val repository = mock[CardRepositoryImpl]
      when(repository.create(cardData, user)).thenReturn(Try{ createdCardDataId })
      when(repository.get(createdCardDataId, user)).thenReturn(Some(createdCardData))

      val handler = new CardResourceHandler(repository)

      (handler.create(cardFormInput, user) mustEqual
        Success(CardResource.fromCardData(createdCardData)))
    }

  }

  "CardResourceHandlerSpec.find" should {

    "Returns resources from repository data" in {
      val cardResource1 = CardResource("foo1", "", "baz1", "")
      val cardData1 = CardData(Some("foo1"), "baz1", "")

      val cardResource2 = CardResource("foo2", "", "baz2", "")
      val cardData2 = CardData(Some("foo2"), "baz2", "")

      val cardListReq = CardListRequest(1, 2, "userid")

      val repository = mock[CardRepositoryImpl]
      when(repository.find(cardListReq)).thenReturn(Array(cardData1, cardData2))
      when(repository.countItemsMatching(cardListReq)).thenReturn(10)

      val handler = new CardResourceHandler(repository)

      val user = mock[User]
      when(user.id).thenReturn("userid")

      val result = handler.find(cardListReq)

      result.items mustEqual Array(cardResource1, cardResource2)
      result.page mustEqual 1
      result.pageSize mustEqual 2
      result.countOfItems mustEqual 10
    }

  }

  "CardResourceHandlerSpec.update" should {

    "unitary tests" should {

      val id = "FOO"
      val input = CardFormInput("title2", "body2")
      val user = mock[User]
      val repository = mock[CardRepositoryImpl]
      val handler = new CardResourceHandler(repository)
      val cardData = CardData(Some(id), "title1", "body1")
      
      "Fails if card not found" in {
        when(repository.get(id, user)).thenReturn(None)
        handler.update(id, input, user).futureValue mustEqual Failure(new CardDoesNotExist)
      }

      "Fail if card can not be updated with inputs" in {
        //empty title -> error
        val input_ = input.copy(title="", body="")
        when(repository.get(id, user)).thenReturn(Some(cardData))
        handler.update(id, input_, user).futureValue mustEqual Failure(InvalidCardData.emptyTitle)
      }

      "Fail if card update fails" in {
        val e = new Exception
        when(repository.get(id, user)).thenReturn(Some(cardData))
        when(repository.update(any, eqTo(user))).thenReturn(Future(Failure(e)))
        handler.update(id, input, user).futureValue mustEqual Failure(e)
      }
    }

    "integration tests" should {

      "Created and update a card" in {
        test.utils.TestUtils.testDB { implicit db =>
          //Creates a card
          val user = User("userId", "userEmail")
          val input = CardFormInput("title", "body")
          val repository = new CardRepositoryImpl(db, new UUIDGenerator)
          val handler = new CardResourceHandler(repository)
          val created = handler.create(input, user).get

          //Updates it
          val updateInput = input.copy(title="title2", body="body2")
          val updated = handler.update(created.id, updateInput, user).futureValue.get

          updated mustEqual created.copy(title="title2", body="body2")
          updated mustEqual handler.get(created.id, user).get
        }
      }

    }

  }

}
