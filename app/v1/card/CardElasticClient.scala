package v1.card

import com.google.inject.Inject
import com.sksamuel.elastic4s.ElasticClient
import play.api.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

trait CardElasticClient {
  def create(cardData: CardData, id: String): Unit
}

class CardElasticClientMock extends CardElasticClient {

  val logger = Logger(getClass)

  def create(cardData: CardData, id: String): Unit = {
    logger.info(s"Mocked create for $cardData and $id")
  }

}

class CardElasticClientImpl @Inject()(
  elasticClient: ElasticClient)(
  implicit val ec: ExecutionContext
) extends CardElasticClient {

  import com.sksamuel.elastic4s.ElasticDsl._

  val logger = Logger(getClass)

  def create(cardData: CardData, id: String): Unit = {
    logger.info(s"Creating elastic search entry for $cardData and $id")
    elasticClient.execute {
      indexInto("cards").id(id).fields("title" -> cardData.title, "body" -> cardData.body)
    } andThen {
      case Failure(exception) => logger.error("Elastic Search failed!", exception)
      case Success(value) => logger.info(s"Success: $value")
    }
  }

}
