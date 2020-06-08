package v1.card

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
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import v1.admin.testUtils.TestEsClient
import org.scalatest.BeforeAndAfter
import test.utils.TestUtils
import play.api.db.Database
import org.joda.time.DateTime
import test.utils.FunctionalTestsTag
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import v1.auth.User
import test.utils.WaitUntil
import org.joda.time.format.ISODateTimeFormat
import org.scalatest.BeforeAndAfterAll
import com.sksamuel.elastic4s.requests.searches.Total


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
      result.countOfIds mustEqual 12
    }

  }

}


class CardElasticClientFunctionalSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with TestEsClient
    with BeforeAndAfter
    with ScalaFutures
    with WaitUntil
    with BeforeAndAfterAll {

  /**
    * Before all, cleanup the db and the es index.
    */
  override def beforeAll() = {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
    cleanIndex(index)
  }

  /**
    * After each, cleanup the db and the es index.
    */
  after {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
    cleanIndex(index)
  }

  /**
    * Overrides the default application to provide the ES client.
    */
  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .overrides(new TestEsFakeModule)
      .overrides(bind[CardElasticClient].to[CardElasticClientImpl])
      .build()

  /**
    * The ES index
    */
  val index: String = "cards"

  /**
    * Common Fixtures
    */
  val user = User("UserId", "Email")
  val cardData1 = CardData(None, "HELLO WORLD", "", List("T1", "T2"), None, None)
  val cardData2 = CardData(None, "FOO BYE", "", List("T1", "T3"), None, None )
  val cardData3 = CardData(None, "BAR", "FOO BAR", List(), None, None)
  val cardData4 = CardData(None, "BYE BYE DUDE", "I SAID BYE", List("T1", "T2", "T4"), None, None)
  val cardListRequest = CardListRequest(1, 100, user.id, List(), List(), None, None)
  def getNowAsISOString = ISODateTimeFormat.dateTime().print(DateTime.now())
  def cardRepository = app.injector.instanceOf[CardRepository]
  def cardEsClient = app.injector.instanceOf[CardElasticClient]

  /**
    * A helper function that creates 4 cards using the cards repository and returns their ids.
    */
  def createFourCards() = {
    import com.sksamuel.elastic4s.ElasticDsl._

    val id1 = cardRepository.create(cardData1, user).get
    val id2 = cardRepository.create(cardData2, user).get
    val id3 = cardRepository.create(cardData3, user).get
    val id4 = cardRepository.create(cardData4, user).get
    refreshIdx(index)
    waitUntil { () =>
      client.execute {
        search(index).matchAllQuery()
      }.futureValue.result.hits.total.value == 4
    }
    (id1, id2, id3, id4)
  }

  "Functional Tests for ES card client" should {

    "Create and delete a card" taggedAs(FunctionalTestsTag) in {

      import com.sksamuel.elastic4s.ElasticDsl._

      val queryByTitleAndBody = search(index).query(
        boolQuery().must(
          matchQuery("title", cardData3.title),
          matchQuery("body", cardData3.body)
        )
      )

      val resultBefore = client.execute(queryByTitleAndBody).await.result
      resultBefore.hits.total.value mustEqual 0

      val id = cardRepository.create(cardData3, user).get
      refreshIdx(index)
      waitUntil { () =>
        client.execute(queryByTitleAndBody).await.result.hits.total.value > 0
      }

      val resultAfter = client.execute(queryByTitleAndBody).await.result
      resultAfter.hits.total.value mustEqual 1
      resultAfter.hits.hits.head.id mustEqual id
      resultAfter.hits.hits.head.sourceAsMap("userId") mustEqual user.id
      resultAfter.hits.hits.head.sourceAsMap("title") mustEqual cardData3.title
      resultAfter.hits.hits.head.sourceAsMap("body") mustEqual cardData3.body
      resultAfter.hits.hits.head.sourceAsMap("tags") mustEqual cardData3.tags
      resultAfter.hits.hits.head.sourceAsMap("createdAt").asInstanceOf[String] must be < getNowAsISOString
      resultAfter.hits.hits.head.sourceAsMap("updatedAt").asInstanceOf[String] must be < getNowAsISOString

      cardRepository.delete(id, user).await
      refreshIdx(index)
      waitUntil { () =>
        client.execute(queryByTitleAndBody).await.result.hits.total.value == 0
      }

      val resultAfterDelete = client.execute(queryByTitleAndBody).await.result
      resultAfterDelete.hits.total.value mustEqual 0
    }

    "Filter cards by userId" should {

      "find no match if user has no cards" taggedAs(FunctionalTestsTag) in {
        val (_, _, _, _) = createFourCards
        val result = cardEsClient.findIds(cardListRequest.copy(userId="NOT_AN_USER_ID")).futureValue
        result.ids mustEqual List()
        result.countOfIds mustEqual 0
      }

      "find all ids if user has cards" taggedAs(FunctionalTestsTag) in {
        val (id1, id2, id3, id4) = createFourCards
        val result = cardEsClient.findIds(cardListRequest).futureValue
        result.ids mustEqual List(id1, id2, id3, id4)
        result.countOfIds mustEqual 4
      }

    }

  }

  "Do pagination" should {

    "Find first page with 2 cards" taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards
      val result = cardEsClient.findIds(cardListRequest.copy(pageSize=2)).futureValue
      result.ids mustEqual List(id1, id2)
      result.countOfIds mustEqual 4
    }

    "Find second page with 2 cards" taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards
      val result = cardEsClient.findIds(cardListRequest.copy(pageSize=2, page=2)).futureValue
      result.ids mustEqual List(id3, id4)
      result.countOfIds mustEqual 4
    }

    "Find last page with no cards" taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards
      val result = cardEsClient.findIds(cardListRequest.copy(pageSize=2, page=3)).futureValue
      result.ids mustEqual List()
      result.countOfIds mustEqual 4
    }
  }

  "Filter cards by search term" should {

    "match more than one" taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards()
      val result = cardEsClient.findIds(cardListRequest.copy(searchTerm=Some("fOo"))).futureValue
      result.ids mustEqual List(id2, id3)
      result.countOfIds mustEqual 2
    }

    "match one by body" taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards()
      val result = cardEsClient.findIds(cardListRequest.copy(searchTerm=Some("I SAID"))).futureValue
      result.ids mustEqual List(id4)
      result.countOfIds mustEqual 1
    }

    "match one by title" taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards()
      val result = cardEsClient.findIds(cardListRequest.copy(searchTerm=Some("HELLO"))).futureValue
      result.ids mustEqual List(id1)
      result.countOfIds mustEqual 1
    }

    "match zero"  taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards()
      val result = cardEsClient.findIds(cardListRequest.copy(searchTerm=Some("&*@^!"))).futureValue
      result.ids mustEqual List()
      result.countOfIds mustEqual 0
    }
  }

  "Filter cards by tag minilang query" should {

    "match more than one" taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards()
      val query = Some("""((tags CONTAINS 'T1'))""")
      val result = cardEsClient.findIds(cardListRequest.copy(query=query)).futureValue
      result.ids mustEqual List(id1, id2, id4)
      result.countOfIds mustEqual 3
    }

    "match one" taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards()
      val query = Some("""((tags CONTAINS 'T1') AND (tags CONTAINS 'T2') AND (tags CONTAINS 'T4'))""")
      val result = cardEsClient.findIds(cardListRequest.copy(query=query)).futureValue
      result.ids mustEqual List(id4)
      result.countOfIds mustEqual 1
    }

    "match with or" taggedAs(FunctionalTestsTag) in {
      val (id1, id2, id3, id4) = createFourCards()
      val query = Some("""((tags CONTAINS 'T3') OR (tags CONTAINS 'T4'))""")
      val result = cardEsClient.findIds(cardListRequest.copy(query=query)).futureValue
      result.ids mustEqual List(id2, id4)
      result.countOfIds mustEqual 2
    }

  }

  "All together" should {

    "tags, pagination and body match" in {
      val (id1, id2, id3, id4) = createFourCards()
      val query = Some(
        """((tags CONTAINS 't3') OR ((tags CONTAINS 'T1') AND (tags CONTAINS 'T2')))"""
      )

      val resultOne = cardEsClient.findIds(cardListRequest.copy(
        page=1, pageSize=1, searchTerm=Some("bye"), query=query
      )).futureValue
      resultOne.ids mustEqual List(id2)
      resultOne.countOfIds mustEqual 2

      val resultTwo = cardEsClient.findIds(cardListRequest.copy(
        page=2, pageSize=1, searchTerm=Some("bye"), query=query
      )).futureValue
      resultTwo.ids mustEqual List(id4)
      resultTwo.countOfIds mustEqual 2

      val resultThree = cardEsClient.findIds(cardListRequest.copy(
        page=1, pageSize=2, searchTerm=Some("bye"), query=query
      )).futureValue
      resultThree.countOfIds mustEqual 2
      resultThree.ids mustEqual List(id2, id4)
    }

  }
}
