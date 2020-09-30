package v1.card

import java.sql.Connection
import org.joda.time.DateTime
import v1.auth.User
import scala.concurrent.Future
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import scala.concurrent.ExecutionContext

/**
  * A trait for all known user exceptions.
  */
sealed trait CardRepositoryUserException { val message: String }

/**
  * Custom exception signaling that a card does not exist.
  */
final case class CardDoesNotExist(
  val message: String = "The required card does not exist.",
  val cause: Throwable = None.orNull
) extends Exception(message, cause) with CardRepositoryUserException

/**
  * Custom exception signaling that an error ocurred when parsing the tags mini lang.
  */
final case class TagsFilterMiniLangSyntaxError(
  val message: String,
  val cause: Throwable = None.orNull
) extends Exception(message, cause) with CardRepositoryUserException

/**
  * A trait for a tags repository.
  */
trait TagsRepositoryLike {
  def delete(cardId: String)(implicit c:Connection): Unit
  def create(cardId: String, tags: List[String])(implicit c: Connection): Unit
  def get(cardId: String)(implicit c: Connection): List[String]
  def fill(card: CardData)(implicit c: Connection): CardData

  def update(card: CardData)(implicit c: Connection): Unit = {
    delete(card.id)
    create(card.id, card.tags)
  }
}

/**
  * A trait for card data repository.
  */
trait CardDataRepositoryLike {
  def create(cardFormInput: CardFormInput, context: CardCreationContext)(implicit c: Connection): Unit
  def get(id: String, user: User)(implicit c: Connection): Option[CardData]
  def find(request: CardListRequest, idsResult: IdsFindResult)(implicit c: Connection): FindResult
  def delete(id: String, user: User)(implicit c: Connection): Unit
  def update(data: CardData, context: CardUpdateContext)(implicit c: Connection): Unit
  def getAllTags(user: User)(implicit c: Connection): List[String]
}

/**
  * The data for a Card.
  */
final case class CardData(
  id: String,
  title: String,
  body: String,
  tags: List[String],
  createdAt: Option[DateTime] = None,
  updatedAt: Option[DateTime] = None,
  ref: Int
)

/**
  * The context for a card creation.
  */
case class CardCreationContext(user: User, now: DateTime, id: String, ref: Int)

/**
  * The context for a card update.
  */
case class CardUpdateContext(user: User, now: DateTime)

/**
  * A result for a find query containing all ids found.
  * 
  * @param ids the ids that are part of the page of results
  * @param countOfItems the total number of items matched
  */
final case class IdsFindResult(ids: Seq[String], countOfItems: Integer)

/**
  * The result of a find query.
  */
final case class FindResult(cards: Seq[CardData], countOfItems: Int)

/**
  * The base trait for a CardRepository.
  */
trait CardRepositoryLike {
  def create(cardFormInput: CardFormInput, user: User): Future[String]
  def get(id: String, user: User): Future[Option[CardData]]
  def find(request: CardListRequest): Future[FindResult]
  def delete(id: String, user: User): Future[Unit]
  def update(data: CardData, user: User): Future[Unit]
  def getAllTags(user: User): Future[List[String]]
}

/**
  * The base trait for a card id query 
  */
trait CardElasticClientLike {

  /**
    * Creates a new entry on ElasticSearch for a new cardFormInput, with a given id created at
    *  a specific time.
    */
  def create(cardFormInput: CardFormInput, context: CardCreationContext): Unit

  /**
    * Updates an entry on ElasticSearch for an existing cardData.
    */
  def update(cardData: CardData, context: CardUpdateContext): Unit

  /**
    * Deletes an entry from ElasticSearch for an existing cardData.
    */
  def delete(id: String): Unit

  /**
    * Returns a seq of ids from ElasticSearch that matches a CardListRequest.
    */
  def findIds(cardListReq: CardListRequest): Future[IdsFindResult]
}

/**
  * The implementation
  */
class CardRepository(
  dataRepo: CardDataRepositoryLike,
  tagsRepo: TagsRepositoryLike,
  esClient: CardElasticClientLike,
  components: CardRepositoryComponentsLike
)(
  implicit ec: ExecutionContext
) extends CardRepositoryLike {

  override def create(cardFormInput: CardFormInput, user: User): Future[String] = Future {
    components.db.withTransaction { implicit c =>
      val now = components.clock.now
      val id = components.uuidGenerator.generate()
      val ref = components.refGenerator.nextRef()
      val context = CardCreationContext(user, now, id, ref)

      dataRepo.create(cardFormInput, context)
      tagsRepo.create(id, cardFormInput.getTags())
      esClient.create(cardFormInput, context)

      id
    }
  }

  override def get(id: String, user: User): Future[Option[CardData]] = Future {
    components.db.withTransaction { implicit c =>
      dataRepo.get(id, user).map { data =>
        tagsRepo.fill(data)
      }
    }
  }

  override def find(request: CardListRequest): Future[FindResult] =
    esClient.findIds(request).map { idsResult =>
      components.db.withConnection { implicit c =>
        val findResult = dataRepo.find(request, idsResult)
        val cardsWithTags = findResult.cards.map(x => tagsRepo.fill(x))
        findResult.copy(cards=cardsWithTags)
      }
    }

  override def delete(id: String, user: User): Future[Unit] = Future {
    components.db.withTransaction { implicit c =>
      dataRepo.delete(id, user)
      tagsRepo.delete(id)
      esClient.delete(id)
    }
  }

  override def update(data: CardData, user: User): Future[Unit] = Future {
    val context = CardUpdateContext(user, components.clock.now)
    components.db.withTransaction { implicit c =>
      dataRepo.update(data, context)
      tagsRepo.update(data)
      esClient.update(data, context)
    }
  }

  override def getAllTags(user: User): Future[List[String]] = Future {
    components.db.withConnection { implicit c =>
      dataRepo.getAllTags(user)
    }
  }

}
