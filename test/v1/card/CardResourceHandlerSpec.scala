package v1.card

import v1.card.tagsrepository._

import org.scalatestplus.play._
import org.scalatestplus.mockito.MockitoSugar

import org.mockito.Mockito._
import org.mockito.{ArgumentMatchersSugar}

import scala.concurrent.ExecutionContext
import org.scalatest.concurrent.ScalaFutures
import v1.auth.User
import scala.concurrent.Future
import org.joda.time.DateTime
import com.mohiva.play.silhouette.api.util.{Clock => SilhouetteClock}
import services.UUIDGenerator
import services.referencecounter.ReferenceCounterLike
import v1.card.models._
import v1.card.repository.CardRepositoryLike
import v1.card.repository.CardElasticClientLike
import v1.card.repository.CardHistoryRecorderLike
import v1.card.repository.CardRepository
import v1.card.datarepository.CardDataRepository
import v1.card.exceptions._

class CardResourceSpec extends PlaySpec {

  val date1 = Some(new DateTime(2000, 12, 12, 12, 12, 12))
  val baseCardResource = CardResource(
    "id",
    "link",
    "title",
    "body",
    List("A"),
    date1,
    date1,
    ref = 1
  )
  val baseCardInput = CardFormInput(
    baseCardResource.title,
    Some(baseCardResource.body),
    Some(baseCardResource.tags)
  )

  "asCardData" should {

    "convert to a card data" in {
      baseCardResource.asCardData mustEqual CardData(
        "id",
        "title",
        "body",
        List("A"),
        ref = 1
      )
    }

  }

  "updateWith" should {

    "updates the title" in {
      (baseCardResource.updateWith(baseCardInput.copy(title = "otherTitle")).get
        mustEqual baseCardResource.copy(title = "otherTitle"))
    }

    "updates the body" in {
      (baseCardResource
        .updateWith(baseCardInput.copy(body = Some("otherTitle")))
        .get
        mustEqual baseCardResource.copy(body = "otherTitle"))
    }

    "updates the body to empty string" in {
      (baseCardResource.updateWith(baseCardInput.copy(body = None)).get
        mustEqual baseCardResource.copy(body = ""))
    }

    "updates the tags" in {
      (baseCardResource
        .updateWith(baseCardInput.copy(tags = Some(List("AAA", "BBB"))))
        .get
        mustEqual baseCardResource.copy(tags = List("AAA", "BBB")))
    }

    "updates the tags when tags is empty" in {
      (baseCardResource.updateWith(baseCardInput.copy(tags = None)).get
        mustEqual baseCardResource.copy(tags = List()))
    }

  }

}

