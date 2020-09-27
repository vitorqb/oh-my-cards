package v1.card.cardrepositorycomponents

import play.api.db.Database
import services.UUIDGenerator
import v1.card.CardRefGenerator.CardRefGeneratorLike
import v1.card.TagsRepository
import services.Clock

/**
  * A trait for the components needed by the card repository.
  */
trait CardRepositoryComponentsLike {
  val db: Database
  val uuidGenerator: UUIDGenerator
  val refGenerator: CardRefGeneratorLike
  val clock: Clock
}

/**
  * An implementtion for the CardRepositoryComponentsLike
  */
class CardRepositoryComponents (
  val db: Database,
  val uuidGenerator: UUIDGenerator,
  val refGenerator: CardRefGeneratorLike,
  val clock: Clock
) extends CardRepositoryComponentsLike
