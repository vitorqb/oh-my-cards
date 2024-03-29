package v1.card.datarepository

import anorm.{SQL, RowParser, SqlParser}
import v1.auth.User
import anorm.`package`.SqlStringInterpolation
import java.sql.Connection
import org.joda.time.DateTime
import anorm.JodaParameterMetaData._
import v1.card.repository.{CardDataRepositoryLike}
import v1.card.models._
import v1.card.exceptions._

/**
  * An implementation for a card repository.
  */
class CardDataRepository extends CardDataRepositoryLike {

  /**
    * The statement used to get a card.
    */
  val sqlGetStatement: String =
    s"SELECT id, title, body, updatedAt, createdAt, ref FROM cards "

  /**
    * A parser for CardData.
    */
  private def cardDataParser(): RowParser[CardData] = {
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

  def create(data: CardCreateData, context: CardCreationContext)(implicit
      c: Connection
  ): Unit = {
    SQL(
      """INSERT INTO cards(id, userId, title, body, createdAt, updatedAt, ref)
             VALUES ({id}, {userId}, {title}, {body}, {now}, {now}, {ref})"""
    ).on(
      "id" -> context.id,
      "userId" -> context.user.id,
      "title" -> data.title,
      "body" -> data.body,
      "now" -> context.now,
      "ref" -> context.ref
    ).executeInsert()
  }

  def get(id: String, user: User)(implicit c: Connection): Option[CardData] =
    SQL(s"${sqlGetStatement} WHERE userId = {userId} AND id = {id}")
      .on("id" -> id, "userId" -> user.id)
      .as(cardDataParser.*)
      .headOption

  def getIdFromRef(ref: Int)(implicit c: Connection): Option[String] =
    SQL("SELECT id FROM cards WHERE ref = {ref}")
      .on("ref" -> ref)
      .as(SqlParser.str("id").*)
      .headOption

  /**
    * Finds a list of cards for a given user.
    */
  def find(idsResult: IdsFindResult)(implicit c: Connection): FindResult = {
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
  def update(data: CardData, context: CardUpdateContext)(implicit
      c: Connection
  ): Unit =
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
       SELECT DISTINCT tag
       FROM cardsTags
       JOIN cards ON cards.id = cardId
       WHERE cards.userId = ${user.id}
    """.as(str(1).*)
  }
}
