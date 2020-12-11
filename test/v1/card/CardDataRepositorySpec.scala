package v1.card.carddatarepositoryspec

import play.api.db.Database
import org.scalatestplus.play.PlaySpec
import test.utils.TestUtils
import v1.auth.User
import org.joda.time.DateTime
import v1.card.CardDataRepository
import v1.card.repository.CardCreateData
import v1.card.repository.CardCreationContext
import v1.card.repository.CardData
import v1.card.repository.IdsFindResult
import v1.card.repository.FindResult



class CardDataRepositorySpec extends PlaySpec {

  case class TestContext(repo: CardDataRepository, db: Database)

  def testContext(block: TestContext => Any): Any = {
    TestUtils.testDB { db =>
      try {
        block(TestContext(new CardDataRepository, db))
      } finally {
        TestUtils.cleanupDb(db)
      }
    }
  }

  val user = User("User", "user@user.user")
  val otherUser = User("OtherUser", "other@user.user")
  val now = new DateTime(2020, 1, 1, 1, 1, 1)

  "create" should {

    "create and retrieve a card" in testContext { c =>
      c.db.withConnection { implicit conn =>
        val data = CardCreateData("title", "", List())
        val context = CardCreationContext(user, now, "1", 1)
        c.repo.create(data, context) mustEqual ()
        val expected = Some(CardData("1", "title", "", List(), Some(now), Some(now), 1))
        c.repo.get("1", user) mustEqual expected
      }
    }

    "create and retrieve with body and tags" in testContext { c =>
      c.db.withConnection { implicit conn =>
        val data = CardCreateData("title", "body", List("A", "B"))
        val context = CardCreationContext(user, now, "1", 1)
        c.repo.create(data, context) mustEqual ()
        val expected = Some(CardData("1", "title", "body", List(), Some(now), Some(now), 1))
        c.repo.get("1", user) mustEqual expected
      }
    }
  }

  "get" should {

    "returns None if not exist" in testContext { c =>
      c.db.withConnection { implicit conn =>
        c.repo.get("id", user) mustEqual None
      }
    }

    "returns None if not exist for User" in testContext { c =>
      c.db.withConnection { implicit conn =>
        val data = CardCreateData("title", "", List())
        val context = CardCreationContext(user, now, "1", 1)
        c.repo.create(data, context)
        c.repo.get("1", user) must not equal None
        c.repo.get("1", otherUser) mustEqual None
      }
    }
  }

  "find" should {

    "return no cards" in testContext { c =>
      c.db.withConnection { implicit conn =>
        val idsResult = IdsFindResult(Seq(), 0)
        val result = c.repo.find(idsResult)
        result mustEqual FindResult(Seq(), 0)
      }
    }

    "return two cards in order" in testContext { c =>
      c.db.withConnection { implicit conn =>
        val createData1 = CardCreateData("A", "", List())
        val context1 = CardCreationContext(user, now, "1", 1)
        val data1 = context1.genCardData(createData1)
        val createData2 = CardCreateData("B", "", List())
        val context2 = CardCreationContext(user, now, "2", 2)
        val data2 = context2.genCardData(createData2)
        c.repo.create(createData1, context1)
        c.repo.create(createData2, context2)

        val idsResult = IdsFindResult(Seq("2", "1"), 5)
        val result = c.repo.find(idsResult)
        result mustEqual FindResult(Seq(data2, data1), 5)
      }
    }

  }

}
