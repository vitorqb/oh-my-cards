package v1.card.historytrackerhandler

import org.scalatestplus.play.PlaySpec
import org.mockito.MockitoSugar
import play.api.db.Database
import scala.concurrent.ExecutionContext
import v1.card.testUtils.MockDb
import java.sql.Connection
import v1.card.historytracker.CardCreation
import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures

class HistoryTrackerHandlerSpec
    extends PlaySpec
    with MockitoSugar
    with ScalaFutures {

  implicit val ec: ExecutionContext = ExecutionContext.global

  case class TestContext(
      connection: Connection,
      db: Database,
      tracker: CardHistoryTrackerLike,
      handler: HistoryTrackerHandler
  )

  def testContext(block: TestContext => Any): Any = {
    val connection = mock[Connection]
    val db = new MockDb {
      override def withConnection[A](block: Connection => A): A =
        block(connection)
    }
    val tracker = mock[CardHistoryTrackerLike]
    val handler = new HistoryTrackerHandler(db, tracker)
    block(TestContext(connection, db, tracker, handler))
  }

  "get" should {

    "return a CardHistoryResource" in testContext { c =>
      val datetime = DateTime.parse("2020-01-01T00:00:00Z")
      val events = Seq(CardCreation(datetime, "id", "userId"))
      when(c.tracker.getEvents("id")(c.connection)).thenReturn(events)

      c.handler.get("id").futureValue mustEqual CardHistoryResource(events)
    }

  }

}
