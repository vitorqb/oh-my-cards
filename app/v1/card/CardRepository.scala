package v1.card

import javax.inject.Inject
import java.util.UUID.randomUUID
import scala.util.{Try,Success,Failure}
import anorm.{SQL,RowParser,Macro,SqlParser,SeqParameter}
import play.api.db.Database
import v1.auth.User
import services.UUIDGenerator
import anorm.`package`.SqlStringInterpolation
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.sql.Connection
import anorm.SimpleSql
import anorm.Row
import services.Clock
import org.joda.time.DateTime
import anorm.JodaParameterMetaData._


/**
  * A trait for all known user exceptions.
  */
sealed trait CardRepositoryUserException { val message: String }


/**
  * Custom exception signaling that a card does not exist.
  */
final case class CardDoesNotExist(
  val message: String = "The required card does not exist.",
  val cause: Throwable = None.orNull
) extends Exception(message, cause) with CardRepositoryUserException

final case class TagsFilterMiniLangSyntaxError(
  val message: String,
  val cause: Throwable = None.orNull
) extends Exception(message, cause) with CardRepositoryUserException


//!!!! TODO We should not be using CardData as parameter for the functions. We should
//          only *return* CardData. The input must be a CardInput, without id, 
//          or createdAt, updatedAt.
/**
  * The data for a Card.
  */
final case class CardData(
  id: Option[String],
  title: String,
  body: String,
  tags: List[String],
  createdAt: Option[DateTime] = None,
  updatedAt: Option[DateTime] = None
)


/**
  * An implementation for a card repository.
  */
class CardRepository @Inject()(
  db: Database,
  uuidGenerator: UUIDGenerator,
  tagsRepo: TagsRepository,
  cardElasticClient: CardElasticClient,
  clock: Clock)(
  implicit val ec: ExecutionContext
) {

  private val cardDataParser: RowParser[CardData] = {
    import anorm._
    (
      SqlParser.str("id") ~
        SqlParser.str("title") ~
        SqlParser.str("body") ~
        SqlParser.get[Option[DateTime]]("createdAt") ~
        SqlParser.get[Option[DateTime]]("updatedAt")
    ) map {
      case id ~ title ~ body ~ createdAt ~ updatedAt =>
        CardData(Some(id), title, body, List(), createdAt, updatedAt)
    }
  }

  def create(cardData: CardData, user: User): Try[String] = {
    val now = clock.now()
    if (cardData.id.isDefined) {
      Failure(new Exception("Id for create should be null!"))
    } else {
      val id = uuidGenerator.generate
      db.withTransaction { implicit c =>
        SQL(
          """INSERT INTO cards(id, userId, title, body, createdAt, updatedAt)
             VALUES ({id}, {userId}, {title}, {body}, {now}, {now})"""
        ).on(
          "id" -> id,
          "userId" -> user.id,
          "title" -> cardData.title,
          "body" -> cardData.body,
          "now" -> now
        ).executeInsert()
        tagsRepo.create(id, cardData.tags)
        cardElasticClient.create(cardData, id, now, user)
        Success(id)
      }
    }
  }

  def get(id: String, user: User): Option[CardData] = db.withConnection { implicit c =>
    SQL(s"${SqlHelpers.sqlGetStatement} AND id = {id}")
      .on("id" -> id, "userId" -> user.id)
      .as(cardDataParser.*)
      .headOption
      .map(_.copy(tags=tagsRepo.get(id)))
  }

  /**
    * Finds a list of cards for a given user.
    */
  def find(request: CardListRequest): Future[Iterable[CardData]] = Future {
    new CardFinder(request, cardElasticClient).prepareSql().map { simpleSql =>
      db.withConnection { implicit c =>
        def assocTags(cardData: CardData) = cardData.copy(tags=tagsRepo.get(cardData.id.get))
        simpleSql.as(cardDataParser.*).map(assocTags)
      }
    }
  }.flatten

  /**
    * Returns the total number of items matching a request for a list of cards.
    */
  def countItemsMatching(request: CardListRequest): Future[Int] = Future {
    new CardCounter(request, cardElasticClient).prepareSql().map { simpleSql =>
      db.withConnection { implicit c =>
        val parser = (anorm.SqlParser.get[Int]("count").*)
        simpleSql.as(parser).headOption.getOrElse(0)
      }
    }
  }.flatten

  /**
    * Deletes a card by it's id.
    */
  def delete(id: String, user: User): Future[Try[Unit]] = Future {
    get(id, user) match {
      case None => Failure(new CardDoesNotExist)
      case Some(_) => db.withTransaction { implicit c =>
        SQL(s"DELETE ${SqlHelpers.sqlFromWhereStatement} AND id = {id}")
          .on("id" -> id, "userId" -> user.id)
          .executeUpdate()
        tagsRepo.delete(id)
        cardElasticClient.delete(id)
        Success(())
      }
    }
  }

  /**
    * Updates a card.
    */
  def update(data: CardData, user: User): Future[Try[Unit]] = Future { Try { db.withTransaction {
    implicit c =>
    val now = clock.now()
    SQL("""
        UPDATE cards SET title={title}, body={body}, updatedAt={now}
        WHERE id={id} AND userId={userId}
       """)
      .on(
        "title" -> data.title,
        "body" -> data.body,
        "id" -> data.id,
        "userId" -> user.id,
        "now" -> now
      )
      .executeUpdate()
    tagsRepo.delete(data.id.get)
    tagsRepo.create(data.id.get, data.tags)
    cardElasticClient.update(data, now, user)
  }}}

  /**
    * Returns all tags for a given user.
    */
  def getAllTags(user: User): Future[List[String]] = Future { db.withTransaction { implicit c =>
    import anorm.SqlParser._
    SQL"""
       SELECT tag
       FROM cardsTags
       JOIN cards ON cards.id = cardId
       WHERE cards.userId = ${user.id}
    """.as(str(1).*)
  }}
}

