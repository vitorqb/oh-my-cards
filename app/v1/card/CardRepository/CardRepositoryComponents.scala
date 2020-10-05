package v1.card.cardrepositorycomponents

import play.api.db.Database
import services.UUIDGeneratorLike
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}
import services.referencecounter.ReferenceCounterLike

//!!!! TODO REMOVE ME

/**
  * A trait for the components needed by the card repository.
  */
trait CardRepositoryComponentsLike {
  val db: Database
  val uuidGenerator: UUIDGeneratorLike
  val refGenerator: ReferenceCounterLike
  val clock: SilhouetteClock
}

/**
  * An implementtion for the CardRepositoryComponentsLike
  */
class CardRepositoryComponents (
  val db: Database,
  val uuidGenerator: UUIDGeneratorLike,
  val refGenerator: ReferenceCounterLike,
  val clock: SilhouetteClock
) extends CardRepositoryComponentsLike
