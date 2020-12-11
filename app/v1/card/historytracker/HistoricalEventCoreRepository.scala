package v1.card.historytracker

import v1.card.models._
import anorm.{SQL}
import anorm.JodaParameterMetaData._
import java.sql.Connection
import anorm.RowParser
import anorm.SqlParser
import org.joda.time.DateTime

/**
  * The implementation for the CardHisotircalEvent repository, responsible for CRUD
  * over historical card events (creation, deletion, update).
  */
class HistoricalEventCoreRepository extends HistoricalEventCoreRepositoryLike {

  /**
    * A parser for a CardHistoricalEvent, comming from a db row.
    */
  private val cardHistoricalEventParser: RowParser[CoreHistoricalEventData] = {
    import anorm._

    (
      SqlParser.str("id") ~
        SqlParser.str("cardId") ~
        SqlParser.str("userId") ~
        SqlParser.get[DateTime]("datetime") ~
        SqlParser.str("eventType")
    ) map {
      case id ~ cardId ~ userId ~ datetime ~ eventType =>
        CoreHistoricalEventData(id, datetime, cardId, userId, eventType)
    }
  }

  override def create(
    id: String,
    cardId: String,
    context: CardEventContextLike,
    eventType: String
  )(
    implicit c: Connection
  ): Unit = {
    SQL("""INSERT INTO cardHistoricalEvents(id, cardId, userId, datetime, eventType)
           VALUES ({id}, {cardId}, {userId}, {datetime}, {eventType})""")
      .on(
        "id" -> id,
        "cardId" -> cardId,
        "userId" -> context.user.id,
        "datetime" -> context.now,
        "eventType" -> eventType
      )
      .executeInsert()
  }

  override def list(cardId: String)(implicit c: Connection): Seq[CoreHistoricalEventData] =
    SQL("""SELECT id, cardId, userId, datetime, eventType
           FROM cardHistoricalEvents
           WHERE cardId = {cardId} """)
      .on("cardId" -> cardId)
      .as(cardHistoricalEventParser.*)

}
