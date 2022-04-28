package services.eventbus

import akka.event.EventBus
import akka.event.LookupClassification
import akka.actor.ActorRef

final case class MsgEnvelope(topic: String, payload: Any)

class ApplicationEventBus extends EventBus with LookupClassification {
  type Event = MsgEnvelope
  type Classifier = String
  type Subscriber = ActorRef

  override protected def classify(event: Event): Classifier = event.topic
  override protected def publish(event: Event, subscriber: Subscriber): Unit = {
    subscriber ! event.payload
  }
  override protected def compareSubscribers(a: Subscriber, b: Subscriber): Int =
    a.compareTo(b)
  override protected def mapSize: Int = 128
}
