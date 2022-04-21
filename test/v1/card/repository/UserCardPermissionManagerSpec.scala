package v1.card.userpermissionmanager

import org.scalatestplus.play.PlaySpec
import test.utils.TestUtils
import test.utils.FunctionalTestsTag
import testutils.UserFactory
import org.scalatest.concurrent.ScalaFutures
import play.api.db.Database
import services.resourcepermissionregistry.ResourcePermissionRegistry
import scala.concurrent.ExecutionContext
import v1.auth.User

class UserCardPermissionManagerSpec extends PlaySpec with ScalaFutures {

  implicit val ec = ExecutionContext.global

  case class TestContext(
      permissionManager: UserCardPermissionManager,
      db: Database
  )

  def testContext(block: TestContext => Any): Any = {
    TestUtils.testDB { db =>
      val registry = new ResourcePermissionRegistry
      val permissionManager = new UserCardPermissionManager(registry)
      block(TestContext(permissionManager, db))
    }
  }

  "Functional tests for UserCardPermissionManager" should {

    "return false if not permission for a card" taggedAs FunctionalTestsTag in testContext {
      c =>
        val user = UserFactory()
        c.db.withConnection { implicit conn =>
          c.permissionManager
            .hasPermission(user, "cardId")
            .futureValue mustEqual false
        };
    }

    "return true if has permission for a card" taggedAs FunctionalTestsTag in testContext {
      c =>
        val user = UserFactory()
        val cardId = "cardId"
        c.db.withConnection { implicit conn =>
          c.permissionManager
            .givePermission(user, cardId)
            .futureValue mustEqual ()
          c.permissionManager
            .hasPermission(user, cardId)
            .futureValue mustEqual true
        }
    }

  }

}
