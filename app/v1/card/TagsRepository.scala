package v1.card.tagsrepository

import v1.card.TagsRepositoryLike
import java.sql.Connection
import anorm.SqlParser
import anorm.`package`.SqlStringInterpolation
import v1.card.CardData

/**
  * Helper object manage cards tags.
  */
class TagsRepository extends TagsRepositoryLike {

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

  /**
    * Queries and fills the card with it's tags.
    */
  def fill(card: CardData)(implicit c: Connection): CardData = card.copy(tags=get(card.id))
}
