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
import com.sksamuel.elastic4s.RequestSuccess
import com.sksamuel.elastic4s.RequestFailure
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.requests.searches.queries.matches.MultiMatchQuery
import v1.auth.User

final case class CardElasticClientException(
  val message: String = "Something went wrong on ElasticSearch",
  val cause: Throwable = None.orNull
) extends Exception(message, cause)


trait CardElasticClient {

  /**
    * Creates a new entry on ElasticSearch for a new cardData, with a given id created at
    *  a specific time.
    */
  def create(cardData: CardData, id: String, createdAt: DateTime, user: User): Unit

  /**
    * Updates an entry on ElasticSearch for an existing cardData.
    */
  def update(cardData: CardData, updatedAt: DateTime, user: User): Unit

  /**
    * Deletes an entry from ElasticSearch for an existing cardData.
    */
  def delete(id: String): Unit

  /**
    * Returns a seq of ids from ElasticSearch that fuzzy matches on title or body.
    */
  def findIds(searchTerm: String): Future[Seq[String]]
}

/**
  * A mocked implementation of the CardElasticClient for tests.
  *  @pram idsFound the ids to return when findIds is called.
  */
class CardElasticClientMock() extends CardElasticClient {

  val logger = Logger(getClass)

  def idsFound = CardElasticClientMock.idsFound

  def create(cardData: CardData, id: String, createdAt: DateTime, user: User): Unit = {
    logger.info(s"Mocked create for $cardData and $id at $createdAt for user $user")
  }

  def update(cardData: CardData, updatedAt: DateTime, user: User): Unit = {
    logger.info(s"Mocked update for $cardData at $updatedAt for $user")
  }

  def delete(id: String): Unit = {
    logger.info(s"Mocked delete for $id")
  }

  def findIds(searchTerm: String): Future[Seq[String]] = {
    logger.info(s"Mocked findIds for $searchTerm")
    Future.successful(idsFound)
  }
  
}

/**
  * Companion object of the mock, usefull for testing
  */
object CardElasticClientMock {
  var idsFound: Seq[String] = Seq()

  def withIdsFound(x: Seq[String])(block: => Any) = {
    val valueBefore = idsFound
    idsFound = x
    try {
      block
    } finally {
      idsFound = valueBefore
    }
  }
}


class CardElasticClientImpl @Inject()(
  elasticClient: ElasticClient)(
  implicit val ec: ExecutionContext
) extends CardElasticClient {

  import com.sksamuel.elastic4s.ElasticDsl._

  val index = "cards"
  val logger = Logger(getClass)

  def idFinder = new CardElasticIdFinder(elasticClient, index)
  
  def handleResponse[T](response: Try[Response[T]]) = response match {
    case Failure(exception) => logger.error("Elastic Search failed!", exception)
    case Success(value) => logger.info(s"Success: $value")
  }

  def create(cardData: CardData, id: String, createdAt: DateTime, user: User): Unit = {
    logger.info(s"Creating elastic search entry for $cardData and $id at $createdAt for $user")
    elasticClient.execute {
      indexInto(index).id(id).fields(
        "title" -> cardData.title,
        "body" -> cardData.body,
        "updatedAt" -> createdAt,
        "createdAt" -> createdAt,
        "userId" -> user.id,
        "tags" -> cardData.tags
      )
    }.onComplete(handleResponse)
  }

  def update(cardData: CardData, updatedAt: DateTime, user: User): Unit = {
    logger.info(s"Updating elastic search entry for $cardData at $updatedAt for $user")
    elasticClient.execute {
      updateById(index, cardData.id.get).doc(
        "title" -> cardData.title,
        "body" -> cardData.body,
        "updatedAt" -> updatedAt,
        "userId" -> user.id,
        "tags" -> cardData.tags
      )
    }.onComplete(handleResponse)
  }

  def delete(id: String): Unit = {
    logger.info(s"Deleting elastic search entry for $id")
    elasticClient.execute(deleteById(index, id)).onComplete(handleResponse)
  }

  def findIds(searchTerm: String): Future[Seq[String]] = idFinder.findIds(searchTerm)

}

class CardElasticIdFinder(
  elasticClient: ElasticClient,
  val index: String
)(
  implicit val ec: ExecutionContext
) {

  import com.sksamuel.elastic4s.ElasticDsl._  
  val logger = Logger(getClass)

  def onSuccess(response: RequestSuccess[SearchResponse]): Future[Seq[String]] = {
    logger.info("Response: " + response)
    Future.successful(response.result.hits.hits.map(x => x.id).toIndexedSeq)
  }

  def onFailure[T](response: RequestFailure): Future[T] = {
    val exception = CardElasticClientException(response.body.getOrElse("Unknown Exception"))
    logger.error("Error when querying ElasticSearch", exception)
    Future.failed(exception)
  }

  def findIds(searchTerm: String): Future[Seq[String]] = {
    logger.info(s"Getting ids for $searchTerm")
    val query = multiMatchQuery(searchTerm).fields("title", "body").operator("or")
    val request = search(index).query(query)
    logger.info(s"Sending request $request")
    elasticClient.execute(request).flatMap {
      case success: RequestSuccess[SearchResponse] => onSuccess(success)
      case failure: RequestFailure => onFailure(failure)
    }

  }

}
