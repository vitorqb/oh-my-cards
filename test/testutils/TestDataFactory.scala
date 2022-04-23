package testutils

import v1.card.models.CardCreateData
import v1.auth.User
import org.joda.time.DateTime
import v1.card.models.CardCreationContext
import services.UUIDGeneratorLike
import v1.card.models.CardData
import v1.card.models.CardUpdateContext

trait TestDataFactoryLike[T] {
  var instance: Option[T] = None
  def build(): T
}

class Counter {
  private var current: Int = -1
  def next(): Int = {
    current += 1
    current
  }
  def getLast() = current
}

case class CardCreateDataFactory(
    val title: String = "title",
    val body: String = "body",
    val tags: List[String] = List()
) extends TestDataFactoryLike[CardCreateData] {
  def withTags(t: List[String]) = copy(tags = t)
  def withTitle(t: String) = copy(title = t)
  def withBody(b: String) = copy(body = b)
  def build() = CardCreateData(title, body, tags)
}

case class UserFactory(
    val id: Option[String] = None,
    val email: String = "email@email.com",
    val isAdmin: Boolean = false
)(implicit
    val uuidGenerator: UUIDGeneratorLike
) extends TestDataFactoryLike[User] {
  def build() = User(id.getOrElse(uuidGenerator.generate()), email, isAdmin)
}

case class CardCreationContextFactory(
    val user: Option[User] = None,
    val now: DateTime = DateTime.parse("2020-01-01T12:12:12"),
    val id: Option[String] = None,
    val ref: Option[Int] = None
)(implicit
    val counter: Counter,
    val uuidGenerator: UUIDGeneratorLike
) extends TestDataFactoryLike[CardCreationContext] {
  def withUser(u: User) = copy(user = Some(u))
  def withNow(n: DateTime) = copy(now = n)
  def withId(i: String) = copy(id = Some(i))
  def withRef(r: Int) = copy(ref = Some(r))
  def build(): CardCreationContext =
    CardCreationContext(
      user.getOrElse(UserFactory().build()),
      now,
      id.getOrElse(uuidGenerator.generate()),
      ref.getOrElse(counter.next())
    )
}

case class CardDataFactory(
    val id: Option[String] = None,
    val title: String = "title",
    val body: String = "body",
    val tags: List[String] = List(),
    val createdAt: Option[DateTime] = Some(
      DateTime.parse("2022-01-01T11:00:00")
    ),
    val updatedAt: Option[DateTime] = Some(
      DateTime.parse("2022-01-10T12:00:00")
    ),
    val ref: Option[Int] = None
)(
    implicit val counter: Counter,
    implicit val uuidGenerator: UUIDGeneratorLike
) extends TestDataFactoryLike[CardData] {
  def withTags(t: List[String]) = copy(tags = t)
  def withBody(b: String) = copy(body = b)
  def withTitle(t: String) = copy(title = t)
  def withCreationContext(c: CardCreationContext) =
    copy(
      id = Some(c.id),
      createdAt = Some(c.now),
      updatedAt = Some(c.now),
      ref = Some(c.ref)
    )
  def build(): CardData =
    CardData(
      id.getOrElse(uuidGenerator.generate()),
      title,
      body,
      tags,
      createdAt,
      updatedAt,
      ref.getOrElse(counter.next())
    )
}

case class CardUpdateContextFactory(
    val user: Option[User] = None,
    val now: DateTime = DateTime.parse("2022-01-01T11:00:00"),
    val oldData: Option[CardData] = None
)(implicit val counter: Counter, implicit val uuidGenerator: UUIDGeneratorLike)
    extends TestDataFactoryLike[CardUpdateContext] {
  def withUser(u: User) = copy(user = Some(u))
  def withOldData(x: CardData) = copy(oldData = Some(x))
  override def build(): CardUpdateContext =
    CardUpdateContext(
      user.getOrElse(UserFactory().build()),
      now,
      oldData.getOrElse(CardDataFactory().build())
    )
}
