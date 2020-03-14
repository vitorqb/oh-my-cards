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


class CardResourceHandlerSpec extends PlaySpec with MockitoSugar {

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

}
