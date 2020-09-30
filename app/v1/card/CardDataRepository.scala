package v1.card

import javax.inject.Inject
import java.util.UUID.randomUUID
import scala.util.{Try,Success,Failure}
import anorm.{SQL,RowParser,Macro,SqlParser,SeqParameter}
import play.api.db.Database
import v1.auth.User
import anorm.`package`.SqlStringInterpolation
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.sql.Connection
import anorm.SimpleSql
import anorm.Row
import org.joda.time.DateTime
import anorm.JodaParameterMetaData._
import v1.card.CardRefGenerator.CardRefGeneratorLike

/**
  * An implementation for a card repository.
  */
class CardDataRepository extends CardDataRepositoryLike {

  /**
    * The statement used to get a card.
    */
  val sqlGetStatement: String = s"SELECT id, title, body, updatedAt, createdAt, ref FROM cards "

  /**
    * A parser for CardData.
    */
  private def cardDataParser()(implicit c: Connection): RowParser[CardData] = {
    import anorm._

    (
      SqlParser.str("id") ~
        SqlParser.str("title") ~
        SqlParser.str("body") ~
        SqlParser.get[Option[DateTime]]("createdAt") ~
        SqlParser.get[Option[DateTime]]("updatedAt") ~
        SqlParser.int("ref")
    ) map {
      case id ~ title ~ body ~ createdAt ~ updatedAt ~ ref =>
        CardData(id, title, body, List(), createdAt, updatedAt, ref)
    }
  }

  def create(
    cardFormInput: CardFormInput,
    context: CardCreationContext
  )(implicit c: Connection): Unit = {
    SQL(
      """INSERT INTO cards(id, userId, title, body, createdAt, updatedAt, ref)
             VALUES ({id}, {userId}, {title}, {body}, {now}, {now}, {ref})"""
    ).on(
      "id" -> context.id,
      "userId" -> context.user.id,
      "title" -> cardFormInput.getTitle(),
      "body" -> cardFormInput.getBody(),
      "now" -> context.now,
      "ref" -> context.ref
    ).executeInsert()
  }

  def get(id: String, user: User)(implicit c: Connection): Option[CardData] =
    SQL(s"${sqlGetStatement} WHERE userId = {userId} AND id = {id}")
      .on("id" -> id, "userId" -> user.id)
      .as(cardDataParser.*)
      .headOption

  /**
    * Finds a list of cards for a given user.
    */
  def find(request: CardListRequest, idsResult: IdsFindResult)(implicit c: Connection): FindResult = {
    val ids = idsResult.ids
    val cards = SQL(s"${sqlGetStatement} WHERE id IN ({ids})")
      .on("ids" -> ids)
      .as(cardDataParser.*)
      .sortWith((x, y) => ids.indexOf(x.id) <= ids.indexOf(y.id))
    FindResult(cards, idsResult.countOfItems)
  }

  /**
    * Deletes a card by it's id.
    */
  def delete(id: String, user: User)(implicit c: Connection): Unit =
    get(id, user) match {
      case None => throw new CardDoesNotExist
      case Some(_) =>
        SQL(s"DELETE FROM cards WHERE userId = {userId} AND id = {id}")
          .on("id" -> id, "userId" -> user.id)
          .executeUpdate()
    }

  /**
    * Updates a card.
    */
  def update(data: CardData, context: CardUpdateContext)(implicit c: Connection): Unit =
    SQL("""
        UPDATE cards SET title={title}, body={body}, updatedAt={now}
        WHERE id={id} AND userId={userId}
       """)
      .on(
        "title" -> data.title,
        "body" -> data.body,
        "id" -> data.id,
        "userId" -> context.user.id,
        "now" -> context.now
      )
      .executeUpdate()

  /**
    * Returns all tags for a given user.
    */
  def getAllTags(user: User)(implicit c: Connection): List[String] = {
    import anorm.SqlParser._
    SQL"""
       SELECT tag
       FROM cardsTags
       JOIN cards ON cards.id = cardId
       WHERE cards.userId = ${user.id}
    """.as(str(1).*)
  }
}
