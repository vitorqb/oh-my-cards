package v1.card.historytracker

import v1.card.{
  CardHistoryRecorderLike,
  CardCreationContext,
  CardUpdateContext,
  CardEventContextLike,
  CardData
}
import services.UUIDGeneratorLike
import java.sql.Connection
import org.joda.time.DateTime
import v1.card.historytrackerhandler.CardHistoryTrackerLike

/**
  * The core data for a historical event, share among all events.
  */
case class CoreHistoricalEventData(
  id: String,
  datetime: DateTime,
  cardId: String,
  userId: String,
  eventType: String
)

/**
  * A trait for the repository of the core data of a historical event.
  */
trait HistoricalEventCoreRepositoryLike {

  /**
    * Saves the new core data for a Historical Event
    * 
    * @param id the id for the new HistoricalEvent.
    * @param cardId the id of the card being updated.
    * @param context the context for the card creation.
    * @param eventType the type of event being created.
    * 
    */
  def create(
    id: String,
    cardId: String,
    context: CardEventContextLike,
    eventType: String
  )(
    implicit c: Connection
  ): Unit

  /**
    * List all events for a card.
    * 
    * @param cardId the id of the card for which events are queried
    */
  def list(cardId: String)(implicit c: Connection): Seq[CoreHistoricalEventData]

}

/**
  * A trait for the repository responsible for creating card update events.
  */
trait CardUpdateDataRepositoryLike {

  /**
    * Creates a new card update from the old and new card data.
    * 
    * @param coreEventId the id of the HistoricalEvent associated with
    *   the update to be created
    */
  def create(
    coreEventId: String,
    oldData: CardData,
    newData: CardData
  )(
    implicit c: Connection
  ): Unit

  /**
    * Gets all field updates for a HistoricalEvent of type update.
    * 
    * @param coreEventId the id of the historical event core data.
    */
  def getFieldsUpdates(
    coreEventId: String
  )(
    implicit c: Connection
  ): Seq[CardFieldUpdateLike]
}

/**
  * Service responsible for keeping track of a card history.
  */
class CardHistoryTracker(
  uuidGenerator: UUIDGeneratorLike,
  coreRepo: HistoricalEventCoreRepositoryLike,
  updateRepo: CardUpdateDataRepositoryLike
) extends CardHistoryRecorderLike
  with CardHistoryTrackerLike
{

  override def registerCreation(context: CardCreationContext)(implicit c: Connection): Unit = {
    val id = uuidGenerator.generate()
    coreRepo.create(id, context.id, context, EventTypes.creation)
  }

  override def registerDeletion(context: CardUpdateContext)(implicit c: Connection): Unit = {
    val id = uuidGenerator.generate()
    coreRepo.create(id, context.oldData.id, context, EventTypes.deletion)
  }

  override def registerUpdate(
    newData: CardData,
    context: CardUpdateContext
  )(
    implicit c: Connection
  ): Unit = {
    val id = uuidGenerator.generate()
    coreRepo.create(id, context.oldData.id, context, EventTypes.update)
    updateRepo.create(id, context.oldData, newData)
  }

  def getEvents(cardId: String)(implicit c: Connection): Seq[CardHistoricalEventLike] =
    coreRepo.list(cardId) map { 
      case CoreHistoricalEventData(id, datetime, cardId, userId, EventTypes.creation) =>
        CardCreation(datetime, cardId, userId)
      case CoreHistoricalEventData(id, datetime, cardId, userId, EventTypes.deletion) =>
        CardDeletion(datetime, cardId, userId)
      case CoreHistoricalEventData(id, datetime, cardId, userId, EventTypes.update) =>
        CardUpdate(datetime, cardId, userId, updateRepo.getFieldsUpdates(id))
      case x =>
        throw new RuntimeException(f"Unknown historical card event type for x")
    }

}

/**
  * An enum-like with the types of events.
  */
object EventTypes {
  val creation = "creation"
  val deletion = "deletion"
  val update = "update"
}
