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
