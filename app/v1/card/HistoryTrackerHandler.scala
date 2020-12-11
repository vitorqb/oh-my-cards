package v1.card.historytrackerhandler

import scala.concurrent.Future
import v1.card.historytracker.CardHistoricalEventLike
import java.sql.Connection
import play.api.db.Database
import scala.concurrent.ExecutionContext
import play.api.libs.json.Json

/**
  * A trait defining the base functionalities for the Card History Tracker.
  */
trait CardHistoryTrackerLike {
  def getEvents(cardId: String)(implicit
      c: Connection
  ): Seq[CardHistoricalEventLike]
}

/**
  * A trait for the handler that knows how to handle requests regarding a card's history.
  */
trait HistoryTrackerHandlerLike {
  def get(id: String): Future[CardHistoryResource]
}

/**
  * The outcome format for the get history call.
  */
case class CardHistoryResource(history: Seq[CardHistoricalEventLike])
object CardHistoryResource {
  implicit val writes = Json.writes[CardHistoryResource]
}

/**
  * The implementation of the handler.
  */
class HistoryTrackerHandler(
    db: Database,
    tracker: CardHistoryTrackerLike
)(implicit
    ec: ExecutionContext
) extends HistoryTrackerHandlerLike {
  def get(id: String): Future[CardHistoryResource] =
    Future {
      db.withConnection { implicit c =>
        CardHistoryResource(tracker.getEvents(id))
      }
    }
}
