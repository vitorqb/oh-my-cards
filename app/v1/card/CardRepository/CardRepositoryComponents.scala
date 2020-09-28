package v1.card.cardrepositorycomponents

import play.api.db.Database
import services.UUIDGenerator
import v1.card.CardRefGenerator.CardRefGeneratorLike
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}


/**
  * A trait for the components needed by the card repository.
  */
trait CardRepositoryComponentsLike {
  val db: Database
  val uuidGenerator: UUIDGenerator
  val refGenerator: CardRefGeneratorLike
  val clock: SilhouetteClock
}

/**
  * An implementtion for the CardRepositoryComponentsLike
  */
class CardRepositoryComponents (
  val db: Database,
  val uuidGenerator: UUIDGenerator,
  val refGenerator: CardRefGeneratorLike,
  val clock: SilhouetteClock
) extends CardRepositoryComponentsLike
