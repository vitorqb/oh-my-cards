package v1.card.CardRefGenerator

import scala.language.reflectiveCalls

import v1.card._

import org.scalatestplus.play._
import v1.card.testUtils.TestContext
import test.utils.TestUtils
import services.UUIDGenerator
import v1.card.CardRepository
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import services.Clock
import scala.concurrent.ExecutionContext
import v1.card.testUtils.CardFixtureRepository
import v1.card.testUtils.CardFixture
import org.joda.time.DateTime
import v1.auth.User


class CardRefGeneratorSpec extends PlaySpec with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val cardFixtures = new CardFixtureRepository {
    val f1 = CardFixture("1", CardFormInput("ABC", None, None), DateTime.parse("2020-01-01"))
    def allFixtures() = Seq(f1)
  }

  def testContext(block: TestContext => Any) = {
    TestUtils.testDB { implicit db =>
      val uuidGenerator = mock[UUIDGenerator]
      val tagsRepo = new TagsRepository
      val cardElasticClient = mock[CardElasticClient]
      val clock = mock[Clock]
      val cardRefGenerator = new CardRefGenerator(db)
      val repository = new CardRepository(db, uuidGenerator, cardRefGenerator, tagsRepo, cardElasticClient, clock)
      val testContext = TestContext(
        db,
        uuidGenerator,
        cardRefGenerator,
        repository,
        tagsRepo,
        cardElasticClient,
        clock,
        cardFixtures,
        new User("userId", "user@email.com")
      )
      try {
        block(testContext)
      } finally {
        TestUtils.cleanupDb(db)
      }
    }
  }

  "nextRef" should {
    "generate 1 if db is empty" in testContext { c =>
      c.cardRefGenerator.nextRef() mustEqual 1
    }

    "generate 2 if db has 1 card" in testContext { c =>
      c.createCardInDb(cardFixtures.f1)
      c.cardRefGenerator.nextRef() mustEqual 2
    }
  }

}
