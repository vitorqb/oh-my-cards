package v1.card

import java.sql.Connection
import org.joda.time.DateTime
import v1.auth.User
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import play.api.db.Database

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
  * The basic data needed to create a card.
  */
final case class CardCreateData(title: String, body: String, tags: List[String])

/**
  * The basic data needed to list cards.
  */
final case class CardListData(
  page: Int,
  pageSize: Int,
  userId: String,
  tags: List[String],
  tagsNot: List[String],
  query: Option[String],
  searchTerm: Option[String]
)

/**
  * A trait for card data repository.
  * 
  * The CardDataRepository is the persistance and retrival layer for
  * the base data of a card, usually stored in the main SQL table if
  * using an SQL layer.
  */
trait CardDataRepositoryLike {
  def create(data: CardCreateData, context: CardCreationContext)(implicit c: Connection): Unit
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
) extends CardEventContextLike {

  def genCardData(data: CardCreateData) = 
    CardData(id, data.title, data.body, data.tags, Some(now), Some(now), ref)
}

/**
  * The context for a card update.
  * 
  * @param user the user that is creating the card.
  * @param now the current datetime to consider.
  */
case class CardUpdateContext(
  user: User,
  now: DateTime,
  oldData: CardData
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
  def create(data: CardCreateData, context: CardCreationContext): Future[String]
  def get(id: String, user: User): Future[Option[CardData]]
  def find(data: CardListData): Future[FindResult]
  def delete(data: CardData, context: CardUpdateContext): Future[Unit]
  def update(data: CardData, context: CardUpdateContext): Future[Unit]
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
  def create(data: CardCreateData, context: CardCreationContext): Unit

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
  def findIds(data: CardListData): Future[IdsFindResult]
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
  def registerDeletion(context: CardUpdateContext)(implicit c: Connection): Unit

  /**
    * Register the update of a card.
    */
  def registerUpdate(newCard: CardData, context: CardUpdateContext)(implicit c: Connection): Unit

}

/**
  * The implementation
  */
class CardRepository(
  dataRepo: CardDataRepositoryLike,
  tagsRepo: TagsRepositoryLike,
  esClient: CardElasticClientLike,
  historyRecorder: CardHistoryRecorderLike,
  db: Database
)(
  implicit ec: ExecutionContext
) extends CardRepositoryLike {

  override def create(data: CardCreateData, context: CardCreationContext): Future[String] = Future {
    db.withTransaction { implicit c =>
      dataRepo.create(data, context)
      tagsRepo.create(context.id, data.tags)
      esClient.create(data, context)
      historyRecorder.registerCreation(context)

      context.id
    }
  }

  override def get(id: String, user: User): Future[Option[CardData]] = Future {
    db.withTransaction { implicit c =>
      dataRepo.get(id, user).map { data =>
        tagsRepo.fill(data)
      }
    }
  }

  override def find(data: CardListData): Future[FindResult] =
    esClient.findIds(data).map { idsResult =>
      db.withConnection { implicit c =>
        val findResult = dataRepo.find(idsResult)
        val cardsWithTags = findResult.cards.map(x => tagsRepo.fill(x))
        findResult.copy(cards=cardsWithTags)
      }
    }

  override def delete(data: CardData, context: CardUpdateContext): Future[Unit] = Future {
    db.withTransaction { implicit c =>
      dataRepo.delete(data.id, context.user)
      tagsRepo.delete(data.id)
      esClient.delete(data.id)
      historyRecorder.registerDeletion(context)
    }
  }

  override def update(data: CardData, context: CardUpdateContext): Future[Unit] = Future {
    db.withTransaction { implicit c =>
      dataRepo.update(data, context)
      tagsRepo.update(data)
      esClient.update(data, context)
      historyRecorder.registerUpdate(data, context)
    }
  }

  override def getAllTags(user: User): Future[List[String]] = Future {
    db.withConnection { implicit c =>
      dataRepo.getAllTags(user)
    }
  }

}
