package v1.card.historytracker

import org.scalatestplus.play.PlaySpec
import org.joda.time.DateTime
import v1.card.CardCreationContext
import v1.auth.User
import services.UUIDGeneratorLike
import play.api.db.Database
import test.utils.TestUtils
import services.CounterUUIDGenerator
import v1.card.CardUpdateContext

class CardHistoryRecorderSpec extends PlaySpec {

  val user = User("user", "user@user.user")
  val datetime = DateTime.parse("2019-12-31T10:10:10")

  case class TestContext(
    db: Database,
    uuidGenerator: UUIDGeneratorLike,
    tracker: CardHistoryTracker
  )

  def testContext(block: TestContext => Any): Any =
    TestUtils.testDB { db =>
      val uuidGenerator = new CounterUUIDGenerator
      val coreRepo = new HistoricalEventCoreRepository
      val tracker = new CardHistoryTracker(uuidGenerator, coreRepo)
      val context = TestContext(db, uuidGenerator, tracker)
      block(context)
    }

  "registerCreation" should {

    "create and retrieve a CardCreation" in testContext { c =>
      val context = new CardCreationContext(user, datetime, "1", 1)

      c.db.withTransaction { implicit conn =>
        c.tracker.registerCreation(context)
        c.tracker.getEvents("1") mustEqual Seq(CardCreation(datetime, "1", user.id))
      }
    }

  }

  "registerDeletion" should {

    "create and retrieve a CardDeletion" in testContext { c =>
      c.db.withTransaction { implicit conn =>
        val context = CardUpdateContext(user, datetime)
        c.tracker.registerDeletion("1", context)
        c.tracker.getEvents("1") mustEqual Seq(CardDeletion(datetime, "1", user.id))
      }
    }

  }

  "getEvents" should {

    "return an empty list of events" in testContext { c =>
      c.db.withTransaction { implicit conn =>
        c.tracker.getEvents("1") mustEqual Seq()
      }
    }

  }

}
