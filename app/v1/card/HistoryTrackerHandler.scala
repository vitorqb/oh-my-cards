package v1.card

import scala.concurrent.Future
import v1.card.historytracker.CardHistoricalEventLike
import java.sql.Connection
import play.api.db.Database
import scala.concurrent.ExecutionContext
import play.api.libs.json.Json
import v1.card.repository.UserCardPermissionManagerLike
import v1.auth.User
import v1.card.exceptions.CardDoesNotExist

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
  def get(id: String, user: User): Future[CardHistoryResource]
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
    tracker: CardHistoryTrackerLike,
    permissionManager: UserCardPermissionManagerLike,
)(implicit
    ec: ExecutionContext
) extends HistoryTrackerHandlerLike {

  def get(id: String, user: User): Future[CardHistoryResource] = {
    implicit val c = db.getConnection()
    permissionManager.hasPermission(user, id).map {
      case true  => CardHistoryResource(tracker.getEvents(id))
      case false => throw new CardDoesNotExist
    } andThen {
      case _ => c.close()
    }
  }

}
