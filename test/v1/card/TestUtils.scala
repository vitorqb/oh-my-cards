package v1.card.testUtils
import org.joda.time.DateTime
import play.api.db.Database
import services.UUIDGenerator
import services.Clock
import v1.auth.User
import org.mockito.Mockito._
import v1.card._
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
  val db: Database,
  val uuidGenerator: UUIDGenerator,
  val cardRefGenerator: CardRefGeneratorLike,
  val cardRepo: CardRepository,
  val tagsRepo: TagsRepository,
  val cardElasticClient: CardElasticClient,
  val clock: Clock,
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
    when(uuidGenerator.generate).thenReturn(id)
    when(clock.now()).thenReturn(now)
    val result = cardRepo.create(formInput, user).get
    reset(uuidGenerator)
    reset(clock)
    result
  }

  def createCardInDb(cardFixture: CardFixture): String = {
    createCardInDb(cardFixture.formInput, cardFixture.id, cardFixture.datetime)
  }
}
