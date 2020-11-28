package testutils.silhouettetestutils

import com.mohiva.play.silhouette.api.Environment
import v1.auth.DefaultEnv
import scala.concurrent.ExecutionContext
import net.codingwell.scalaguice.ScalaModule
import play.api.inject.guice.GuiceApplicationBuilder
import v1.auth.User
import com.mohiva.play.silhouette.api.LoginInfo
import scala.concurrent.Future
import com.mohiva.play.silhouette.test.FakeEnvironment
import com.mohiva.play.silhouette.api.RequestProvider
import play.api.mvc.Request
import v1.auth.SilhouetteEnvWrapper
import play.api.Application

/**
  * Context that can be used in tests providing an `app` with silhouette stubbed.
  */
trait SilhouetteInjectorContext {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  /**
    * The mocked user and loginInfo that is used
    */
  val user = User("fakeUserId", "fakeUser@fakeUser.com", false)
  val loginInfo = LoginInfo("fakeProvider", "fakeUser@fakeUser.com")

  /**
    * Mocked request provider always returning the LoginInfo
    */
  lazy val requestProvider = new RequestProvider {
    override def id = "fakeRequestProvider"
    override def authenticate[B](request: Request[B]): Future[Option[LoginInfo]] = {
      Future.successful(Some(loginInfo))
    }
  }

  /**
    * The mocked silhouette environment.
    */
  lazy val env = FakeEnvironment[DefaultEnv](Seq((loginInfo -> user)), Seq(requestProvider))

  /**
    * The GuiceModule with the bindings for the env
    */
  class GuiceModule extends ScalaModule {
    override def configure(): Unit = {
      bind[Environment[DefaultEnv]].toInstance(env)
    }
  }

  /**
    * The app
    */
  lazy val app = new GuiceApplicationBuilder().overrides(new GuiceModule).build()

  /**
    * The silhouette instance
    */
  lazy val silhouette = app.injector.instanceOf[SilhouetteEnvWrapper].silhouette
  def silhouette(app: Application) = app.injector.instanceOf[SilhouetteEnvWrapper].silhouette

}


object SilhouetteTestUtils {

  def running()(block: SilhouetteInjectorContext => Any): Any = {
    val context = new SilhouetteInjectorContext {}
    block(context)
  }

}
