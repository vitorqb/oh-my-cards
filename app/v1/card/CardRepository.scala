package v1.card

import javax.inject.Inject
import java.util.UUID.randomUUID
import scala.util.{Try,Success,Failure}
import anorm.{SQL,RowParser,Macro,SqlParser}
import play.api.db.Database
import v1.auth.User
import services.UUIDGenerator
import anorm.`package`.SqlStringInterpolation
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.sql.Connection

/**
  * Custom exception signaling that a card does not exist.
  */
final case class CardDoesNotExist(
  private val message: String = "The required card does not exist.",
  private val cause: Throwable = None.orNull
) extends Exception(message, cause)

/**
  * The data for a Card.
  */
final case class CardData(id: Option[String], title: String, body: String, tags: List[String])

/**
  * An interface for a card repository.
  */
trait CardRepository {
  def create(cardData: CardData, user: User): Try[String]
  def get(id: String, user: User): Option[CardData]
  def find(r: CardListRequest): Iterable[CardData]
}

/**
  * An implementation for a card repository.
  */
class CardRepositoryImpl @Inject()(
  db: Database,
  uuidGenerator: UUIDGenerator,
  tagsRepo: TagsRepository)(
  implicit val ec: ExecutionContext) extends CardRepository {

  private val cardDataParser: RowParser[CardData] = {
    import anorm._
    SqlParser.str("id") ~ SqlParser.str("title") ~ SqlParser.str("body") map {
      case id ~ title ~ body => CardData(Some(id), title, body, List())
    }
  }

  def create(cardData: CardData, user: User): Try[String] = {
    if (cardData.id.isDefined) {
      Failure(new Exception("Id for create should be null!"))
    } else {
      val id = uuidGenerator.generate
      db.withTransaction { implicit c =>
        SQL("INSERT INTO cards(id, userId, title, body) VALUES ({id}, {userId}, {title}, {body});")
          .on("id" -> id, "userId" -> user.id, "title" -> cardData.title, "body" -> cardData.body)
          .executeInsert()
        tagsRepo.create(id, cardData.tags)
        Success(id)
      }
    }
  }

  def get(id: String, user: User): Option[CardData] = db.withConnection { implicit c =>
    SQL("SELECT id, title, body FROM cards WHERE id = {id} AND userId = {userId}")
      .on("id" -> id, "userId" -> user.id)
      .as(cardDataParser.*)
      .headOption
      .map(_.copy(tags=tagsRepo.get(id)))
  }

  /**
    * Finds a list of cards for a given user.
    */
  def find(request: CardListRequest): Iterable[CardData] = db.withConnection { implicit c =>
    SQL(s"""
      | SELECT id, title, body FROM cards
      | WHERE userId = {userId}
      | ORDER BY id DESC
      | LIMIT ${request.pageSize}
      | OFFSET ${(request.page - 1) * request.pageSize}
      | """.stripMargin)
      .on("userId" -> request.userId)
      .as(cardDataParser.*)
      .map(cardData => cardData.copy(tags=tagsRepo.get(cardData.id.get)))
  }

  /**
    * Returns the total number of items matching a request for a list of cards.
    */
  def countItemsMatching(request: CardListRequest): Int = db.withConnection { implicit c =>
    val parser = anorm.SqlParser.get[Int]("count").*
      SQL"""
        SELECT COUNT(*) AS count FROM cards WHERE userId = ${request.userId}
       """
      .as(parser)
      .headOption
      .getOrElse(0)
  }

  /**
    * Deletes a card by it's id.
    */
  def delete(id: String, user: User): Future[Try[Unit]] = Future {
    get(id, user) match {
      case None => Failure(new CardDoesNotExist)
      case Some(_) => db.withConnection { implicit c =>
        SQL"""DELETE FROM cards where userId = ${user.id} AND id = ${id}""".executeUpdate
        tagsRepo.delete(id)
        Success(())
      }
    }
  }

  /**
    * Updates a card.
    */
  def update(data: CardData, user: User): Future[Try[Unit]] = Future { Try { db.withTransaction {
    implicit c =>
    SQL"""
       UPDATE cards SET title=${data.title}, body=${data.body} 
       WHERE id=${data.id} AND userId=${user.id}
       """
      .executeUpdate()
    tagsRepo.delete(data.id.get)
    tagsRepo.create(data.id.get, data.tags)
  }}}
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
