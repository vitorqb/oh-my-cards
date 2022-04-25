package v1.card

import org.scalatestplus.play.PlaySpec
import org.mockito.MockitoSugar
import play.api.db.Database
import scala.concurrent.ExecutionContext
import v1.card.testUtils.MockDb
import java.sql.Connection
import org.scalatest.concurrent.ScalaFutures
import testutils.CardCreationHistoricalEventFactory
import scala.concurrent.Future
import v1.card.repository.UserCardPermissionManagerLike
import testutils.UserFactory
import services.CounterSeedUUIDGenerator
import v1.card.exceptions.CardDoesNotExist

class HistoryTrackerHandlerSpec
    extends PlaySpec
    with MockitoSugar
    with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val uuidGenerator = new CounterSeedUUIDGenerator

  case class TestContext(
      connection: Connection,
      db: Database,
      tracker: CardHistoryTrackerLike,
      permissionManager: UserCardPermissionManagerLike,
      handler: HistoryTrackerHandler
  )

  def testContext(block: TestContext => Any): Any = {
    val connection = mock[Connection]
    val db = new MockDb {
      override def withConnection[A](block: Connection => A): A =
        block(connection)
      override def getConnection(): Connection = connection
    }
    val tracker = mock[CardHistoryTrackerLike]
    val permissionManager = mock[UserCardPermissionManagerLike]
    val handler = new HistoryTrackerHandler(db, tracker, permissionManager)
    block(TestContext(connection, db, tracker, permissionManager, handler))
  }

  "get" should {

    "return a CardHistoryResource" in testContext { c =>
      val user = UserFactory().build()
      val events = Seq(CardCreationHistoricalEventFactory().build())
      when(c.tracker.getEvents("id")(c.connection)).thenReturn(events)
      when(c.permissionManager.hasPermission(user, "id")(c.connection))
        .thenReturn(Future.successful(true))

      c.handler.get("id", user).futureValue mustEqual CardHistoryResource(
        events
      )
    }

    "refuse to return a CardHistoryResource of another user" in testContext {
      c =>
        val user = UserFactory().build()
        val events = Seq(CardCreationHistoricalEventFactory().build())
        when(c.tracker.getEvents("id")(c.connection)).thenReturn(events)
        when(c.permissionManager.hasPermission(user, "id")(c.connection))
          .thenReturn(Future.successful(false))

        c.handler
          .get("id", user)
          .failed
          .futureValue mustEqual new CardDoesNotExist
    }

  }

}
