package v1.card

import javax.inject.Inject
import java.util.UUID.randomUUID
import scala.util.{Try,Success,Failure}
import anorm.{SQL,RowParser,Macro}
import play.api.db.Database
import v1.auth.User
import services.UUIDGenerator

/**
  * The data for a Card.
  */
final case class CardData(id: Option[String], title: String, body: String)

/**
  * An interface for a card repository.
  */
trait CardRepository {
  def create(cardData: CardData, user: User): Try[String]
  def get(id: String, user: User): Option[CardData]
}

/**
  * An implementation for a card repository.
  */
class CardRepositoryImpl @Inject()(db: Database, uuidGenerator: UUIDGenerator)
    extends CardRepository {

  private val cardDataParser: RowParser[CardData] = Macro.namedParser[CardData]

  def create(cardData: CardData, user: User): Try[String] = {
    if (cardData.id.isDefined) {
      Failure(new Exception("Id for create should be null!"))
    } else {
      val id = uuidGenerator.generate
      db.withConnection { implicit c =>
        SQL("INSERT INTO cards(id, userId, title, body) VALUES ({id}, {userId}, {title}, {body});")
          .on("id" -> id, "userId" -> user.id, "title" -> cardData.title, "body" -> cardData.body)
          .executeInsert()
        Success(id)
      }
    }
  }

  def get(id: String, user: User): Option[CardData] = db.withConnection { implicit c =>
    SQL("SELECT id, title, body FROM cards WHERE id = {id} AND userId = {userId}")
      .on("id" -> id, "userId" -> user.id)
      .as(cardDataParser.*)
      .headOption
  }
}
