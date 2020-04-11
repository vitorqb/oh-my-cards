package v1.cardGridProfile

import org.scalatestplus.play.PlaySpec
import v1.auth.User
import test.utils._

import scala.concurrent.ExecutionContext.Implicits.global
import org.mockito.MockitoSugar
import services.UUIDGenerator
import anorm.{SQL}
import anorm.SqlParser
import org.scalatest.concurrent.ScalaFutures
import java.sql.Connection
import play.api.db.Database

class CardGridProfileRepositorySpec extends PlaySpec with MockitoSugar with ScalaFutures {

  val input = CardGridProfileInput(
    "Name",
    CardGridConfigInput(
      page=Some(1),
      pageSize=None,
      includeTags=Some(List("IncludeTag1")),
      excludeTags=None
    )
  )

  val user = User("user-id", "user@email")

  val uuidGenerator = mock[UUIDGenerator]
  when(uuidGenerator.generate).thenReturn("created-id")

  "create" should {

    "returns the created data" in {
      TestUtils.testDB { db =>
        val repository = new CardGridProfileRepository(db, uuidGenerator)
        val result = repository.create(input, user).futureValue
        val expected = CardGridProfileData(
          "created-id",
          user.id,
          "Name",
          CardGridConfigData(
            "created-id",
            Some(1),
            None,
            Some(List("IncludeTag1")),
            None
          )
        )
        result mustEqual expected
      }
    }

    "store the info on the db" in {
      TestUtils.testDB { db =>
        val repository = new CardGridProfileRepository(db, uuidGenerator)
        val result = repository.create(input, user).futureValue
        db.withConnection { implicit db =>
          val count = SQL("""SELECT COUNT(*) FROM cardGridProfiles WHERE id = "created-id"""")
            .as(SqlParser.int(1).single)
          count mustEqual 1
        }
      }
    }
  }

  "getProfileIdFromName" should {

    "returns none if not found for this user" in {
      TestUtils.testDB { implicit db =>
        db.withConnection { implicit c =>
          val wrongUser = User("wrongUserId", "wrong@user")
          SQL("""INSERT INTO cardGridProfiles(id, name, userId) 
                 VALUES ('id', 'name', 'user-id')""")
            .executeInsert()
          val repository = new CardGridProfileRepository(db, uuidGenerator)
          repository.getProfileIdFromName("name", wrongUser).futureValue mustEqual None
        }
      }
    }

    "returns data if found for this user" in {
      TestUtils.testDB { implicit db =>
        db.withConnection { implicit c =>
          SQL("""INSERT INTO cardGridProfiles(id, name, userId)
                 VALUES ('id', 'name', 'user-id')""")
            .executeInsert()
          val repository = new CardGridProfileRepository(db, uuidGenerator)
          repository.getProfileIdFromName("name", user).futureValue mustEqual Some("id")
        }
      }
    }
  }

  "userHasProfileWithName" should {

    trait UserHasProfileWithName {
      val user = User("userId", "user@email")
      def repository(implicit db: Database) = new CardGridProfileRepository(db, uuidGenerator)
      def addProfile()( implicit db: Database) = db.withConnection { implicit c =>
        SQL("INSERT INTO cardGridProfiles(id, name, userId) VALUES ('id', 'name', 'userId')")
          .executeInsert()
      }
    }

    "return true if user has profile with name" in new UserHasProfileWithName {
      TestUtils.testDB { implicit db =>
        addProfile()
        repository.userHasProfileWithName(user, "name").futureValue mustBe true
      }
    }

    "return false if user does not has profile with name" in new UserHasProfileWithName {
      TestUtils.testDB { implicit db =>
        val otherUser = User("other", "user")
        addProfile()
        repository.userHasProfileWithName(otherUser, "name").futureValue mustBe false
        repository.userHasProfileWithName(user, "otherName").futureValue mustBe false
      }
    }
  }
}

class CardGridProfileCreatorSpec extends PlaySpec {

  val input = CardGridProfileInput("", CardGridConfigInput(None, None, None, None))
  val user = User("userId", "user@email")

