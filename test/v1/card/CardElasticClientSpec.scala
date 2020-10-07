package v1.card.elasticclient

import v1.card.tagsrepository._

import org.scalatestplus.play.PlaySpec
import com.sksamuel.elastic4s.ElasticClient
import org.scalatestplus.mockito.MockitoSugar
import com.sksamuel.elastic4s.RequestFailure
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import com.sksamuel.elastic4s.RequestSuccess
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.requests.searches.SearchHit
import com.sksamuel.elastic4s.requests.searches.SearchHits
import v1.admin.testUtils.TestEsClient
import test.utils.TestUtils
import org.joda.time.DateTime
import test.utils.FunctionalTestsTag
import v1.auth.User
import test.utils.WaitUntil
import com.sksamuel.elastic4s.requests.searches.Total
import org.scalatest.time.Span
import org.scalatest.time.Millis

import scala.concurrent.ExecutionContext
import v1.card.CardListRequest
import v1.card.CardFormInput
import v1.card.CardDataRepository
import v1.card.CardRepository
import v1.card.TagsFilterMiniLangSyntaxError
import v1.card.historytracker.HistoricalEventCoreRepository
import v1.card.historytracker.CardUpdateDataRepository
import services.CounterUUIDGenerator
import v1.card.historytracker.CardHistoryTracker
import v1.card.CardCreationContext
import v1.card.CardRepositoryLike
import v1.card.TagsRepositoryLike
import v1.card.CardElasticClientLike
import v1.card.CardUpdateContext

class CardElasticIdFinderSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  implicit val ec = scala.concurrent.ExecutionContext.global

  def testContext(block: (ElasticClient, CardElasticIdFinder) => Any): Any = {
    val elasticClient = mock[ElasticClient]
    val idFinder = new CardElasticIdFinder(elasticClient, "someIndex")
    block(elasticClient, idFinder)
  }

  "onFailure" should {

    "return a failed exception" in testContext { (_, idFinder) =>
      val response = mock[RequestFailure]
      when(response.body).thenReturn(Some("Foo"))

      val result = idFinder.onFailure(response)

      whenReady(result.failed) { e =>
        e mustBe a [CardElasticClientException]
        e.asInstanceOf[CardElasticClientException].message mustEqual "Foo"
      }
    }

  }

  "onSuccess" should {

    def mockResponse() = {
      val hitOne = mock[SearchHit]
      when(hitOne.id).thenReturn("foo")

      val hitTwo = mock[SearchHit]
      when(hitTwo.id).thenReturn("bar")

      val total = mock[Total]
      when(total.value).thenReturn(12)

      val searchHits = mock[SearchHits]
      when(searchHits.hits).thenReturn(Array(hitOne, hitTwo))
      when(searchHits.total).thenReturn(total)

      val searchResponse = mock[SearchResponse]
      when(searchResponse.hits).thenReturn(searchHits)

      val response = mock[RequestSuccess[SearchResponse]]
      when(response.result).thenReturn(searchResponse)

      response
    }

    "return hits" in testContext { (_, idFinder) =>
      val result = idFinder.onSuccess(mockResponse()).futureValue
      result.ids mustEqual Seq("foo", "bar")
    }

    "count total ids" in testContext { (_, idFinder) =>
      val result = idFinder.onSuccess(mockResponse()).futureValue
      result.countOfItems mustEqual 12
    }

  }

}