class CardResourceHandlerSpec
    extends PlaySpec
    with MockitoSugar
    with ScalaFutures
    with ArgumentMatchersSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val date1 = Some(new DateTime(2000, 12, 12, 12, 12, 12))

  "CardResourceHandlerSpec.create" should {

    "Delegate to repository.create" in {
      val user = mock[User]
      val cardFormInput = CardFormInput("foo", Some("bar"), None)
      val cardCreateData = cardFormInput.toCreateData()
      val context = CardCreationContext(user, date1.get, "1", 1)
      val createdCardData = context.genCardData(cardCreateData)
      val repository = mock[CardRepositoryLike]
      when(repository.create(cardCreateData, context))
        .thenReturn(Future.successful("1"))
      when(repository.get("1", user))
        .thenReturn(Future.successful(Some(createdCardData)))
      val clock = mock[SilhouetteClock]
      when(clock.now).thenReturn(date1.get)
      val refCounter = mock[ReferenceCounterLike]
      when(refCounter.nextRef()).thenReturn(1)
      val uuidGenerator = mock[UUIDGenerator]
      when(uuidGenerator.generate()).thenReturn("1")
      val handler =
        new CardResourceHandler(repository, clock, refCounter, uuidGenerator)

      val resource = handler.create(cardFormInput, user).futureValue

      (resource mustEqual CardResource.fromCardData(createdCardData))
    }

  }

  "CardResourceHandlerSpec.find" should {

    "Returns resources from repository data" in {
      val cardResource1 =
        CardResource("foo1", "", "baz1", "", List(), date1, date1, ref = 1)
      val cardData1 =
        CardData("foo1", "baz1", "", List(), date1, date1, ref = 1)

      val cardResource2 =
        CardResource("foo2", "", "baz2", "", List("A"), date1, date1, ref = 2)
      val cardData2 =
        CardData("foo2", "baz2", "", List("A"), date1, date1, ref = 2)

      val findResult = FindResult(Seq(cardData1, cardData2), 10)

      val cardListReq = CardListRequest(1, 2, "userid", List(), List(), None)

      val repository = mock[CardRepositoryLike]
      when(repository.find(cardListReq.toCardListData()))
        .thenReturn(Future.successful(findResult))

      val clock = mock[SilhouetteClock]
      val refCounter = mock[ReferenceCounterLike]
      val uuidGenerator = mock[UUIDGenerator]
      val handler =
        new CardResourceHandler(repository, clock, refCounter, uuidGenerator)

      val user = mock[User]
      when(user.id).thenReturn("userid")

      val result = handler.find(cardListReq).futureValue

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
      val repository = mock[CardRepositoryLike]
      val clock = mock[SilhouetteClock]
      val refCounter = mock[ReferenceCounterLike]
      val uuidGenerator = mock[UUIDGenerator]
      val handler =
        new CardResourceHandler(repository, clock, refCounter, uuidGenerator)
      val cardData = CardData(id, "title1", "body1", List(), ref = 1)

      "Fails if card not found" in {
        when(repository.get(id, user)).thenReturn(Future.successful(None))
        handler
          .update(id, input, user)
          .failed
          .futureValue mustEqual new CardDoesNotExist
      }

      "Fail if card can not be updated with inputs" in {
        //empty title -> error
        val input_ = input.copy(title = "", body = Some(""))
        when(repository.get(id, user))
          .thenReturn(Future.successful(Some(cardData)))
        handler
          .update(id, input_, user)
          .failed
          .futureValue mustEqual InvalidCardData.emptyTitle
      }

      "Fail if card update fails" in {
        val e = new Exception
        when(repository.get(id, user))
          .thenReturn(Future.successful(Some(cardData)))
        when(repository.update(any, any)).thenReturn(Future.failed(e))
        handler.update(id, input, user).failed.futureValue mustEqual e
      }
    }

    "integration tests" should {

      "Created and update a card" in {
        test.utils.TestUtils.testDB { implicit db =>
          //Creates a card
          val uuidGenerator = new UUIDGenerator
          val dataRepo = new CardDataRepository
          val tagsRepo = new TagsRepository
          val elasticClient = mock[CardElasticClientLike]
          val clock = mock[SilhouetteClock]
          val user = User("userId", "userEmail")
          val refCounter = mock[ReferenceCounterLike]
          val input = CardFormInput("title", Some("body"), None)
          val historyTracker = mock[CardHistoryRecorderLike]
          val repository = new CardRepository(
            dataRepo,
            tagsRepo,
            elasticClient,
            historyTracker,
            db
          )
          val handler = new CardResourceHandler(
            repository,
            clock,
            refCounter,
            uuidGenerator
          )
          val created = handler.create(input, user).futureValue

          //Updates it
          val updateInput = input.copy(title = "title2", body = Some("body2"))
          val updated =
            handler.update(created.id, updateInput, user).futureValue

          updated mustEqual created.copy(title = "title2", body = "body2")
          updated mustEqual handler.get(created.id, user).futureValue.get
        }
      }

    }

  }

  "CardResourceHandler.getMetadata" should {

    "get the tags from the repository and transforms to a resource" in {
      val tags = List("FOO", "BAR")
      val user = mock[User]
      val clock = mock[SilhouetteClock]
      val refCounter = mock[ReferenceCounterLike]
      val uuidGenerator = mock[UUIDGenerator]
      val repository = mock[CardRepositoryLike]
      when(repository.getAllTags(user)).thenReturn(Future.successful(tags))
      val handler =
        new CardResourceHandler(repository, clock, refCounter, uuidGenerator)

      val exp = CardMetadataResource(tags)
      val res = handler.getMetadata(user).futureValue
      exp mustEqual res
    }

  }

}
