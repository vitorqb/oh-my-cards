package testutils

import v1.card.models.CardCreateData
import v1.auth.User
import org.joda.time.DateTime
import v1.card.models.CardCreationContext
import services.CounterSeedUUIDGenerator
import utils.lazyvalue.LazyValue

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

class TestDataFactory {

  val uuidGenerator = new CounterSeedUUIDGenerator
  val counter = new Counter

  def buildUser(
      id: Option[String] = None,
      email: String = "email@email.com",
      isAdmin: Boolean = false
  ) = {
    val id_ = id.getOrElse(uuidGenerator.generate())
    User(id_, email, isAdmin)
  }

  def buildCardCreateData(
      title: String = "title",
      body: String = "body",
      tags: List[String] = List()
  ) = CardCreateData(title, body, tags)

  def buildCardCreationContext(
      user: Option[User] = None,
      now: DateTime = DateTime.parse("2020-01-01T12:12:12"),
      id: Option[String] = None,
      ref: Option[Int] = None
  ) = {
    val user_ = user.getOrElse(buildUser())
    val id_ = id.getOrElse(uuidGenerator.generate())
    val ref_ = ref.getOrElse(counter.next())
    CardCreationContext(user_, now, id_, ref_)
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

object TestDataFactory {
  def run(f: TestDataFactory => Any): Unit = f(new TestDataFactory)
}

object TestDataFactoryLike {
  implicit def build[T](factory: TestDataFactoryLike[T]): T = {
    if (!factory.instance.isDefined) {
      factory.instance = Some(factory.build())
    }
    factory.instance.get
  }
}
