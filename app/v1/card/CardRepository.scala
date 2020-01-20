package v1.card

import javax.inject.Inject
import java.util.UUID.randomUUID
import scala.util.{Try,Success,Failure}
import anorm.{SQL,RowParser,Macro}
import play.api.db.Database

/**
  * The data for a Card.
  */
final case class CardData(id: Option[String], title: String, body: String)

/**
  * An interface for a card repository.
  */
trait CardRepository {
  def genId(): String
  def create(cardData: CardData): Try[String]
  def get(id: String): Option[CardData]
}

/**
  * An implementation for a card repository.
  */
class CardRepositoryImpl @Inject()(db: Database) extends CardRepository {

  private val cardDataParser: RowParser[CardData] = Macro.namedParser[CardData]

  def genId = randomUUID().toString()

  def create(cardData: CardData): Try[String] = {
    if (cardData.id.isDefined) {
      Failure(new Exception("Id for create should be null!"))
    } else {
      val id = genId
      db.withConnection { implicit c =>
        SQL("INSERT INTO cards(id, title, body) VALUES ({id}, {title}, {body});")
          .on("id" -> id, "title" -> cardData.title, "body" -> cardData.body)
          .executeInsert()
        Success(id)
      }
    }
  }

  def get(id: String): Option[CardData] = db.withConnection { implicit c =>
    SQL("SELECT id, title, body FROM cards WHERE id = {id}")
      .on("id" -> id)
      .as(cardDataParser.*)
      .headOption
  }
}
