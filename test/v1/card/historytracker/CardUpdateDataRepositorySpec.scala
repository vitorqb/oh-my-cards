package v1.card.historytracker

import org.scalatestplus.play.PlaySpec
import v1.card.{
  CardData
}
import services.CounterUUIDGenerator
import play.api.db.Database
import test.utils.TestUtils

class CardUpdateDataRepositorySpec extends PlaySpec {

  case class TestContext(db: Database)

  def testContext(block: TestContext => Any): Any =
    TestUtils.testDB { db =>
      block(TestContext(db))
    }

  "create" should {

    "create and retrieve an update without any changes" in testContext { c =>
      c.db.withTransaction { implicit t =>
        val data = CardData("1", "foo", "", List(), None, None, 1)
        val repo = new CardUpdateDataRepository(new CounterUUIDGenerator)
        repo.create("2", data, data)
        repo.getFieldsUpdates("2") mustEqual Seq()
      }
    }

    "create and retrieve an update for the title" in testContext { c =>
      c.db.withTransaction { implicit t =>
        val oldData = CardData("1", "foo", "", List(), None, None, 1)
        val newData = CardData("1", "bar", "", List(), None, None, 1)
        val repo = new CardUpdateDataRepository(new CounterUUIDGenerator)

        repo.create("2", oldData, newData)

        repo.getFieldsUpdates("2") mustEqual Seq(new StringFieldUpdate("title", "foo", "bar"))
      }
    }

    "create and retrieve an update for the body" in testContext { c =>
      c.db.withTransaction { implicit t =>
        val oldData = CardData("1", "bar", "foo", List(), None, None, 1)
        val newData = CardData("1", "bar", "", List(), None, None, 1)
        val repo = new CardUpdateDataRepository(new CounterUUIDGenerator)

        repo.create("2", oldData, newData)

        repo.getFieldsUpdates("2") mustEqual Seq(new StringFieldUpdate("body", "foo", ""))
      }
    }

    "create and retrieve an update for the body and title" in testContext { c =>
      c.db.withTransaction { implicit t =>
        val oldData = CardData("1", "bar", "A", List(), None, None, 1)
        val newData = CardData("1", "foo", "", List(), None, None, 1)
        val repo = new CardUpdateDataRepository(new CounterUUIDGenerator)

        repo.create("2", oldData, newData)

        val exp = Seq(StringFieldUpdate("title", "bar", "foo"), StringFieldUpdate("body", "A", ""))
        repo.getFieldsUpdates("2").toSet mustEqual exp.toSet
      }
    }

    "create and retrieve an update for tags" in testContext { c =>
      c.db.withTransaction { implicit t =>
        val oldData = CardData("1", "title", "", List("A", "B"), None, None, 1)
        val newData = CardData("1", "title", "", List("B", "C"), None, None, 1)
        val repo = new CardUpdateDataRepository(new CounterUUIDGenerator)

        repo.create("2", oldData, newData)

        val expUpdate = TagsFieldUpdate("tags", List("A", "B"), List("B", "C"))
        repo.getFieldsUpdates("2") mustEqual Seq(expUpdate)
      }
    }

    "all together" in testContext { c =>
      c.db.withTransaction { implicit t =>
        val oldData = CardData("1", "OldTitle", "OldBody", List("A"), None, None, 1)
        val newData = CardData("1", "NewTitle", "NewBody", List("B"), None, None, 1)
        val repo = new CardUpdateDataRepository(new CounterUUIDGenerator)

        repo.create("2", oldData, newData)

        val expUpdates = Seq(
          StringFieldUpdate("title", "OldTitle", "NewTitle"),
          StringFieldUpdate("body", "OldBody", "NewBody"),
          TagsFieldUpdate("tags", List("A"), List("B")),
        )
        repo.getFieldsUpdates("2") mustEqual expUpdates

      }
    }

  }
}
