package v1.card.historytracker

import scala.language.postfixOps

import v1.card.models._
import anorm.{SQL}
import services.UUIDGeneratorLike
import java.sql.Connection
import anorm.RowParser
import anorm.SqlParser

/**
  * Main implementation for CardUpdateDataRepository, a repository for
  * persisting data specific to card updates.
  */
class CardUpdateDataRepository(uuidGenerator: UUIDGeneratorLike)
    extends CardUpdateDataRepositoryLike {

  /**
    * A parser for a field update of type String.
    */
  private val stringFieldUpdateParser: RowParser[StringFieldUpdate] = {
    import anorm._

    (
      SqlParser.str("fieldName") ~
        SqlParser.str("oldValue") ~
        SqlParser.str("newValue")
    ) map {
        case fieldName ~ oldValue ~ newValue =>
          StringFieldUpdate(fieldName, oldValue, newValue)
      }
  }

  override def create(
    coreEventId: String,
    oldData: CardData,
    newData: CardData
  )(
    implicit c: Connection
  ): Unit = {
    if (oldData.title != newData.title)
      createStringFieldUpdate(coreEventId, "title", oldData.title, newData.title)
    if (oldData.body != newData.body)
      createStringFieldUpdate(coreEventId, "body", oldData.body, newData.body)
    if (oldData.tags != newData.tags)
      createTagsFieldUpdate(coreEventId, "tags", oldData.tags, newData.tags)
  }

  override def getFieldsUpdates(
    coreEventId: String
  )(
    implicit c: Connection
  ): Seq[CardFieldUpdateLike] = {
    import anorm.SqlParser._

    val stringUpdates =
      SQL("""SELECT * FROM cardStringFieldUpdates WHERE coreEventId = {coreEventId}""")
        .on("coreEventId" -> coreEventId)
        .as(stringFieldUpdateParser.*)
    val tagsUpdates = TagsFieldUpdate.fromRows {
      SQL("""SELECT * FROM cardTagsFieldUpdates WHERE coreEventId = {coreEventId}""")
        .on("coreEventId" -> coreEventId)
        .as(str("fieldName") ~ str("oldOrNew") ~ str("tag") map (flatten) *)
    }
    stringUpdates ++ tagsUpdates
  }

  /**
    * Inserts into the db the data for an update for a string field.
    * 
    * @param coreEventId the id of the historical card event.
    * @param fieldName the name of the string field that is being updated
    * @param oldValue the old string value
    * @param newValue the new string value
    */
  protected def createStringFieldUpdate(
    coreEventId: String,
    fieldName: String,
    oldValue: String,
    newValue: String
  )(
    implicit c: Connection
  ): Unit =
    SQL("""INSERT INTO cardStringFieldUpdates(id, coreEventId, fieldName, oldValue, newValue)
           VALUES ({id}, {coreEventId}, {fieldName}, {oldValue}, {newValue})""")
      .on(
        "id" -> uuidGenerator.generate(),
        "coreEventId" -> coreEventId,
        "fieldName" -> fieldName,
        "oldValue" -> oldValue,
        "newValue" -> newValue
      )
      .execute()

  /**
    * Inserts into the db the data for an update for a tags field.
    * 
    * @param coreEventId the id of the historical card event.
    * @param fieldName the name of the tags field that is being updated
    * @param oldValue the old tags value
    * @param newValue the new tags value
    */
  protected def createTagsFieldUpdate(
    coreEventId: String,
    fieldName: String,
    oldValue: List[String],
    newValue: List[String]
  )(
    implicit c: Connection
  ): Unit = {
    val insertSql = SQL(
      """INSERT INTO cardTagsFieldUpdates(id, coreEventId, fieldName, oldOrNew, tag)
         VALUES ({id}, {coreEventId}, {fieldName}, {oldOrNew}, {tag})"""
    )
    oldValue.foreach { tag =>
      insertSql
        .on(
          "id" -> uuidGenerator.generate(),
          "coreEventId" -> coreEventId,
          "fieldName" -> fieldName,
          "oldOrNew" -> "OLD",
          "tag" -> tag
        )
        .executeInsert()
    }
    newValue.foreach { tag =>
      insertSql
        .on(
          "id" -> uuidGenerator.generate(),
          "coreEventId" -> coreEventId,
          "fieldName" -> fieldName,
          "oldOrNew" -> "NEW",
          "tag" -> tag
        )
        .executeInsert()
    }
  }
}
