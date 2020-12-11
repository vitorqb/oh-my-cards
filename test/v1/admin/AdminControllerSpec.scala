package v1.admin

import org.scalatestplus.play.PlaySpec
import test.utils.LoggedInUserAuthContext
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.mockito.MockitoSugar
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.BeforeAndAfter
import test.utils.TestUtils
import play.api.db.Database
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers._

class AdminControllerSpec
    extends PlaySpec
    with LoggedInUserAuthContext
    with GuiceOneAppPerSuite
    with MockitoSugar
    with ScalaFutures
    with BeforeAndAfter {

  after {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
  }

  val fakeVersion = "000.111.222"

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .overrides(new LoggedInUserFakeModule)
      .configure("app.version" -> fakeVersion)
      .build()

  def controller = app.injector.instanceOf[AdminController]

  "synchronizeElasticSearch" should {

    "return forbidden for users that are not admin" in {
      val request = FakeRequest()
      val response = controller.synchronizeElasticSearch()(request)
      status(response) mustEqual 403
    }

  }

  "version" should {

    "return the app version" in {
      val request = FakeRequest("GET", "/version")
      val response = controller.version()(request)
      contentAsString(response) mustEqual fakeVersion
    }

  }

}
