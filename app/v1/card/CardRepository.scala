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

/**
  * Custom exception signaling that an error ocurred when parsing the tags mini lang.
  */
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
  * The result of a find query.
  */
final case class FindResult(cards: Seq[CardData], countOfItems: Int)

object FindResult {

  /**
    * Alternative constructor from the results of the query for the IDs and the query for
    * the data.
    */
  def fromQueryResults(
    cardData: Seq[CardData],
    idsResult: CardElasticIdFinder.Result
  ): FindResult = {
    val cardDataById = cardData.map(x => (x.id.get, x)).toMap
    val sortedCardData = idsResult.ids.map(x => cardDataById.get(x)).flatten
    FindResult(sortedCardData, idsResult.countOfIds)
  }

}

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

  /**
    * The statement used to get a card.
    */
  val sqlGetStatement: String = s"SELECT id, title, body, updatedAt, createdAt FROM cards "

  /**
    * A parser for CardData that also queries for the tags from TagsRepo.
    */
  private def cardDataParser()(implicit c: Connection): RowParser[CardData] = {
    import anorm._

    (
      SqlParser.str("id") ~
        SqlParser.str("title") ~
        SqlParser.str("body") ~
        SqlParser.get[Option[DateTime]]("createdAt") ~
        SqlParser.get[Option[DateTime]]("updatedAt")
    ) map {
      case id ~ title ~ body ~ createdAt ~ updatedAt =>
        CardData(Some(id), title, body, tagsRepo.get(id), createdAt, updatedAt)
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
    SQL(s"${sqlGetStatement} WHERE userId = {userId} AND  id = {id}")
      .on("id" -> id, "userId" -> user.id)
      .as(cardDataParser.*)
      .headOption
  }

  /**
    * Finds a list of cards for a given user.
    */
  def find(request: CardListRequest): Future[FindResult] = {
    for {
      esIdsResult <- cardElasticClient.findIds(request)
      query = SQL(s"${sqlGetStatement} WHERE id IN ({ids})").on("ids" -> esIdsResult.ids)
    } yield {
      db.withConnection { implicit c =>
        val sqlQueryResult = query.as(cardDataParser.*)
        FindResult.fromQueryResults(sqlQueryResult, esIdsResult)
      }
    }
  }

  /**
    * Deletes a card by it's id.
    */
  def delete(id: String, user: User): Future[Try[Unit]] = Future {
    get(id, user) match {
      case None => Failure(new CardDoesNotExist)
      case Some(_) => db.withTransaction { implicit c =>
        SQL(s"DELETE FROM cards WHERE userId = {userId} AND id = {id}")
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
