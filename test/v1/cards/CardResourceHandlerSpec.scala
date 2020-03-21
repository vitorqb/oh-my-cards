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

  val baseCardResource = CardResource("id", "link", "title", "body", List("A"))
  val baseCardInput = CardFormInput(
    baseCardResource.title,
    Some(baseCardResource.body),
    Some(baseCardResource.tags)
  )

  "asCardData" should {

    "convert to a card data" in {
      baseCardResource.asCardData mustEqual CardData(Some("id"), "title", "body", List("A"))
    }

  }

  "updateWith" should {

    "updates the title" in {
      (baseCardResource.updateWith(baseCardInput.copy(title="otherTitle")).get
        mustEqual baseCardResource.copy(title="otherTitle"))
    }

    "updates the body" in {
      (baseCardResource.updateWith(baseCardInput.copy(body=Some("otherTitle"))).get
        mustEqual baseCardResource.copy(body="otherTitle"))
    }

    "updates the body to empty string" in {
      (baseCardResource.updateWith(baseCardInput.copy(body=None)).get
        mustEqual baseCardResource.copy(body=""))
    }

    "updates the tags" in {
      (baseCardResource.updateWith(baseCardInput.copy(tags=Some(List("AAA", "BBB")))).get
        mustEqual baseCardResource.copy(tags=List("AAA", "BBB")))
    }

    "updates the tags when tags is empty" in {
      (baseCardResource.updateWith(baseCardInput.copy(tags=None)).get
        mustEqual baseCardResource.copy(tags=List()))
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
      val cardFormInput = CardFormInput("foo", Some("bar"), None)
      val cardData = CardData(None, "foo", "bar", List())
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
      val cardResource1 = CardResource("foo1", "", "baz1", "", List())
      val cardData1 = CardData(Some("foo1"), "baz1", "", List())

      val cardResource2 = CardResource("foo2", "", "baz2", "", List("A"))
      val cardData2 = CardData(Some("foo2"), "baz2", "", List("A"))

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
      val input = CardFormInput("title2", Some("body2"), None)
      val user = mock[User]
      val repository = mock[CardRepositoryImpl]
      val handler = new CardResourceHandler(repository)
      val cardData = CardData(Some(id), "title1", "body1", List())
      
      "Fails if card not found" in {
        when(repository.get(id, user)).thenReturn(None)
        handler.update(id, input, user).futureValue mustEqual Failure(new CardDoesNotExist)
      }

      "Fail if card can not be updated with inputs" in {
        //empty title -> error
        val input_ = input.copy(title="", body=Some(""))
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
          val input = CardFormInput("title", Some("body"), None)
          val repository = new CardRepositoryImpl(db, new UUIDGenerator, new TagsRepository)
          val handler = new CardResourceHandler(repository)
          val created = handler.create(input, user).get

          //Updates it
          val updateInput = input.copy(title="title2", body=Some("body2"))
          val updated = handler.update(created.id, updateInput, user).futureValue.get

          updated mustEqual created.copy(title="title2", body="body2")
          updated mustEqual handler.get(created.id, user).get
        }
      }

    }

  }

}
