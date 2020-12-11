package v1.admin.elasticSearchSynchronizer

import play.api.Logger
import com.google.inject.Inject
import play.api.db.Database
import org.joda.time.DateTime
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import com.sksamuel.elastic4s.ElasticClient
import scala.util.Failure
import scala.util.Success
import com.sksamuel.elastic4s.RequestFailure
import com.sksamuel.elastic4s.RequestSuccess
import com.sksamuel.elastic4s.Response
import com.sksamuel.elastic4s.requests.searches.SearchResponse

import scala.language.postfixOps

/**
  * A command-like class responsible of synchronizing Elastic Search with the db.
  */
class ElasticSearchSynchornizer @Inject() (
    dbReader: DatabaseReader,
    esClient: SyncronizerElasticClient
)(implicit
    val ec: ExecutionContext
) {

  val logger = Logger(getClass)

  def logError(t: Throwable) = logger.error("Catched error!", t)

  /**
    * Runs the synchronization.
    */
  def run(): Future[Unit] = {
    //This is a very ugly and inneficient algorithim. We could try doing something smarter,
    //specifically we could do something with a `startDatetime` and `endDatetime`, and synchronize
    //all entries that have been updated during this period specifically.
    deleteStaleEntries().map { _ =>
      updateAllEntries().map { _ =>
        ()
      }
    }
  }

  /**
    * Delete all entries from ES.
    */
  def updateAllEntries(): Future[Unit] =
    dbReader.readAll().flatMap { rows =>
      rows.foldLeft(Future.successful(()))((acc, row) => {
        acc.flatMap(_ => esClient.send(row))
      })
    }

  /**
    * Deletes entries from ES that are no longer in the DB.
    */
  def deleteStaleEntries() = {
    esClient
      .getAllIds()
      .flatMap { esIds =>
        logger.info(s"Ids in ES: ${esIds}")
        dbReader
          .getAllIds()
          .flatMap { dbIds =>
            logger.info(s"Ids in DB: ${dbIds}")
            val staleIds: Set[String] = esIds diff dbIds
            logger.info(s"Identified stale ids: ${staleIds}")
            def deleteOne(id: String): Future[Unit] =
              esClient.delete(id).recover(logError _).map(_ => ())
            staleIds.foldLeft(Future.successful(())) { (acc, id) =>
              acc.flatMap(_ => deleteOne(id))
            }
          }
          .recover(logError _)
      }
      .recover(logError _)
  }

}

/**
  * Helpers class to access the db for synchronization.
  */
class DatabaseReader @Inject() (db: Database)(implicit
    val ec: ExecutionContext
) {
  import anorm._

  val rowWithoutTagsParser = {
    import anorm.SqlParser._
    (str("id") ~ str("userId") ~ str("title") ~ str("body") ~ get[Option[
      DateTime
    ]]("createdAt")
      ~ get[Option[DateTime]]("createdAt"))
  }

  val tagRowParser = {
    import anorm.SqlParser._
    str("cardId") ~ str("tag") map (flatten) *
  }

  /**
    * Reads all rows in the db.
    */
  def readAll(): Future[List[DatabaseRow]] =
    Future {
      db.withConnection { implicit c =>
        val rowsWithoutTag =
          SQL"""SELECT id, title, body, createdAt, updatedAt, userId FROM cards"""
            .as(rowWithoutTagsParser.*)
        val tags = SQL"""SELECT cardId, tag FROM cardsTags"""
          .as(tagRowParser)
          .groupBy { case (id, _) => id }
          .view
          .mapValues { lstOfTags => lstOfTags.map { case (_, tag) => tag } }
        rowsWithoutTag.map {
          case (id ~ userId ~ title ~ body ~ createdAt ~ updatedAt) =>
            DatabaseRow(
              id,
              userId,
              title,
              body,
              createdAt,
              updatedAt,
              tags.getOrElse(id, List())
            )
        }
      }
    }

  /**
    * Returns a set with all known ids.
    */
  def getAllIds(): Future[Set[String]] =
    Future {
      db.withConnection { implicit c =>
        SQL"""SELECT id FROM cards""".as(SqlParser.str("id").*).toSet
      }
    }

}

/**
  * Represents a row read from the db.
  */
