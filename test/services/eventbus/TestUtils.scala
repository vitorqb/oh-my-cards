package services.eventbus

import akka.actor.Actor
import javax.inject

@inject.Singleton
class MockReceiver extends Actor {

  var received: Option[String] = None

  override def receive = {
    case "tellMe" => {
      sender() ! received
    }
    case s: String => {
      received = Some(s)
    }
  }

}
