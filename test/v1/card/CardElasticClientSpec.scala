package v1.card

import org.scalatestplus.play.PlaySpec
import com.sksamuel.elastic4s.ElasticClient
import org.scalatestplus.mockito.MockitoSugar
import com.sksamuel.elastic4s.RequestFailure
import org.mockito.Mockito._
import scala.concurrent.Await
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

    "return hits" in testContext { (_, idFinder) =>
      val hitOne = mock[SearchHit]
      when(hitOne.id).thenReturn("foo")

      val hitTwo = mock[SearchHit]
      when(hitTwo.id).thenReturn("bar")

      val searchHits = mock[SearchHits]
      when(searchHits.hits).thenReturn(Array(hitOne, hitTwo))

      val searchResponse = mock[SearchResponse]
      when(searchResponse.hits).thenReturn(searchHits)

      val response = mock[RequestSuccess[SearchResponse]]
      when(response.result).thenReturn(searchResponse)

      val result = idFinder.onSuccess(response).futureValue
      result mustEqual Seq("foo", "bar")
    }

  }

}


class CardElasticClientFunctionalSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with TestEsClient
    with BeforeAndAfter
    with WaitUntil {

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .overrides(new TestEsFakeModule)
      .overrides(bind[CardElasticClient].to[CardElasticClientImpl])
      .build()


  before {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
    cleanIndex("cards")
  }

  after {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
    cleanIndex("cards")
  }

  val user = User("UserId", "Email")
  val index: String = "cards"
  val title = "TITLE"
  val body = "BODY"
  val cardTags = List("T1", "T2")
  val createdAt = new DateTime(2020, 1, 1, 0, 0)
  val updatedAt = new DateTime(2020, 1, 1, 0, 0)
  val cardData = CardData(None, title, body, cardTags, Some(createdAt), Some(updatedAt))

  def getNowAsISOString = ISODateTimeFormat.dateTime().print(DateTime.now())

  def cardRepository = app.injector.instanceOf[CardRepository]

  "Functional Tests for ES card client" should {

    "Create and delete a card" taggedAs(FunctionalTestsTag) in {

      import com.sksamuel.elastic4s.ElasticDsl._

      val queryByTitleAndBody = search(index).query(
        boolQuery().must(
          matchQuery("title", title),
          matchQuery("body", body)
        )
      )

      val resultBefore = client.execute(queryByTitleAndBody).await.result
      resultBefore.hits.total.value mustEqual 0

      val id = cardRepository.create(cardData, user).get
      refreshIdx(index)
      waitUntil { () =>
        client.execute(queryByTitleAndBody).await.result.hits.total.value > 0
      }

      val resultAfter = client.execute(queryByTitleAndBody).await.result
      resultAfter.hits.total.value mustEqual 1
      resultAfter.hits.hits.head.id mustEqual id
      resultAfter.hits.hits.head.sourceAsMap("userId") mustEqual user.id
      resultAfter.hits.hits.head.sourceAsMap("title") mustEqual title
      resultAfter.hits.hits.head.sourceAsMap("body") mustEqual body
      resultAfter.hits.hits.head.sourceAsMap("tags") mustEqual cardTags
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

  }

}
