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
sealed trait CardFieldUpdateLike {

  /**
    * The Name of the field being updated.
    */
  val fieldName: String
}

/**
  * Specific types of card field updates.
  */
case class StringFieldUpdate(val fieldName: String, val oldValue: String, val newValue: String)
    extends CardFieldUpdateLike {}

case class TagsFieldUpdate(
  val fieldName: String,
  val oldValue: Seq[String] = Seq(),
  val newValue: Seq[String] = Seq()
) extends CardFieldUpdateLike {

  def appendOldTag(t: String) = copy(oldValue=oldValue :+ t)
  def appendNewTag(t: String) = copy(newValue=newValue :+ t)

}

object TagsFieldUpdate {

  /**
    * Creates TagFieldUpdates from tupples representing (fieldName, oldOrNew, tag) 
    */
  def fromRows(rows: Seq[(String, String, String)]): Seq[TagsFieldUpdate] =
    rows.foldLeft(Seq(): Seq[TagsFieldUpdate]) { (acc, row) =>
      val fieldName = row._1
      val oldOrNew = row._2
      val tag = row._3
      val update = acc.find(_.fieldName == fieldName).getOrElse(TagsFieldUpdate(fieldName))
      val newUpdate = oldOrNew match {
        case "OLD" => update.appendOldTag(tag)
        case "NEW" => update.appendNewTag(tag)
        case _ => throw new RuntimeException(f"Can not parse row: {row}")
      }
      acc.indexWhere(_.fieldName == fieldName) match {
        case -1 => acc :+ newUpdate
        case x  => acc.updated(x, newUpdate)
      }
    }

}
