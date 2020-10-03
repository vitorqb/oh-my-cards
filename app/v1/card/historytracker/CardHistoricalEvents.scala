package v1.card.historytracker

import org.joda.time.DateTime


/**
  * Represents a historical event for a card (creation, update, deletion)
  */
sealed trait CardHistoricalEventLike {
  val datetime: DateTime
  val cardId: String
  val userId: String
}

case class CardCreation(
  val datetime: DateTime,
  val cardId: String,
  val userId: String
) extends CardHistoricalEventLike {}

case class CardDeletion(
  val datetime: DateTime,
  val cardId: String,
  val userId: String
) extends CardHistoricalEventLike {}

case class CardUpdate(
  val datetime: DateTime,
  val cardId: String,
  val userId: String,
  val fieldsUpdates: Seq[CardFieldUpdateLike]
) extends CardHistoricalEventLike {}

/**
  * Represents an update to a single card field. Part of a CardUpdate.
  */
trait CardFieldUpdateLike {

  /**
    * The Name of the field being updated.
    */
  val fieldName: String

  /**
    * A string representation of the update.
    */
  def stringRepr(): String
}

/**
  * Specific types of card field updates.
  */
case class StringFieldUpdate(val fieldName: String, val oldValue: String, val newValue: String)
    extends CardFieldUpdateLike {

  override def stringRepr(): String = ???

}