/**
  * Helper object manage cards tags.
  */
private class TagsRepository {

  /**
    * Delets all tags for a given card id.
    */
  def delete(cardId: String)(implicit c:Connection): Unit = {
    SQL"DELETE FROM cardsTags WHERE cardId = ${cardId}".execute()
  }

  /**
    * Create all tags for a given card id.
    */
  def create(cardId: String, tags: List[String])(implicit c: Connection): Unit = {
    tags.foreach { tag =>
      SQL"INSERT INTO cardsTags(cardId, tag) VALUES (${cardId}, ${tag})".executeInsert()
    }
  }

  /**
    * Returns all tags for a given card id.
    */
  def get(cardId: String)(implicit c: Connection): List[String] = {
    SQL"SELECT tag FROM cardsTags WHERE cardId = ${cardId} ORDER BY tag"
      .as(SqlParser.scalar[String].*)
  }
}


/** 
  * Class responsible for querying the db for a list of cards.
  */
private class CardFinder(
  val request: CardListRequest,
  val cardElasticClient: CardElasticClient)(
  implicit val ec: ExecutionContext
) extends CardSqlFilterOperations {

  /**
    * Prepares the query.
    */
  def prepareSql(): Future[SimpleSql[Row]] = withParams { SQL {
    s"""${SqlHelpers.sqlGetStatement}
        ${searchTermFilter}
        ${tagsFilter}
        ${tagsNotFilter}
        ${tagsMiniLangFilter}
        ORDER BY id DESC LIMIT {pageSize} OFFSET {offset}"""
  }}
}


/**
  * Class responseible for counting the number of cards matching a list query.
  */
private class CardCounter(
  val request: CardListRequest,
  val cardElasticClient: CardElasticClient)(
  implicit val ec: ExecutionContext
) extends CardSqlFilterOperations {

  /**
    * Prepares the query.
    */
  def prepareSql(): Future[SimpleSql[Row]] = withParams { SQL {
    s"""SELECT COUNT(*) AS count
        ${SqlHelpers.sqlFromWhereStatement}
        ${searchTermFilter}
        ${tagsFilter}
        ${tagsNotFilter}
        ${tagsMiniLangFilter}"""
  }}

}



