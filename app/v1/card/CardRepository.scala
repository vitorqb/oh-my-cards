package v1.card

import javax.inject.Inject
import java.util.UUID.randomUUID
import scala.util.{Try,Success,Failure}
import anorm.{SQL,RowParser,Macro}
import play.api.db.Database
import v1.auth.User
import services.UUIDGenerator
import anorm.`package`.SqlStringInterpolation
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

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
final case class CardData(id: Option[String], title: String, body: String)

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
  uuidGenerator: UUIDGenerator)(
  implicit val ec: ExecutionContext) extends CardRepository {

  private val cardDataParser: RowParser[CardData] = Macro.namedParser[CardData]

  def create(cardData: CardData, user: User): Try[String] = {
    if (cardData.id.isDefined) {
      Failure(new Exception("Id for create should be null!"))
    } else {
      val id = uuidGenerator.generate
      db.withConnection { implicit c =>
        SQL("INSERT INTO cards(id, userId, title, body) VALUES ({id}, {userId}, {title}, {body});")
          .on("id" -> id, "userId" -> user.id, "title" -> cardData.title, "body" -> cardData.body)
          .executeInsert()
        Success(id)
      }
    }
  }

  def get(id: String, user: User): Option[CardData] = db.withConnection { implicit c =>
    SQL("SELECT id, title, body FROM cards WHERE id = {id} AND userId = {userId}")
      .on("id" -> id, "userId" -> user.id)
      .as(cardDataParser.*)
      .headOption
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
        Success(())
      }
    }
  }

  /**
    * Updates a card.
    */
  def update(data: CardData, user: User): Future[Try[Unit]] = Future { Try { db.withConnection {
    implicit c =>
    SQL"""
       UPDATE cards SET title=${data.title}, body=${data.body} 
       WHERE id=${data.id} AND userId=${user.id}
       """
      .executeUpdate()
  }}}
}
