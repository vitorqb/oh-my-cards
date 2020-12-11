package v1.card.elasticclient

import com.google.inject.Inject
import com.sksamuel.elastic4s.ElasticClient
import play.api.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import com.sksamuel.elastic4s.Response
import com.sksamuel.elastic4s.RequestSuccess
import com.sksamuel.elastic4s.RequestFailure
import com.sksamuel.elastic4s.requests.searches.SearchResponse
import com.sksamuel.elastic4s.requests.searches.queries.Query
import services.TagsFilterMiniLang.TagsFilterMiniLang
import services.TagsFilterMiniLang.ParsingError
import com.sksamuel.elastic4s.requests.searches.sort.FieldSort
import com.sksamuel.elastic4s.requests.searches.sort.ScoreSort
import com.sksamuel.elastic4s.requests.searches.sort.SortOrder
import v1.card.exceptions._
import v1.card.repository.{CardElasticClientLike}
import v1.card.models._
import scala.concurrent.Await
import scala.concurrent.duration.Duration


final case class CardElasticClientException(
  val message: String = "Something went wrong on ElasticSearch",
  val cause: Throwable = None.orNull
) extends Exception(message, cause)


/**
  * A mocked implementation of the CardElasticClient for tests.
  *  @pram idsFound the ids to return when findIds is called.
  */
class CardElasticClientMock() extends CardElasticClientLike {

  import CardElasticClientMock._

  val logger = Logger(getClass)

  override def create(data: CardCreateData, context: CardCreationContext): Unit = {
    logger.info(s"Mocked create for $data with $context")
  }

  override def update(cardData: CardData, context: CardUpdateContext): Unit = {
    logger.info(s"Mocked update for $cardData with $context")
  }

  override def delete(id: String): Unit = {
    logger.info(s"Mocked delete for $id")
  }

  override def findIds(data: CardListData): Future[IdsFindResult] = {
    logger.info(s"Mocked findIds for $data")
    Future.successful(IdsFindResult(idsFound, countOfItems))
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
) extends CardElasticClientLike {

  import com.sksamuel.elastic4s.ElasticDsl._

  val index = "cards"
  val logger = Logger(getClass)

  def wait[A](future: Future[A]): Unit = Await.ready(future, Duration("10 s"))

  def handleResponse[T](response: Try[Response[T]]) = response match {
    case Failure(exception) => logger.error("Elastic Search failed!", exception)
    case Success(value) => logger.info(s"A request was completed successfully")
  }

  override def create(data: CardCreateData, context: CardCreationContext): Unit = {
    logger.info(s"Creating elastic search entry with $context")
    wait {
      elasticClient.execute {
        indexInto(index).id(context.id).fields(
          "title" -> data.title,
          "body" -> data.body,
          "updatedAt" -> context.now,
          "createdAt" -> context.now,
          "userId" -> context.user.id,
          "tags" -> data.tags.map(_.toLowerCase())
        )
      }.andThen(handleResponse(_))
    }
  }

  override def update(cardData: CardData, context: CardUpdateContext): Unit = {
    logger.info(s"Updating elastic search entry for ${cardData.id} with $context")
    wait {
      elasticClient.execute {
        updateById(index, cardData.id).doc(
          "title" -> cardData.title,
          "body" -> cardData.body,
          "updatedAt" -> context.now,
          "userId" -> context.user.id,
          "tags" -> cardData.tags.map(_.toLowerCase())
        )
      }.andThen(handleResponse(_))
    }
  }

  override def delete(id: String): Unit = {
    logger.info(s"Deleting elastic search entry for $id")
    wait {
      elasticClient.execute(deleteById(index, id)).andThen(handleResponse(_))
    }
  }

  override def findIds(data: CardListData): Future[IdsFindResult] =
    new CardElasticIdFinder(elasticClient, index).findIds(data)

}

class CardElasticIdFinder(
  elasticClient: ElasticClient,
  val index: String
)(
  implicit val ec: ExecutionContext
) {

  import com.sksamuel.elastic4s.ElasticDsl._  
  val logger = Logger(getClass)

  /**
    * Handles a success ES query.
    */
  def onSuccess(response: RequestSuccess[SearchResponse]): Future[IdsFindResult] = {
    Future.successful {
      val result = IdsFindResult(
        response.result.hits.hits.map(x => x.id).toSeq,
        response.result.hits.total.value.intValue()
      )
      logger.info(s"Ids found: $result")
      result
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
  def findIds(data: CardListData): Future[IdsFindResult] = Future {
    logger.info(s"Getting ids for $data")

    var queries : List[Query] = List(matchAllQuery())
    def appendQuery(q: Query) = { queries = q :: queries }
    def simpleTagQuery(tag: String) = termQuery("tags.keyword", tag.toLowerCase())

    //Add userid query
    appendQuery {
      termQuery("userId.keyword", data.userId)
    }

    //Search term query
    data.searchTerm.foreach { searchTerm => appendQuery {
      multiMatchQuery(searchTerm).fields("title", "body").operator("or").fuzziness("AUTO")
    }}

    //Tag lang query
    data.query.foreach { statement => appendQuery {
      TagsFilterMiniLang.parseAsES(statement) match {
        case Success(query) => query
        case Failure(e: ParsingError) => throw new TagsFilterMiniLangSyntaxError(e.message, e)
        case Failure(e) => throw e
      }
    }}

    //Tags query
    data.tags.foreach { tag => appendQuery {
      simpleTagQuery(tag)
    }}

    //Tags not query
    data.tagsNot.foreach { tag => appendQuery {
      boolQuery().not(simpleTagQuery(tag))
    }}

    val request = search(index)
      .query(boolQuery().must(queries))
      .sortBy(ScoreSort(SortOrder.DESC), FieldSort("createdAt", order=SortOrder.DESC))
      .from((data.page - 1) * data.pageSize)
      .size(data.pageSize)
      .trackTotalHits(true)

    logger.info(s"Sending search request for $data")
    elasticClient.execute(request).flatMap {
      case success: RequestSuccess[SearchResponse] => onSuccess(success)
      case failure: RequestFailure => onFailure(failure)
    }

  }.flatten

}
