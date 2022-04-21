package v1.card.repository

import java.sql.Connection
import v1.auth.User
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import play.api.db.Database
import v1.card.models._

/**
  * A trait for a tags repository.
  *
  * The TagsRepository's single responsibility is to store the tags and
  * it's relations to the cards.
  */
trait TagsRepositoryLike {
  def delete(cardId: String)(implicit c: Connection): Unit
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
  def create(data: CardCreateData, context: CardCreationContext)(implicit
      c: Connection
  ): Unit
  def get(id: String, user: User)(implicit c: Connection): Option[CardData]
  def getIdFromRef(ref: Int)(implicit c: Connection): Option[String]
  def find(idsResult: IdsFindResult)(implicit c: Connection): FindResult
  def delete(id: String, user: User)(implicit c: Connection): Unit
  def update(data: CardData, context: CardUpdateContext)(implicit
      c: Connection
  ): Unit
  def getAllTags(user: User)(implicit c: Connection): List[String]
}

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
  def getByRef(cardRef: Int, user: User): Future[Option[CardData]]
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
  def registerCreation(context: CardCreationContext)(implicit
      c: Connection
  ): Unit

  /**
    * Register the deletion of a card.
    */
  def registerDeletion(context: CardUpdateContext)(implicit c: Connection): Unit

  /**
    * Register the update of a card.
    */
  def registerUpdate(newCard: CardData, context: CardUpdateContext)(implicit
      c: Connection
  ): Unit

}

/**
  * A manager for card permission
  */
trait UserCardPermissionManagerLike {

  /**
    * Gives an user permission to a card.
    */
  def givePermission(user: User, cardId: String)(implicit
      c: Connection
  ): Future[Unit]

  /**
    * Returns true if an user has permission to a card.
    */
  def hasPermission(user: User, cardId: String)(implicit
      c: Connection
  ): Future[Boolean]

}

/**
  * The implementation
  */
class CardRepository(
    dataRepo: CardDataRepositoryLike,
    tagsRepo: TagsRepositoryLike,
    esClient: CardElasticClientLike,
    historyRecorder: CardHistoryRecorderLike,
    permissionManager: UserCardPermissionManagerLike,
    db: Database
)(implicit
    ec: ExecutionContext
) extends CardRepositoryLike {

  override def create(
      data: CardCreateData,
      context: CardCreationContext
  ): Future[String] =
    Future {
      db.withTransaction { implicit c =>
        dataRepo.create(data, context)
        tagsRepo.create(context.id, data.tags)
        esClient.create(data, context)
        historyRecorder.registerCreation(context)
        permissionManager.givePermission(context.user, context.id)

        context.id
      }
    }

  override def get(id: String, user: User): Future[Option[CardData]] =
    Future {
      db.withConnection { implicit c =>
        dataRepo.get(id, user).map { data =>
          tagsRepo.fill(data)
        }
      }
    }

  override def getByRef(ref: Int, user: User): Future[Option[CardData]] =
    Future {
      db.withConnection { implicit c =>
        dataRepo.getIdFromRef(ref) match {
          case None     => Future.successful(None)
          case Some(id) => get(id, user)
        }
      }
    }.flatten

  override def find(data: CardListData): Future[FindResult] =
    esClient.findIds(data).map { idsResult =>
      db.withConnection { implicit c =>
        val findResult = dataRepo.find(idsResult)
        val cardsWithTags = findResult.cards.map(x => tagsRepo.fill(x))
        findResult.copy(cards = cardsWithTags)
      }
    }

  override def delete(
      data: CardData,
      context: CardUpdateContext
  ): Future[Unit] =
    Future {
      db.withTransaction { implicit c =>
        dataRepo.delete(data.id, context.user)
        tagsRepo.delete(data.id)
        esClient.delete(data.id)
        historyRecorder.registerDeletion(context)
      }
    }

  override def update(
      data: CardData,
      context: CardUpdateContext
  ): Future[Unit] =
    Future {
      db.withTransaction { implicit c =>
        dataRepo.update(data, context)
        tagsRepo.update(data)
        esClient.update(data, context)
        historyRecorder.registerUpdate(data, context)
      }
    }

  override def getAllTags(user: User): Future[List[String]] =
    Future {
      db.withConnection { implicit c =>
        dataRepo.getAllTags(user)
      }
    }

}
