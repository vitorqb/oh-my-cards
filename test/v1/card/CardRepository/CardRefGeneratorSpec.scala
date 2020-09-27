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
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import v1.card.cardrepositorycomponents.CardRepositoryComponents
import v1.card.testUtils.ComponentsBuilder


class CardRefGeneratorSpec extends PlaySpec with MockitoSugar {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val cardFixtures = new CardFixtureRepository {
    val f1 = CardFixture("1", CardFormInput("ABC", None, None), DateTime.parse("2020-01-01"))
    def allFixtures() = Seq(f1)
  }

  def testContext(block: TestContext => Any) = {
    TestUtils.testDB { implicit db =>
      val tagsRepo = new TagsRepository
      val cardElasticClient = mock[CardElasticClient]
      val components = ComponentsBuilder(db).build()
      val repository = new CardRepository(components, tagsRepo, cardElasticClient)
      val testContext = TestContext(
        components,
        repository,
        tagsRepo,
        cardElasticClient,
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
      c.components.refGenerator.nextRef() mustEqual 1
    }

    "generate 2 if db has 1 card" in testContext { c =>
      c.createCardInDb(cardFixtures.f1)
      c.components.refGenerator.nextRef() mustEqual 2
    }
  }

}
