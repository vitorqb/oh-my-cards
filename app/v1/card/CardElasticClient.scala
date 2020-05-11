package v1.card

import com.google.inject.Inject
import com.sksamuel.elastic4s.ElasticClient
import play.api.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import org.joda.time.DateTime
import scala.util.Try
import com.sksamuel.elastic4s.Response
import com.sksamuel.elastic4s.requests.indexes.IndexResponse

trait CardElasticClient {

  /**
    * Creates a new entry on ElasticSearch for a new cardData, with a given id created at
    *  a specific time.
    */
  def create(cardData: CardData, id: String, createdAt: DateTime): Unit

  /**
    * Updates an entry on ElasticSearch for an existing cardData.
    */
  def update(cardData: CardData, updatedAt: DateTime): Unit

  /**
    * Deletes an entry from ElasticSearch for an existing cardData.
    */
  def delete(id: String): Unit
}

class CardElasticClientMock extends CardElasticClient {

  val logger = Logger(getClass)

  def create(cardData: CardData, id: String, createdAt: DateTime): Unit = {
    logger.info(s"Mocked create for $cardData and $id at $createdAt")
  }

  def update(cardData: CardData, updatedAt: DateTime): Unit = {
    logger.info(s"Mocked update for $cardData at $updatedAt")
  }

  def delete(id: String): Unit = {
    logger.info(s"Mocked delete for $id")
  }
  
}

class CardElasticClientImpl @Inject()(
  elasticClient: ElasticClient)(
  implicit val ec: ExecutionContext
) extends CardElasticClient {

  import com.sksamuel.elastic4s.ElasticDsl._

  val index = "cards"
  val logger = Logger(getClass)
  
  def handleResponse[T](response: Try[Response[T]]) = response match {
    case Failure(exception) => logger.error("Elastic Search failed!", exception)
    case Success(value) => logger.info(s"Success: $value")
  }

  def create(cardData: CardData, id: String, createdAt: DateTime): Unit = {
    logger.info(s"Creating elastic search entry for $cardData and $id at $createdAt")
    elasticClient.execute {
      indexInto(index).id(id).fields(
        "title" -> cardData.title,
        "body" -> cardData.body,
        "updatedAt" -> createdAt,
        "createdAt" -> createdAt
      )
    }.onComplete(handleResponse)
  }

  def update(cardData: CardData, updatedAt: DateTime): Unit = {
    logger.info(s"Updating elastic search entry for $cardData at $updatedAt")
    elasticClient.execute {
      updateById(index, cardData.id.get).doc(
        "title" -> cardData.title,
        "body" -> cardData.body,
        "updatedAt" -> updatedAt
      )
    }.onComplete(handleResponse)
  }

  def delete(id: String): Unit = {
    logger.info(s"Deleting elastic search entry for $id")
    elasticClient.execute(deleteById(index, id)).onComplete(handleResponse)
  }
}
