package v1.card

import v1.card.tagsrepository._

import scala.language.reflectiveCalls

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
import org.scalatest.time.Span
import org.scalatest.time.Millis

import v1.card.testUtils._
import services.{UUIDGenerator}
import scala.concurrent.ExecutionContext
import v1.card.CardRefGenerator.CardRefGenerator
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import v1.card.cardrepositorycomponents.CardRepositoryComponents

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
  def getNowAsISOString = ISODateTimeFormat.dateTime().print(DateTime.now)

  val cardFixtures = new CardFixtureRepository() {
    val f1 = CardFixture(
      "id1",
      CardFormInput("HELLO WORLD", Some(""), Some(List("T1", "T2"))),
      DateTime.parse("2020-01-01T00:00:00Z")
    )

    val f2 = CardFixture(
      "id2",
      CardFormInput("FOO BYE", Some(""), Some(List("T1", "T3"))),
      DateTime.parse("2020-01-02T00:00:00Z")
    )

    val f3 = CardFixture(
      "id3",
      CardFormInput("BAR", Some("FOO BAR"), Some(List())),
      DateTime.parse("2020-01-03T00:00:00Z")
    )

    val f4 = CardFixture(
      "id4",
      CardFormInput("BYE BYE DUDE", Some("I SAID BYE"), Some(List("T1", "T2", "T4"))),
      DateTime.parse("2020-01-04T00:00:00Z")
    )

    def allFixtures() = Seq(f1, f2, f3, f4)
  }

  def testContext(block: TestContext => Any) = {
    TestUtils.testDB { db =>
      val tagsRepo = new TagsRepository
      val cardElasticClient = new CardElasticClientImpl(client)
      val components = ComponentsBuilder().withDb(db).build()
      val testContext = TestContext(
        components=components,
        cardRepo=new CardRepository(new CardDataRepository(components, tagsRepo, cardElasticClient), tagsRepo, cardElasticClient, components),
        tagsRepo=tagsRepo,
        cardElasticClient=cardElasticClient,
        cardFixtures=cardFixtures,
        user=user
      )
      try {
        block(testContext)
      } finally {
        TestUtils.cleanupDb(db)
        cleanIndex(index)
      }
    }
  }

  "Functional Tests for ES card client" should {

    "Create and delete a card" taggedAs(FunctionalTestsTag) in testContext { c =>
      import com.sksamuel.elastic4s.ElasticDsl._

      val fixture = cardFixtures.f4

      val queryByTitleAndBody = search(index).query(
        boolQuery().must(
          matchQuery("title", fixture.formInput.title),
          matchQuery("body", fixture.formInput.body.get)
        )
      )

      val resultBefore = client.execute(queryByTitleAndBody).await.result
      resultBefore.hits.total.value mustEqual 0

      val id = c.createCardInDb(fixture)
      refreshIdx(index)
      waitUntil { () =>
        client.execute(queryByTitleAndBody).await.result.hits.total.value > 0
      }
      val cardData = c.cardRepo.get(id, c.user).get

      val resultAfter = client.execute(queryByTitleAndBody).await.result
      resultAfter.hits.total.value mustEqual 1
      resultAfter.hits.hits.head.id mustEqual id
      resultAfter.hits.hits.head.sourceAsMap("userId") mustEqual user.id
      resultAfter.hits.hits.head.sourceAsMap("title") mustEqual cardData.title
      resultAfter.hits.hits.head.sourceAsMap("body") mustEqual cardData.body
      resultAfter.hits.hits.head.sourceAsMap("tags") mustEqual cardData.tags.map(_.toLowerCase())
      resultAfter.hits.hits.head.sourceAsMap("createdAt").asInstanceOf[String] mustEqual "2020-01-04T00:00:00.000Z"
      resultAfter.hits.hits.head.sourceAsMap("updatedAt").asInstanceOf[String] mustEqual "2020-01-04T00:00:00.000Z"

      c.cardRepo.delete(id, user).await
      refreshIdx(index)
      waitUntil { () =>
        client.execute(queryByTitleAndBody).await.result.hits.total.value == 0
      }

      val resultAfterDelete = client.execute(queryByTitleAndBody).await.result
      resultAfterDelete.hits.total.value mustEqual 0
    }

    "Filter cards by userId" should {

      "find no match if user has no cards" taggedAs(FunctionalTestsTag) in testContext { c =>
        c.saveCardsToDb()
        refreshIdx(index)
        val result = c.cardElasticClient.findIds(cardListRequest.copy(userId="NOT_AN_USER_ID")).futureValue
        result.ids mustEqual List()
        result.countOfIds mustEqual 0
      }

      "find all ids if user has cards" taggedAs(FunctionalTestsTag) in testContext { c =>
        c.saveCardsToDb()
        refreshIdx(index)
        val result = c.cardElasticClient.findIds(cardListRequest).futureValue
        result.ids mustEqual cardFixtures.allFixtures().map(_.id).reverse;
        result.countOfIds mustEqual 4
      }

    }

  }

  "Do pagination" should {

    "Find first page with 2 cards" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val result = c.cardElasticClient.findIds(cardListRequest.copy(pageSize=2)).futureValue
      result.ids mustEqual List(cardFixtures.f4.id, cardFixtures.f3.id)
      result.countOfIds mustEqual 4
    }

    "Find second page with 2 cards" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val result = c.cardElasticClient.findIds(cardListRequest.copy(pageSize=2, page=2)).futureValue
      result.ids mustEqual List(cardFixtures.f2.id, cardFixtures.f1.id)
      result.countOfIds mustEqual 4
    }

    "Find last page with no cards" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val result = c.cardElasticClient.findIds(cardListRequest.copy(pageSize=2, page=3)).futureValue
      result.ids mustEqual List()
      result.countOfIds mustEqual 4
    }
  }

  "Filter cards by search term" should {

    "match more than one" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val result = c.cardElasticClient.findIds(cardListRequest.copy(searchTerm=Some("fOo"))).futureValue
      result.ids mustEqual List(cardFixtures.f2.id, cardFixtures.f3.id)
      result.countOfIds mustEqual 2
    }

    "match one by body" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val result = c.cardElasticClient.findIds(cardListRequest.copy(searchTerm=Some("I SAID"))).futureValue
      result.ids mustEqual List(cardFixtures.f4.id)
      result.countOfIds mustEqual 1
    }

    "match one by title" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val result = c.cardElasticClient.findIds(cardListRequest.copy(searchTerm=Some("HELLO"))).futureValue
      result.ids mustEqual List(cardFixtures.f1.id)
      result.countOfIds mustEqual 1
    }

    "match zero"  taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val result = c.cardElasticClient.findIds(cardListRequest.copy(searchTerm=Some("&*@^!"))).futureValue
      result.ids mustEqual List()
      result.countOfIds mustEqual 0
    }
  }

  "Filter cards by tag minilang query" should {

    "match more than one" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val query = Some("""((tags CONTAINS 'T1'))""")
      val result = c.cardElasticClient.findIds(cardListRequest.copy(query=query)).futureValue
      result.ids mustEqual List(cardFixtures.f4.id, cardFixtures.f2.id, cardFixtures.f1.id)
      result.countOfIds mustEqual 3
    }

    "match one" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val query = Some("""((tags CONTAINS 'T1') AND (tags CONTAINS 'T2') AND (tags CONTAINS 'T4'))""")
      val result = c.cardElasticClient.findIds(cardListRequest.copy(query=query)).futureValue
      result.ids mustEqual List(cardFixtures.f4.id)
      result.countOfIds mustEqual 1
    }

    "match with or" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val query = Some("""((tags CONTAINS 'T3') OR (tags CONTAINS 'T4'))""")
      val result = c.cardElasticClient.findIds(cardListRequest.copy(query=query)).futureValue
      result.ids mustEqual List(cardFixtures.f2.id, cardFixtures.f4.id)
      result.countOfIds mustEqual 2
    }

  }

  "Sorting" should {

    val date1 = DateTime.parse("2000-01-01T00:00:00")
    val date2 = DateTime.parse("2020-01-01T00:00:00")
    val searchTerm = cardFixtures.f1.formInput.title
    val request = cardListRequest.copy(searchTerm=Some(searchTerm))
    def runQuery(c: TestContext) = c.cardElasticClient.findIds(request)

    "if the score is the same, sort by createdAt" taggedAs(FunctionalTestsTag) in testContext { c =>
      val fixture1 = cardFixtures.f1.copy(id="1", datetime=date1)
      val fixture2 = fixture1.copy(id="2", datetime=date2)

      c.createCardInDb(fixture1)
      c.createCardInDb(fixture2)

      refreshIdx(index)
      val result = runQuery(c).futureValue

      result.ids mustEqual List("2", "1")
    }

    "if the score is not the same, sort by score" taggedAs(FunctionalTestsTag) in testContext { c =>
      val formInput1 = cardFixtures.f1.formInput.copy(title=searchTerm)
      val fixture1 = cardFixtures.f1.copy(id="1", formInput=formInput1, datetime=date1)
      val formInput2 = formInput1.copy(title=searchTerm.substring(1, searchTerm.length()))
      val fixture2 = fixture1.copy(id="2", formInput=formInput2, datetime=date2)

      c.createCardInDb(fixture1)
      c.createCardInDb(fixture2)

      refreshIdx(index)
      val result = runQuery(c).futureValue

      result.ids mustEqual List("1", "2")
    }

  }

  "All together" should {

    "tags, pagination and body match" taggedAs(FunctionalTestsTag) in testContext { c =>
      c.saveCardsToDb()
      refreshIdx(index)
      val query = Some(
        """((tags CONTAINS 't3') OR ((tags CONTAINS 'T1') AND (tags CONTAINS 'T2')))"""
      )

      val resultOne = c.cardElasticClient.findIds(cardListRequest.copy(
        page=1, pageSize=1, searchTerm=Some("bye"), query=query
      )).futureValue
      resultOne.ids mustEqual List("id2")
      resultOne.countOfIds mustEqual 2

      val resultTwo = c.cardElasticClient.findIds(cardListRequest.copy(
        page=2, pageSize=1, searchTerm=Some("bye"), query=query
      )).futureValue
      resultTwo.ids mustEqual List("id4")
      resultTwo.countOfIds mustEqual 2

      val resultThree = c.cardElasticClient.findIds(cardListRequest.copy(
        page=1, pageSize=2, searchTerm=Some("bye"), query=query
      )).futureValue
      resultThree.countOfIds mustEqual 2
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
