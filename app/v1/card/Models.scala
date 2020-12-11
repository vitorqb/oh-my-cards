package v1.card.models

import org.joda.time.DateTime
import v1.auth.User

/**
  * The basic data needed to create a card.
  */
final case class CardCreateData(title: String, body: String, tags: List[String])

/**
  * The basic data needed to list cards.
  */
final case class CardListData(
    page: Int,
    pageSize: Int,
    userId: String,
    tags: List[String],
    tagsNot: List[String],
    query: Option[String],
    searchTerm: Option[String]
)

/**
  * The basic representation for a Card.
  */
final case class CardData(
    id: String,
    title: String,
    body: String,
    tags: List[String],
    createdAt: Option[DateTime] = None,
    updatedAt: Option[DateTime] = None,
    ref: Int
)

/**
  * A genetic context for a card event, like update, create or delete.
  * @param user the user that is creating the card.
  * @param now the current datetime to consider.
  */
trait CardEventContextLike {
  val user: User
  val now: DateTime
}

/**
  * The context for a card creation.
  *
  * @param user the user that is creating the card.
  * @param now the current datetime to consider.
  * @param id the id to use for the new card.
  * @param ref the ref to use for the new card.
  */
case class CardCreationContext(
    user: User,
    now: DateTime,
    id: String,
    ref: Int
) extends CardEventContextLike {

  def genCardData(data: CardCreateData) =
    CardData(id, data.title, data.body, data.tags, Some(now), Some(now), ref)
}

/**
  * The context for a card update.
  *
  * @param user the user that is creating the card.
  * @param now the current datetime to consider.
  */
case class CardUpdateContext(
    user: User,
    now: DateTime,
    oldData: CardData
) extends CardEventContextLike

/**
  * A paged result with the matched ids for a query to find cards.
  *
  * @param ids the ids of the cards that are part of this page of results
  * @param countOfItems the total number of items matched
  */
final case class IdsFindResult(ids: Seq[String], countOfItems: Integer)

/**
  * The result of for a query to find cards.
  *
  * @param cards the cards on this page of results.
  * @param countOfItems the total number of items matched.
  */
final case class FindResult(cards: Seq[CardData], countOfItems: Int)
