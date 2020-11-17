package services.resourcepermissionregistry

import org.scalatestplus.play.PlaySpec
import test.utils.TestUtils
import v1.auth.User
import scala.concurrent.ExecutionContext
import org.scalatest.concurrent.ScalaFutures

class ResourcePermissionRegistrySpec
    extends PlaySpec
    with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  "save and retrieve user permission" should {
    "work" in {
      TestUtils.testDB { db =>
        val user = User("id", "a@b.c", false)
        val key = "95fd47fb-fa65-4b92-befe-220dd247d849"
        val registry = new ResourcePermissionRegistry(db)
        registry.hasAccess(user, key).futureValue mustEqual false
        registry.grantAccess(user, key).futureValue mustEqual ()
        registry.hasAccess(user, key).futureValue mustEqual true
      }
    }
  }

}