  "createConfig" should {

    def countWhere(whereStatement: String)(implicit c: Connection): Int =
      SQL("SELECT COUNT(*) FROM cardGridConfigs WHERE " + whereStatement)
        .as(SqlParser.int(1).single)

    "Store the config in the db without null" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          val input_ = input.copyConfig(page=Some(1), pageSize=Some(2))
          val creator = new CardGridProfileCreator(t, input_, user, "profileId", "configId")
          creator.createConfig
          countWhere("page=1 AND pageSize=2 AND profileId='profileId'") mustEqual 1
        }
      }
    }

    "Store the config in the db with nulls" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          val creator = new CardGridProfileCreator(t, input, user, "profileId", "configId")
          creator.createConfig
          countWhere("page IS null AND pageSize IS null AND profileId='profileId'") mustEqual 1
        }
      }
    }
  }

  "createIncludeTags" should {

    def countWhere(whereStatement: String)(implicit c: Connection): Int =
      SQL("SELECT COUNT(*) FROM cardGridConfigIncludeTags WHERE " + whereStatement)
        .as(SqlParser.int(1).single)

    "empty" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          val creator = new CardGridProfileCreator(t, input, user, "profileId", "configId")
          creator.createIncludeTags
          countWhere("1 = 1") mustEqual 0
        }
      }
    }

    "two long" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          val input_ = input.copyConfig(includeTags=Some(List("A", "B")))
          val creator = new CardGridProfileCreator(t, input_, user, "profileId", "configId")
          creator.createIncludeTags
          countWhere("configId = 'configId'") mustEqual 2
          countWhere("tag = 'A' AND configId = 'configId'") mustEqual 1
          countWhere("tag = 'B' AND configId = 'configId'") mustEqual 1
        }
      }
    }
  }

  "createExcludeTags" should {

    def countWhere(whereStatement: String)(implicit c: Connection): Int =
      SQL("SELECT COUNT(*) FROM cardGridConfigExcludeTags WHERE " + whereStatement)
        .as(SqlParser.int(1).single)

    "two long" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          val input_ = input.copyConfig(excludeTags=Some(List("FOO", "BAR")))
          val creator = new CardGridProfileCreator(t, input_, user, "profileId", "configId")
          creator.createExcludeTags
          countWhere("configId = 'configId'") mustEqual 2
          countWhere("tag = 'FOO' AND configId = 'configId'") mustEqual 1
          countWhere("tag = 'BAR' AND configId = 'configId'") mustEqual 1
        }
      }
    }
  }

  "createProfile" should {

    def countWhere(whereStatement: String)(implicit c: Connection): Int =
      SQL("SELECT COUNT(*) FROM cardGridProfiles WHERE " + whereStatement)
        .as(SqlParser.int(1).single)

    "create the profile in the db" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          val input_ = input.copy(name="FOO")
          val creator = new CardGridProfileCreator(t, input_, user, "profileId", "configId")
          creator.createProfile
          countWhere("id = 'profileId' AND name = 'FOO'") mustEqual 1
        }
      }
    }
  }
}

class CardGridProfileReaderSpec extends PlaySpec with ScalaFutures {

  "readProfile" in {
    TestUtils.testDB { implicit db =>
      db.withTransaction { implicit t =>
        SQL("""INSERT INTO cardGridProfiles(id, userId, name)
               VALUES ('a', 'b', 'c')""")
          .executeInsert()
        val config = CardGridConfigData("", None, None, None, None)
        val reader = new CardGridProfileReader("a")
        reader.readProfile(config) mustEqual CardGridProfileData("a", "b", "c", config)
      }
    }
  }

  "readIncludeTags" should {

    "" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          SQL("""INSERT INTO cardGridConfigIncludeTags(tag, configId)
               VALUES ('a', 'z'), ('b', 'z'), ('b', 'w')""")
            .executeInsert()
          new CardGridProfileReader("").readIncludeTags("z") mustEqual Some(List("a", "b"))
        }
      }
    }

    "None" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          new CardGridProfileReader("").readIncludeTags("z") mustEqual None
        }
      }
    }
  }

  "readExcludeTags" should {

    "" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          SQL("""INSERT INTO cardGridConfigExcludeTags(tag, configId)
               VALUES ('a', 'z'), ('b', 'z'), ('b', 'w')""")
            .executeInsert()
          new CardGridProfileReader("").readExcludeTags("z") mustEqual Some(List("a", "b"))
        }
      }
    }

    "None" in {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          new CardGridProfileReader("").readExcludeTags("z") mustEqual None
        }
      }
    }
  }

  "readConfig" should {

    "" in  {
      TestUtils.testDB { implicit db =>
        db.withTransaction { implicit t =>
          val configId = "configId"
          val includeTags = Some(List("A"))
          val excludeTags = Some(List("B"))
          SQL("""INSERT INTO cardGridConfigs(id, profileId, page, pageSize)
             VALUES ('configId', 'profileId', 1, 2), ('configId2', 'profileId2', 3, 4)""")
            .executeInsert()
          val reader = new CardGridProfileReader("profileId")
          val result = reader.readConfig(configId, includeTags, excludeTags)
          val expected = CardGridConfigData(
            configId,
            Some(1),
            Some(2),
            includeTags,
            excludeTags
          )
          result mustEqual expected
        }
      }
    }
  }
}
