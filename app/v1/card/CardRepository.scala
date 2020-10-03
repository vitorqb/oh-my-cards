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
  * 
  * The TagsRepository's single responsibility is to store the tags and
  * it's relations to the cards.
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
  * 
  * The CardDataRepository is the persistance and retrival layer for
  * the base data of a card, usually stored in the main SQL table if
  * using an SQL layer.
  */
trait CardDataRepositoryLike {
  def create(cardFormInput: CardFormInput, context: CardCreationContext)(implicit c: Connection): Unit
  def get(id: String, user: User)(implicit c: Connection): Option[CardData]
  def find(idsResult: IdsFindResult)(implicit c: Connection): FindResult
  def delete(id: String, user: User)(implicit c: Connection): Unit
  def update(data: CardData, context: CardUpdateContext)(implicit c: Connection): Unit
  def getAllTags(user: User)(implicit c: Connection): List[String]
}

/**
  * The basic representation for a Card.
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
  * A genetic context for a card event, like update, create or delete.
  * @param user the user that is creating the card.
  * @param now the current datetime to consider.
  */
trait CardEventContextLike {
  val user: User
  val now: DateTime
}

/**
  * The context for a card creation.
  * 
  * @param user the user that is creating the card.
  * @param now the current datetime to consider.
  * @param id the id to use for the new card.
  * @param ref the ref to use for the new card.
  */
case class CardCreationContext(
  user: User,
  now: DateTime,
  id: String,
  ref: Int
) extends CardEventContextLike

/**
  * The context for a card update.
  * 
  * @param user the user that is creating the card.
  * @param now the current datetime to consider.
  */
case class CardUpdateContext(
  user: User,
  now: DateTime
) extends CardEventContextLike

/**
  * A paged result with the matched ids for a query to find cards.
  * 
  * @param ids the ids of the cards that are part of this page of results
  * @param countOfItems the total number of items matched
  */
final case class IdsFindResult(ids: Seq[String], countOfItems: Integer)

/**
  * The result of for a query to find cards.
  * 
  * @param cards the cards on this page of results.
  * @param countOfItems the total number of items matched.
  */
final case class FindResult(cards: Seq[CardData], countOfItems: Int)

/**
  * The base trait for a CardRepository.
  * 
  * A `CardRepository` is responsible for the persistence and retrival
  * of every data related to a card. This include tags and queries
  * using specialized services (e.g. ElasticSearch).
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
  * The base trait for a a card query service like ElasticSearch.
  * 
  * It's single responsibility is to store data in a text search
  * optimized way, so we can use the `findIds` method to run a
  * query and find the matched ids.
  * 
  * All other method exists so we can ensure the data on this service
  * is in sync with the data in the other persistance services for
  * cards.
  */
trait CardElasticClientLike {

  /**
    * Creates a new entry on ElasticSearch for a new card.
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
  * A manager for card history, keeping tack of historical events like
  * card creation, deletion and update.
  */
trait CardHistoryRecorderLike {

  /**
    * Register the creation of a card.
    */
  def registerCreation(context: CardCreationContext)(implicit c: Connection): Unit

  /**
    * Register the deletion of a card.
    */
  def registerDeletion(cardId: String, context: CardUpdateContext)(implicit c: Connection): Unit

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
        val findResult = dataRepo.find(idsResult)
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