case class DatabaseRow(
    id: String,
    userId: String,
    title: String,
    body: String,
    createdAt: Option[DateTime],
    updatedAt: Option[DateTime],
    tags: List[String]
)

/**
  * Helpers class to send mutate ElasticSearch.
  */
class SyncronizerElasticClient @Inject() (elasticClient: ElasticClient)(implicit
    val ec: ExecutionContext
) {

  import com.sksamuel.elastic4s.ElasticDsl._

  val logger = Logger(getClass())
  val index = "cards"

  /**
    * Returns an instance of the helper class IdsScroller for getting all ids.
    */
  def idsScroller =
    new IdsScroller(index, elasticClient, requestSize, scrollKeepAlive)

  /**
    * How many items are requested from ES in a go.
    */
  val requestSize = 500

  /**
    * Scroll time (see ES documentation on scroll)
    */
  val scrollKeepAlive = "1m"

  /**
    * Sends a db row to ES, making sure that the document exists as it should.
    */
  def send(row: DatabaseRow): Future[Unit] = {
    logger.info(s"Sending row: ${row}")

    elasticClient.execute {
      indexInto(index)
        .id(row.id)
        .fields(
          "title" -> row.title,
          "body" -> row.body,
          "updatedAt" -> row.updatedAt.orNull,
          "createdAt" -> row.createdAt.orNull,
          "userId" -> row.userId,
          "tags" -> row.tags.map(_.toLowerCase())
        )
    } andThen {
      case Failure(exception) => logger.error("Future failed!", exception)
      case Success(value: RequestFailure) =>
        logger.error(s"Request failed! ${value}")
      case Success(value: RequestSuccess[_]) =>
        logger.info(s"Success: ${value}")
    } map (_ => ())
  }

  /**
    * Returns a set with all known ids.
    */
  def getAllIds(): Future[Set[String]] = idsScroller.run().map(_.toSet)

  /**
    * Deletes an id.
    */
  def delete(id: String): Future[Unit] =
    elasticClient.execute {
      logger.info(s"Deleting: ${id}")
      deleteById(index, id)
    } map {
      case value: RequestFailure =>
        throw new Exception(s"Failed to delete ${id}")
      case value: RequestSuccess[_] => ()
    }

}

/**
  * Helper class for scrolling results
  */
class IdsScroller(
    index: String,
    elasticClient: ElasticClient,
    requestSize: Int,
    scrollKeepAlive: String
)(implicit
    val ec: ExecutionContext
) {

  import com.sksamuel.elastic4s.ElasticDsl._

  val logger = Logger(getClass())

  /**
    *  Returns true if we have fetched all the ids.
    */
  def isFinished(ids: List[String], results: SearchResponse) =
    ids.length == results.totalHits

  /**
    * Prepares the next request.
    */
  def nextReq(resp: SearchResponse) =
    elasticClient.execute {
      val req = searchScroll(resp.scrollId.get).keepAlive(scrollKeepAlive)
      logger.info(s"Executing request: ${req}")
      req
    }

  /**
    * Handler failed requests.
    */
  def handleFailure(req: RequestFailure) =
    throw new Exception(s"Failed to get ids from ES! ${req}")

  /**
    * Handler failed futures.
    */
  def handleFailure(t: Throwable) =
    throw new Exception(s"Future failed while getting ids from ES", t)

  /**
    * Runs the next step, for a given accumulator with all known ids and the last request.
    */
  def doRun(
      acc: List[String],
      request: Future[Response[SearchResponse]]
  ): Future[List[String]] =
    request.map {
      case request: RequestFailure => handleFailure(request)
      case request: RequestSuccess[_] => {
        val newAcc: List[String] = acc ++ request.result.hits.hits.map(_.id)
        if (isFinished(newAcc, request.result))
          Future.successful(newAcc)
        else
          doRun(newAcc, nextReq(request.result))
      }
    }.flatten

  /**
    * API to runs the scroller to get all ids.
    */
  def run(): Future[List[String]] = {
    val query =
      search(index).matchAllQuery().scroll(scrollKeepAlive).size(requestSize)
    logger.info(s"Executing initial query: ${query}")
    doRun(List(), elasticClient.execute(query))
  }
}