class CardElasticClientFunctionalSpec
    extends PlaySpec
    with TestEsClient
    with ScalaFutures
    with WaitUntil
    with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  /**
    * Increases patience for future because we were having timeouts
    */
  override implicit def patienceConfig = new PatienceConfig(Span(1000, Millis))

  /**
    * The ES index
    */
  val index: String = "cards"

  /**
    * Common Fixtures
    */
  val user = User("UserId", "Email")
  val cardListRequest = CardListRequest(1, 100, user.id, List(), List(), None, None)

  val cardInput1 = CardFormInput("HELLO WORLD", Some(""), Some(List("T1", "T2")))
  val cardContext1 = CardCreationContext(user, DateTime.parse("2020-01-01T00:00:00Z"), "id1", 1)
  val cardData1 = cardInput1.asCardData(cardContext1)

  val cardInput2 = CardFormInput("FOO BYE", Some(""), Some(List("T1", "T3")))
  val cardContext2 = CardCreationContext(user, DateTime.parse("2020-01-02T00:00:00Z"), "id2", 2)
  val cardData2 = cardInput2.asCardData(cardContext2)

  val cardInput3 = CardFormInput("BAR", Some("FOO BAR"), Some(List()))
  val cardContext3 = CardCreationContext(user, DateTime.parse("2020-01-03T00:00:00Z"), "id3", 3)
  val cardData3 = cardInput3.asCardData(cardContext3)

  val cardInput4 = CardFormInput("BYE BYE DUDE", Some("I SAID BYE"), Some(List("T1", "T2", "T4")))
  val cardContext4 = CardCreationContext(user, DateTime.parse("2020-01-04T00:00:00Z"), "id4", 4)
  val cardData4 = cardInput4.asCardData(cardContext4)

  val allInputs = Seq(cardInput1, cardInput2, cardInput3, cardInput4)
  val allContexts = Seq(cardContext1, cardContext2, cardContext3, cardContext4)
  val allFixtures = allInputs.zip(allContexts)

  case class TestContext(
    val cardRepo: CardRepositoryLike,
    val tagsRepo: TagsRepositoryLike,
    val cardElasticClient: CardElasticClientLike,
    val user: User
  ) extends ScalaFutures {

    /**
      * Increases patience for future because we were having timeouts
      */
    override implicit def patienceConfig = new PatienceConfig(Span(3000, Millis))

    def saveCardsToDb(): Unit = allFixtures.foreach(x => saveCardToDb(x._1, x._2))
    def saveCardToDb(input: CardFormInput, context: CardCreationContext): String =
      cardRepo.create(input, context).futureValue

  }

  def testContext(block: TestContext => Any) = {
    TestUtils.testDB { db =>
      val cardHistoryCoreRepo = new HistoricalEventCoreRepository
      val cardHistoryUpdateRepo = new CardUpdateDataRepository(new CounterUUIDGenerator)
      val historyRecorder = new CardHistoryTracker(
        new CounterUUIDGenerator,
        cardHistoryCoreRepo,
        cardHistoryUpdateRepo
      )
      val dataRepo = new CardDataRepository
      val tagsRepo = new TagsRepository
      val cardElasticClient = new CardElasticClientImpl(client)
      val cardRepo = new CardRepository(
        dataRepo,
        tagsRepo,
        cardElasticClient,
        historyRecorder,
        db
      )
      val testContext = TestContext(
        cardRepo=cardRepo,
        tagsRepo=tagsRepo,
        cardElasticClient=cardElasticClient,
        user=user
      )
      try {
        block(testContext)
      } finally {
        cleanIndex()
      }
    }
  }

  "Functional Tests for ES card client" should {

    "Create and delete a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      import com.sksamuel.elastic4s.ElasticDsl._

      val queryByTitleAndBody = search(index).query(
        boolQuery().must(
          matchQuery("title", cardInput4.title),
          matchQuery("body", cardInput4.body.get)
        )
      )

      val id = c.saveCardToDb(cardInput4, cardContext4)
      refreshIdx()
      val cardData = c.cardRepo.get(id, c.user).futureValue.get

      val resultAfter = client.execute(queryByTitleAndBody).await.result
      resultAfter.hits.total.value mustEqual 1
      resultAfter.hits.hits.head.id mustEqual id
      resultAfter.hits.hits.head.sourceAsMap("userId") mustEqual user.id
      resultAfter.hits.hits.head.sourceAsMap("title") mustEqual cardData.title
      resultAfter.hits.hits.head.sourceAsMap("body") mustEqual cardData.body
      resultAfter.hits.hits.head.sourceAsMap("tags") mustEqual cardData.tags.map(_.toLowerCase())
      resultAfter.hits.hits.head.sourceAsMap("createdAt").asInstanceOf[String] mustEqual "2020-01-04T00:00:00.000Z"
      resultAfter.hits.hits.head.sourceAsMap("updatedAt").asInstanceOf[String] mustEqual "2020-01-04T00:00:00.000Z"

      //Need to mock the clock for deletion
      val datetime = DateTime.parse("2020-01-10T00:00:00Z")
      val updateContext = CardUpdateContext(user, datetime, cardData4)
      c.cardRepo.delete(cardData4, updateContext).await
      refreshIdx()
      client.execute(queryByTitleAndBody).await.result.hits.total.value == 0

      val resultAfterDelete = client.execute(queryByTitleAndBody).await.result
      resultAfterDelete.hits.total.value mustEqual 0
    }

    "Filter cards by userId" should {

      "find no match if user has no cards" taggedAs(FunctionalTestsTag) in testContext { c =>
        c.saveCardsToDb()
        refreshIdx()
        val result = c.cardElasticClient.findIds(cardListRequest.copy(userId="NOT_AN_USER_ID")).futureValue
        result.ids mustEqual List()
        result.countOfItems mustEqual 0
      }

      "find all ids if user has cards" taggedAs(FunctionalTestsTag) in testContext { c =>
        c.saveCardsToDb()
        refreshIdx()
        val result = c.cardElasticClient.findIds(cardListRequest).futureValue
        result.ids mustEqual allContexts.map(_.id).reverse;
        result.countOfItems mustEqual 4
      }

    }

  }

  "Do pagination" should {

    "Find first page with 2 cards" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val result = c.cardElasticClient.findIds(cardListRequest.copy(pageSize=2)).futureValue
      result.ids mustEqual List(cardContext4.id, cardContext3.id)
      result.countOfItems mustEqual 4
    }

    "Find second page with 2 cards" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val result = c.cardElasticClient.findIds(cardListRequest.copy(pageSize=2, page=2)).futureValue
      result.ids mustEqual List(cardContext2.id, cardContext1.id)
      result.countOfItems mustEqual 4
    }

    "Find last page with no cards" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val result = c.cardElasticClient.findIds(cardListRequest.copy(pageSize=2, page=3)).futureValue
      result.ids mustEqual List()
      result.countOfItems mustEqual 4
    }
  }

  "Filter cards by search term" should {

    "match more than one" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val result = c.cardElasticClient.findIds(cardListRequest.copy(searchTerm=Some("fOo"))).futureValue
      result.ids mustEqual List(cardContext2.id, cardContext3.id)
      result.countOfItems mustEqual 2
    }

    "match one by body" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val result = c.cardElasticClient.findIds(cardListRequest.copy(searchTerm=Some("I SAID"))).futureValue
      result.ids mustEqual List(cardContext4.id)
      result.countOfItems mustEqual 1
    }

    "match one by title" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val result = c.cardElasticClient.findIds(cardListRequest.copy(searchTerm=Some("HELLO"))).futureValue
      result.ids mustEqual List(cardContext1.id)
      result.countOfItems mustEqual 1
    }

    "match zero"  taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val result = c.cardElasticClient.findIds(cardListRequest.copy(searchTerm=Some("&*@^!"))).futureValue
      result.ids mustEqual List()
      result.countOfItems mustEqual 0
    }
  }

  "Filter cards by tag minilang query" should {

    "match more than one" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val query = Some("""((tags CONTAINS 'T1'))""")
      val result = c.cardElasticClient.findIds(cardListRequest.copy(query=query)).futureValue
      result.ids mustEqual List(cardContext4.id, cardContext2.id, cardContext1.id)
      result.countOfItems mustEqual 3
    }

    "match one" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val query = Some("""((tags CONTAINS 'T1') AND (tags CONTAINS 'T2') AND (tags CONTAINS 'T4'))""")
      val result = c.cardElasticClient.findIds(cardListRequest.copy(query=query)).futureValue
      result.ids mustEqual List(cardContext4.id)
      result.countOfItems mustEqual 1
    }

    "match with or" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val query = Some("""((tags CONTAINS 'T3') OR (tags CONTAINS 'T4'))""")
      val result = c.cardElasticClient.findIds(cardListRequest.copy(query=query)).futureValue
      result.ids mustEqual List(cardContext2.id, cardContext4.id)
      result.countOfItems mustEqual 2
    }

  }

  "Sorting" should {

    val date1 = DateTime.parse("2000-01-01T00:00:00")
    val date2 = DateTime.parse("2020-01-01T00:00:00")
    val searchTerm = cardInput1.title
    val request = cardListRequest.copy(searchTerm=Some(searchTerm))
    def runQuery(c: TestContext) = c.cardElasticClient.findIds(request)

    "if the score is the same, sort by createdAt" taggedAs(FunctionalTestsTag) in testContext { c =>
      val cardInput_1 = cardInput1
      val cardContext_1 = cardContext1.copy(id="1", now=date1, ref=1)

      val cardInput_2 = cardInput1
      val cardContext_2 = cardContext1.copy(id="2", now=date2, ref=2)

      c.saveCardToDb(cardInput_1, cardContext_1)
      c.saveCardToDb(cardInput_2, cardContext_2)

      refreshIdx()
      val result = runQuery(c).futureValue

      result.ids mustEqual List("2", "1")
    }

    "if the score is not the same, sort by score" taggedAs(FunctionalTestsTag) in testContext { c =>
      val cardInput_1 = cardInput1.copy(title=searchTerm)
      val cardContext_1 = cardContext1.copy(id="1", now=date1, ref=1)

      val cardInput_2 = cardInput1.copy(title=searchTerm.substring(1, searchTerm.length()))
      val cardContext_2 = cardContext_1.copy(id="2", now=date2, ref=2)

      c.saveCardToDb(cardInput_1, cardContext_1)
      c.saveCardToDb(cardInput_2, cardContext_2)

      refreshIdx()
      val result = runQuery(c).futureValue

      result.ids mustEqual List("1", "2")
    }

  }

  "All together" should {

    "tags, pagination and body match" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx()
      val query = Some(
        """((tags CONTAINS 't3') OR ((tags CONTAINS 'T1') AND (tags CONTAINS 'T2')))"""
      )

      val resultOne = c.cardElasticClient.findIds(cardListRequest.copy(
        page=1, pageSize=1, searchTerm=Some("bye"), query=query
      )).futureValue
      resultOne.ids mustEqual List("id2")
      resultOne.countOfItems mustEqual 2

      val resultTwo = c.cardElasticClient.findIds(cardListRequest.copy(
        page=2, pageSize=1, searchTerm=Some("bye"), query=query
      )).futureValue
      resultTwo.ids mustEqual List("id4")
      resultTwo.countOfItems mustEqual 2

      val resultThree = c.cardElasticClient.findIds(cardListRequest.copy(
        page=1, pageSize=2, searchTerm=Some("bye"), query=query
      )).futureValue
      resultThree.countOfItems mustEqual 2
      resultThree.ids mustEqual List("id2", "id4")
    }

  }

  "Error handling" should {

    "Returns failed future if minilang has parsing error" taggedAs(FunctionalTestsTag) in testContext { c =>

      val query = Some("(foo)")
      val request = cardListRequest.copy(query=query)
      val result = c.cardElasticClient.findIds(request)
      whenReady(result.failed) { e =>
        e mustBe a [TagsFilterMiniLangSyntaxError]
      }
    }
  }
}
