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
import com.sksamuel.elastic4s.requests.searches.queries.Query
import services.TagsFilterMiniLang.TagsFilterMiniLang
import services.TagsFilterMiniLang.ParsingError

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
    * Returns a seq of ids from ElasticSearch that matches a CardListRequest.
    */
  def findIds(cardListReq: CardListRequest): Future[CardElasticIdFinder.Result]
}

/**
  * A mocked implementation of the CardElasticClient for tests.
  *  @pram idsFound the ids to return when findIds is called.
  */
class CardElasticClientMock() extends CardElasticClient {

  import CardElasticIdFinder._
  import CardElasticClientMock._

  val logger = Logger(getClass)

  override def create(cardData: CardData, id: String, createdAt: DateTime, user: User): Unit = {
    logger.info(s"Mocked create for $cardData and $id at $createdAt for user $user")
  }

  override def update(cardData: CardData, updatedAt: DateTime, user: User): Unit = {
    logger.info(s"Mocked update for $cardData at $updatedAt for $user")
  }

  override def delete(id: String): Unit = {
    logger.info(s"Mocked delete for $id")
  }

  override def findIds(cardListReq: CardListRequest): Future[Result] = {
    logger.info(s"Mocked findIds for $cardListReq")
    Future.successful(Result(idsFound, countOfItems))
  }
  
}

/**
  * Companion object of the mock, usefull for testing
  */
object CardElasticClientMock {
  var idsFound: Seq[String] = Seq()
  var countOfItems: Int = 100

  /**
    * Temporarily sets `idsFound` and `countOfItems`, evaluates block, then set them back.
    */
  def withIdsFound(x: Seq[String], i: Int = 100)(block: => Any) = {
    val valueBefore = idsFound
    val countBefore = countOfItems
    idsFound = x
    countOfItems = i
    try {
      block
    } finally {
      idsFound = valueBefore
      countOfItems = countBefore
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

  def handleResponse[T](response: Try[Response[T]]) = response match {
    case Failure(exception) => logger.error("Elastic Search failed!", exception)
    case Success(value) => logger.info(s"Success: $value")
  }

  override def create(cardData: CardData, id: String, createdAt: DateTime, user: User): Unit = {
    logger.info(s"Creating elastic search entry for $cardData and $id at $createdAt for $user")
    elasticClient.execute {
      indexInto(index).id(id).fields(
        "title" -> cardData.title,
        "body" -> cardData.body,
        "updatedAt" -> createdAt,
        "createdAt" -> createdAt,
        "userId" -> user.id,
        "tags" -> cardData.tags.map(_.toLowerCase())
      )
    }.onComplete(handleResponse)
  }

  override def update(cardData: CardData, updatedAt: DateTime, user: User): Unit = {
    logger.info(s"Updating elastic search entry for $cardData at $updatedAt for $user")
    elasticClient.execute {
      updateById(index, cardData.id.get).doc(
        "title" -> cardData.title,
        "body" -> cardData.body,
        "updatedAt" -> updatedAt,
        "userId" -> user.id,
        "tags" -> cardData.tags.map(_.toLowerCase())
      )
    }.onComplete(handleResponse)
  }

  override def delete(id: String): Unit = {
    logger.info(s"Deleting elastic search entry for $id")
    elasticClient.execute(deleteById(index, id)).onComplete(handleResponse)
  }

  override def findIds(cardListReq: CardListRequest): Future[CardElasticIdFinder.Result] =
    new CardElasticIdFinder(elasticClient, index).findIds(cardListReq)

}

class CardElasticIdFinder(
  elasticClient: ElasticClient,
  val index: String
)(
  implicit val ec: ExecutionContext
) {

  import CardElasticIdFinder._

  import com.sksamuel.elastic4s.ElasticDsl._  
  val logger = Logger(getClass)

  /**
    * Handles a success ES query.
    */
  def onSuccess(response: RequestSuccess[SearchResponse]): Future[Result] = {
    logger.info("Response: " + response)
    Future.successful {
      Result (
        response.result.hits.hits.map(x => x.id).toIndexedSeq,
        response.result.hits.total.value.intValue()
      )
  }
  }

  /**
    * Handles a failed ES query.
    */
  def onFailure[T](response: RequestFailure): Future[T] = {
    val exception = CardElasticClientException(response.body.getOrElse("Unknown Exception"))
    logger.error("Error when querying ElasticSearch", exception)
    Future.failed(exception)
  }

  /**
    * Find all ids matching a query.
    * The result has the matching ids and the count of total matches.
    */
  def findIds(cardListReq: CardListRequest): Future[Result] = {
    logger.info(s"Getting ids for $cardListReq")

    var queries : List[Query] = List(matchAllQuery())
    def appendQuery(q: Query) = { queries = q :: queries }
    def simpleTagQuery(tag: String) = termQuery("tags.keyword", tag.toLowerCase())

    //Add userid query
    appendQuery {
      termQuery("userId.keyword", cardListReq.userId)
    }

    //Search term query
    cardListReq.searchTerm.foreach { searchTerm => appendQuery {
      multiMatchQuery(searchTerm).fields("title", "body").operator("or").fuzziness("AUTO")
    }}

    //Tag lang query
    cardListReq.query.foreach { statement => appendQuery {
      TagsFilterMiniLang.parseAsES(statement) match {
        case Success(query) => query
        case Failure(e: ParsingError) => throw new TagsFilterMiniLangSyntaxError(e.message, e)
        case Failure(e) => throw e
      }
    }}

    //Tags query
    cardListReq.tags.foreach { tag => appendQuery {
      simpleTagQuery(tag)
    }}

    //Tags not query
    cardListReq.tagsNot.foreach { tag => appendQuery {
      boolQuery().not(simpleTagQuery(tag))
    }}

    val request = search(index)
      .query(boolQuery().must(queries))
      .sortByFieldAsc("createdAt")
      .from((cardListReq.page - 1) * cardListReq.pageSize)
      .size(cardListReq.pageSize)
      .trackTotalHits(true)

    logger.info(s"Sending request $request")
    elasticClient.execute(request).flatMap {
      case success: RequestSuccess[SearchResponse] => onSuccess(success)
      case failure: RequestFailure => onFailure(failure)
    }

  }

}

object CardElasticIdFinder {

  /**
    * Auxiliar class to host results of a find. `ids` contain matched ids taking
    * pagination into account. `countOfIds` is an integer with the total count of
    * matching elements, disconsidering pagination.
    */
  case class Result(ids: Seq[String], countOfIds: Integer)

}