/**
  * A common trait for filtering operations for cards.
  */
private trait CardSqlFilterOperations {
  import services.TagsFilterMiniLang.{TagsFilterMiniLang,Result=>MiniLangResult,ParsingError}

  implicit  val ec: ExecutionContext

  /**
    * The list request. Must be implemented by subclasses.
    */
  val request: CardListRequest

  /**
    * The CardElasticClient to use. Must be implemented by subclasses.
    */
  val cardElasticClient: CardElasticClient

  /**
    * The filter for the `include` tags.
    */
  val tagsFilter = if (request.tags.isEmpty) "" else "AND {tagsFilterSqlSeq}"

  /**
    * The filter for the `exclude` tags.
    */
  val tagsNotFilter =
    if (request.tagsNot.isEmpty) ""
    else " AND id NOT IN (SELECT cardId FROM cardsTags WHERE LOWER(tag) IN ({lowerTagsNot}))"

  /**
    * The filter for the `searchTerm`.
    */
  val searchTermFilter = if (request.searchTerm.isEmpty) "" else " AND id IN ({searchTermIds}) "

  /**
    * Fills an sql statement with all known parameters.
    */
  def withParams(sql: SimpleSql[Row]): Future[SimpleSql[Row]] = {

    var result = sql;
    def addToResult(key: String, value: String) = { result = result.on(key -> value) }

    // Normal sql parameters from request
    result = result.on(
      "tagsFilterSqlSeq" -> SqlHelpers.tagsFilterSqlSeq(request.tags),
      "userId" -> request.userId,
      "pageSize" -> request.pageSize,
      "offset" -> (request.page - 1) * request.pageSize,
      "lowerTags" -> request.tags.map(_.toLowerCase),
      "lowerTagsNot" -> request.tagsNot.map(_.toLowerCase)
    )

    // Parameters from tags mini language
    tagsMiniLangParams.foreach { case (key, value) => addToResult(key, value) }

    // Parameters from ElasticSearch
    request.searchTerm match {
      case None => Future.successful(result)
      case Some(s) => cardElasticClient.findIds(s).map(ids => result.on("searchTermIds" -> ids))
    }
  }

  /**
    * The result for the TagsMiniLang parsing, if a query is defined.
    */
  lazy val tagsMiniLangResult = request.query.map { tagsMiniLangQuery =>
    TagsFilterMiniLang.parse(tagsMiniLangQuery) match {
      case Success(r) => r
      case Failure(e: ParsingError) => throw new TagsFilterMiniLangSyntaxError(e.message, e)
      case Failure(e) => throw e
    }
  }

  /**
    * An sql filter statement for the tags MiniLang query, if any.
    */
  lazy val tagsMiniLangFilter = tagsMiniLangResult match {
    case None => ""
    case Some(result) => s" AND id IN (${result.sql}) "
  }

  /**
    * The params to bind for the tagsMiniLangFilter
    */
  lazy val tagsMiniLangParams: Map[String, String] = tagsMiniLangResult match {
    case None => Map()
    case Some(result) => result.params
  }

}



/**
  * Some helper functions for generating sql.
  */
private object SqlHelpers {

  /**
    * A base from where statement.
    */
  val sqlFromWhereStatement: String = "FROM cards WHERE userId = {userId} "

  /**
    * A base get statement.
    */
  val sqlGetStatement: String = s"SELECT id, title, body, updatedAt, createdAt ${sqlFromWhereStatement}"

  /**
    * Returns a SeqParameter for the filter of including tags.
    */
  def tagsFilterSqlSeq(tags: List[String]): SeqParameter[String] = {
    SeqParameter(
      seq=tags.map(_.toLowerCase),
      sep=" AND ",
      pre="id IN (SELECT cardId FROM cardsTags WHERE LOWER(tag) = ",
      post=")"
    )
  }

}
