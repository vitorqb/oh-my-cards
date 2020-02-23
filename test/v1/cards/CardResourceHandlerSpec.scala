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

}
