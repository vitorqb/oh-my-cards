package testutils

import v1.card.models.CardCreateData
import v1.auth.User
import org.joda.time.DateTime
import v1.card.models.CardCreationContext
import utils.lazyvalue.LazyValue
import services.UUIDGeneratorLike

trait TestDataFactoryLike[T] {
  var instance: Option[T] = None
  def build(): T
}

class Counter {
  var current = -1
  def next() = {
    current += 1
    current
  }
}

case class CardCreateDataFactory() extends TestDataFactoryLike[CardCreateData] {
  private var title: String = "title"
  private var body: String = "body"
  private var tags: List[String] = List()

  def withTags(t: List[String]): CardCreateDataFactory = {
    tags = t
    this
  }

  def build() = CardCreateData(title, body, tags)
}

case class UserFactory() extends TestDataFactoryLike[User] {
  private val counter = new Counter
  private var id = LazyValue(() => counter.next().toString())
  private var email: String = "email@email.com"
  private var isAdmin: Boolean = false

  def build() = User(id, email, isAdmin)
}

case class CardCreationContextFactory()(implicit val counter: Counter, val uuidGenerator: UUIDGeneratorLike) extends TestDataFactoryLike[CardCreationContext] {
  private var user = LazyValue(() => UserFactory().build())
  private var now = DateTime.parse("2020-01-01T12:12:12")
  private var id = LazyValue(() => uuidGenerator.generate())
  private var ref = LazyValue(() => counter.next())

  def withUser(u: User) = {
    user = u
    this
  }

  def build(): CardCreationContext = CardCreationContext(user, now, id, ref)
}

object TestDataFactoryLike {
  implicit def build[T](factory: TestDataFactoryLike[T]): T = {
    if (!factory.instance.isDefined) {
      factory.instance = Some(factory.build())
    }
    factory.instance.get
  }
}
