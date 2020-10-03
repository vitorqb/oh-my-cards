package v1.card.historytracker

import v1.card.CardData
import anorm.{SQL}
import services.UUIDGeneratorLike
import java.sql.Connection
import anorm.RowParser

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
  }

  override def getFieldsUpdates(
    coreEventId: String
  )(
    implicit c: Connection
  ): Seq[CardFieldUpdateLike] =
    SQL("""SELECT * FROM cardStringFieldUpdates WHERE coreEventId = {coreEventId}""")
      .on("coreEventId" -> coreEventId)
      .as(stringFieldUpdateParser.*)

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
}
