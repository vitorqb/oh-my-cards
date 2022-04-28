package services.eventbus

import org.scalatestplus.play.PlaySpec
import scala.concurrent.duration._
import play.api.inject.guice.GuiceApplicationBuilder
import com.google.inject.Guice
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures

class ApplicationEventBusSpec extends PlaySpec with ScalaFutures {

  implicit val timeout: Timeout = 5.seconds

  case class TestContext(bus: ApplicationEventBus, system: ActorSystem)

  def testContext(f: TestContext => Any) = {
    val guiceModule = new GuiceApplicationBuilder().applicationModule()
    val injector = Guice.createInjector(guiceModule)
    val bus = injector.getInstance(classOf[ApplicationEventBus])
    val system = injector.getInstance(classOf[ActorSystem])
    f(TestContext(bus, system))
  }

  "message sending and receiving" should {
    "send and receive a message" in testContext { c =>
      val topic = "FOO"
      val subscriber = c.system.actorOf(Props[MockReceiver])
      c.bus.subscribe(subscriber, topic)
      c.bus.publish(MsgEnvelope(topic, "Hola"))
      (subscriber ? "tellMe").futureValue mustEqual Some("Hola")
    }
  }

}
