package v1.card.tagsrepositoryspec

import v1.card.tagsrepository._

import org.scalatestplus.play.PlaySpec
import v1.card.TagsRepositoryLike
import test.utils.TestUtils
import play.api.db.Database
import v1.card.CardData

case class TestContext(val db: Database, val tagsRepo: TagsRepositoryLike)

object TestContext {
  def run(block: TestContext => Any): Any = {
    val tagsRepo = new TagsRepository()
    TestUtils.testDB { db =>
      try {
        block(TestContext(db, tagsRepo))
      } finally {
        TestUtils.cleanupDb(db)
      }
    }
  }
}

class TagsRepositorySpec extends PlaySpec {

  "TagsRepository" should {

    "create and get tags for cards" in TestContext.run { c =>
      c.db.withTransaction { implicit t =>
        c.tagsRepo.get("id") mustEqual List()
        c.tagsRepo.create("id", List("A", "B")) mustEqual ()
        c.tagsRepo.get("id") mustEqual List("A", "B")
      }
    }

    "create and delete tags for a card" in TestContext.run { c =>
      c.db.withConnection { implicit t =>
        c.tagsRepo.get("id1") mustEqual List()
        c.tagsRepo.get("id2") mustEqual List()

        c.tagsRepo.create("id1", List("Bar", "Foo"))
        c.tagsRepo.create("id2", List("Baz", "Buz"))

        c.tagsRepo.get("id1") mustEqual List("Bar", "Foo")
        c.tagsRepo.get("id2") mustEqual List("Baz", "Buz")

        c.tagsRepo.delete("id1")

        c.tagsRepo.get("id1") mustEqual List()
        c.tagsRepo.get("id2") mustEqual List("Baz", "Buz")
      }
    }

  }

  "fill" should {
    "Fill the card with it's tags" in TestContext.run { c =>
      c.db.withTransaction { implicit t =>
        c.tagsRepo.create("1", List("Bar", "Foo"))
        val data = CardData("1", "A", "B", List(), None, None, 1)
        c.tagsRepo.fill(data) mustEqual data.copy(tags=List("Bar", "Foo"))
      }
    }
  }

}
