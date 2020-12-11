package v1.card.historytracker

import org.scalatestplus.play.PlaySpec
import org.joda.time.DateTime
import v1.auth.User
import play.api.db.Database
import test.utils.TestUtils
import services.CounterUUIDGenerator
import v1.card.repository.CardCreationContext
import v1.card.repository.CardUpdateContext
import v1.card.repository.CardData

class CardHistoryRecorderSpec extends PlaySpec {

  val user = User("user", "user@user.user")
  val datetime = DateTime.parse("2019-12-31T10:10:10")

  case class TestContext(db: Database, tracker: CardHistoryTracker)

  def testContext(block: TestContext => Any): Any =
    TestUtils.testDB { db =>
      val coreRepo = new HistoricalEventCoreRepository
      val updateRepo = new CardUpdateDataRepository(new CounterUUIDGenerator)
      val tracker = new CardHistoryTracker(new CounterUUIDGenerator, coreRepo, updateRepo)
      val context = TestContext(db, tracker)
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
        val oldData = CardData("1", "A", "B", List(), None, None, 1)
        val context = CardUpdateContext(user, datetime, oldData)
        c.tracker.registerDeletion(context)
        c.tracker.getEvents("1") mustEqual Seq(CardDeletion(datetime, "1", user.id))
      }
    }

  }

  "registerUpdate" should {

    "create and retrieve a CardUpdate" in testContext { c =>
      c.db.withTransaction { implicit conn =>
        val oldData = CardData("1", "old", "OLD", List("A"), None, None, 1)
        val context = CardUpdateContext(user, datetime, oldData)
        val newData = oldData.copy(title="new", body="NEW", tags=List("B"))

        c.tracker.registerUpdate(newData, context)

        val expUpdate = CardUpdate(
          datetime,
          "1",
          user.id,
          Seq(
            StringFieldUpdate("title", "old", "new"),
            StringFieldUpdate("body", "OLD", "NEW"),
            TagsFieldUpdate("tags", Seq("A"), Seq("B"))
          )
        )
        c.tracker.getEvents("1") mustEqual Seq(expUpdate)
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
