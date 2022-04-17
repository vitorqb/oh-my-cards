package testutils

import v1.card.models.CardCreateData
import v1.auth.User
import org.joda.time.DateTime
import v1.card.models.CardCreationContext
import services.CounterSeedUUIDGenerator

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

object TestDataFactory {
  def run(f: TestDataFactory => Any): Unit = f(new TestDataFactory)
}
