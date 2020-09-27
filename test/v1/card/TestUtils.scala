package v1.card.testUtils
import org.joda.time.DateTime
import play.api.db.Database
import services.UUIDGenerator
import services.Clock
import v1.auth.User
import org.mockito.Mockito._
import v1.card._
import v1.card.CardRefGenerator.CardRefGenerator
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import v1.card.cardrepositorycomponents.CardRepositoryComponents
import org.mockito.MockitoSugar
import v1.card.CardRefGenerator.CardRefGeneratorLike

/**
  * A data class for the data that a fixture of a card needs.
  */
case class CardFixture(val id: String, val formInput: CardFormInput, val datetime: DateTime)

/**
  * A trait for a Repository of card fixtures
  */
trait CardFixtureRepository {
  def allFixtures(): Seq[CardFixture]
}

/**
  * All fixtures for the test
  */
case class TestContext(
  val components: CardRepositoryComponentsLike,
  val cardRepo: CardDataRepository,
  val tagsRepo: TagsRepositoryLike,
  val cardElasticClient: CardElasticClient,
  val cardFixtures: CardFixtureRepository,
  val user: User
) {

  /**
    * Save all card fixtures to the db
    */
  def saveCardsToDb(): Unit = {
    for (cardFixture <- cardFixtures.allFixtures) yield {
      createCardInDb(cardFixture)
    }
  }

  /**
    * Performs the creation of a card.
    */
  def createCardInDb(formInput: CardFormInput, id: String, now: DateTime): String = {
    when(components.uuidGenerator.generate).thenReturn(id)
    when(components.clock.now()).thenReturn(now)
    val result = cardRepo.create(formInput, user).get
    reset(components.uuidGenerator)
    reset(components.clock)
    result
  }

  def createCardInDb(cardFixture: CardFixture): String = {
    createCardInDb(cardFixture.formInput, cardFixture.id, cardFixture.datetime)
  }
}

/**
  * A fixture factory for the CardRepositoryComponentsLike that defaults to mock everything.
  */
case class ComponentsBuilder(
  val db: Database,
  val uuidGenerator: Option[UUIDGenerator] = None,
  val refGenerator: Option[CardRefGeneratorLike] = None,
  val clock: Option[Clock] = None
) extends MockitoSugar {

  def withUUIDGenerator(uuidGenerator: UUIDGenerator) = copy(uuidGenerator=Some(uuidGenerator))
  def withRefGenerator(refGenerator: CardRefGeneratorLike) = copy(refGenerator=Some(refGenerator))
  def withClock(clock: Clock) = copy(clock=Some(clock))

  def build(): CardRepositoryComponentsLike =
    new CardRepositoryComponents(
      db,
      uuidGenerator.getOrElse(mock[UUIDGenerator]),
      refGenerator.getOrElse(new CardRefGenerator(db)),
      clock.getOrElse(mock[Clock])
    )

}
