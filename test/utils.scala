package test.utils

import play.api.db.Database
import play.api.db.Databases
import play.api.db.evolutions.Evolutions
import anorm.{SQL}
import com.mohiva.play.silhouette.api.LoginInfo
import v1.auth.User
import com.mohiva.play.silhouette.test.FakeDummyAuthenticatorService
import com.mohiva.play.silhouette.api.util.ExtractableRequest
import scala.concurrent.Future
import com.mohiva.play.silhouette.impl.authenticators.DummyAuthenticator
import com.mohiva.play.silhouette.api.Environment
import v1.auth.DefaultEnv
import com.mohiva.play.silhouette.test.FakeEnvironment
import scala.concurrent.ExecutionContext.Implicits.global
import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import play.api.i18n.MessagesProvider
import play.api.i18n.MessagesImpl
import play.api.i18n.Lang
import play.api.i18n.DefaultMessagesApi
import org.scalatest.Tag

/**
  * Utils for testing.
  */
object TestUtils {

  private var dbInitialized = false

  val dbDriver = "org.sqlite.JDBC"
  val dbUrl = "jdbc:sqlite:test.sqlite"
  lazy val db = Databases(dbDriver, dbUrl)

  def getDb(): Database = {
    val out = Databases(dbDriver, dbUrl)
    Evolutions.cleanupEvolutions(out)
    Evolutions.applyEvolutions(out)
    out
  }

  /**
    * Cleans all tables from db.
    */
  def cleanupDb(db: Database) = {
    db.withConnection { implicit c =>
      SQL("DELETE FROM cards").execute()
      SQL("DELETE FROM oneTimePasswords").execute()
      SQL("DELETE FROM users").execute()
      SQL("DELETE FROM userTokens").execute()
      SQL("DELETE FROM cardsTags").execute()
      SQL("DELETE FROM cardGridConfigs").execute()
      SQL("DELETE FROM cardGridConfigIncludeTags").execute()
      SQL("DELETE FROM cardGridConfigExcludeTags").execute()
      SQL("DELETE FROM cardGridProfiles").execute()
      SQL("DELETE FROM cardHistoricalEvents").execute()
      SQL("DELETE FROM cardStringFieldUpdates").execute()
      SQL("DELETE FROM cardTagsFieldUpdates").execute()
    }
  }

  /**
    * Used as a context manager for tests with db.
    */
  def testDB[T](block: Database => T) = {
    if (! dbInitialized) {
      Evolutions.cleanupEvolutions(db)
      Evolutions.applyEvolutions(db)
      dbInitialized = true
    }
    try {
      block(db)
    } finally {
      cleanupDb(db)
    }
  }

}

/**
  * Utils for testing with json objects.
  */
trait JsonUtils {
  import play.api.libs.json._
  implicit class EnrichedJsonForTest(val js: JsValue) {
    def hasKey(key: String): Boolean = (js \ key) match {
      case JsDefined(_) => true
      case JsUndefined() => false
    }
  }
}

/**
  * String comparison utils.
  */
trait StringUtils {

  implicit class EnrichedStringForTest(val s: String) {
    def cleanForComparison: String = s.trim.replaceAll("\\n+", "").replaceAll(" +", " ")
  }

}

/**
  * Utils for tests with an authenticated user.
  * Use it like this:
  * @class BlaSpec extends PlaySpec with LoggedInUserAuthContext with GuiceOneAppPerSuite {
  *   override def fakeApplication: Application =
  *     new GuiceApplicationBuilder().overrides(new LoggedInUserFakeModule).build()
  * }
  */
trait LoggedInUserAuthContext {

  /**
    * The User that will be authenticated.
    */
  val user = User("user-id", "user@email")

  /**
    * A LoginInfo that will be authenticated.
    */
  val loginInfo = LoginInfo("provider-id", "user@email")

  /**
    * An authenticator that will be used for authentication.
    */
  val authenticator = DummyAuthenticator(loginInfo)

  /**
    * This is a fake authenticator service that will always retrieve the user.
    */
  class LoggedInUserAuthenticatorService extends FakeDummyAuthenticatorService {
    override def retrieve[B](implicit request: ExtractableRequest[B]) =
      Future.successful(Some(authenticator))
  }

  /**
    * This is a fake Silhouette Environment that will be used by our Controller.
    */
  implicit val env: Environment[DefaultEnv] =
    new FakeEnvironment[DefaultEnv](Seq(loginInfo -> user)) {
      override val authenticatorService = new LoggedInUserAuthenticatorService
    }

  /**
    * This is a fake Guice Module for injecting our FakeEnvironment into the Silhouette
    *  stack.
    */
  class LoggedInUserFakeModule extends AbstractModule with ScalaModule {
    override def configure() = bind[Environment[DefaultEnv]].toInstance(env)
  }
}

/**
  * Provides a default MessageProvider for testing.
  */
trait WithImplicitMessageProvider {
  implicit val messagesProvider: MessagesProvider =
    MessagesImpl(Lang("en"), new DefaultMessagesApi)
}

/**
  * A tag for functional tests that depend on extra setup.
  */
object FunctionalTestsTag extends Tag("tags.FunctionalTests")

/**
  * Provides a quick way to wait until something is accomplished
  */
trait WaitUntil {

  val maxAttempts = 20
  val waitFor = 250

  def waitUntil(block: () => Boolean): Unit = {
    var attempts = 0
    while (attempts <= maxAttempts) {
      if (block()) {
        return ()
      }
      attempts += 1
      Thread.sleep(waitFor)
    }
    throw new Exception("Timed out!")
  }

}
