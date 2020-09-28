package v1.card

import java.sql.Connection
import org.joda.time.DateTime
import v1.auth.User
import scala.util.Try
import scala.concurrent.Future
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import scala.concurrent.ExecutionContext
import scala.util.Success
import scala.util.Failure

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
  def create(cardFormInput: CardFormInput, context: CardCreationContext)(implicit c: Connection): Try[String]
  def get(id: String, user: User)(implicit c: Connection): Option[CardData]
  def find(request: CardListRequest, idsResult: CardElasticIdFinder.Result)(implicit c: Connection): Future[FindResult]
  def delete(id: String, user: User)(implicit c: Connection): Future[Try[Unit]]
  def update(data: CardData, context: CardUpdateContext)(implicit c: Connection): Future[Try[Unit]]
  def getAllTags(user: User)(implicit c: Connection): Future[List[String]]
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
  * The result of a find query.
  */
final case class FindResult(cards: Seq[CardData], countOfItems: Int)

object FindResult {

  /**
    * Alternative constructor from the results of the query for the IDs and the query for
    * the data.
    */
  def fromQueryResults(
    cardData: Seq[CardData],
    idsResult: CardElasticIdFinder.Result
  ): FindResult = {
    val cardDataById = cardData.map(x => (x.id, x)).toMap
    val sortedCardData = idsResult.ids.map(x => cardDataById.get(x)).flatten
    FindResult(sortedCardData, idsResult.countOfIds)
  }

}

/**
  * The base trait for a CardRepository.
  */
trait CardRepositoryLike {
  def create(cardFormInput: CardFormInput, user: User): Try[String]
  def get(id: String, user: User): Option[CardData]
  def find(request: CardListRequest): Future[FindResult]
  def delete(id: String, user: User): Future[Try[Unit]]
  def update(data: CardData, user: User): Future[Try[Unit]]
  def getAllTags(user: User): Future[List[String]]
}

/**
  * The implementation
  */
class CardRepository(
  dataRepo: CardDataRepositoryLike,
  tagsRepo: TagsRepositoryLike,
  esClient: CardElasticClient,
  components: CardRepositoryComponentsLike
)(
  implicit ec: ExecutionContext
) extends CardRepositoryLike {

  override def create(cardFormInput: CardFormInput, user: User): Try[String] = {
    components.db.withTransaction { implicit c =>
      val now = components.clock.now
      val id = components.uuidGenerator.generate()
      val ref = components.refGenerator.nextRef()
      val context = CardCreationContext(user, now, id, ref)
      for {
        id <- dataRepo.create(cardFormInput, context)
      } yield {
        tagsRepo.create(id, cardFormInput.getTags())
        esClient.create(cardFormInput, context)
        id
      }
    }
  }

  override def get(id: String, user: User): Option[CardData] =
    components.db.withTransaction { implicit c =>
      dataRepo.get(id, user).map { data =>
        tagsRepo.fill(data)
      }
    }

  override def find(request: CardListRequest): Future[FindResult] =
    esClient.findIds(request).flatMap { esIdsResult =>
      implicit val c: Connection = components.db.getConnection()
      dataRepo.find(request, esIdsResult).map { findResult =>
        val cardsWithTags = findResult.cards.map(x => tagsRepo.fill(x))
        findResult.copy(cards=cardsWithTags)
      } andThen {
        case _ => c.close()
      }
    }

  override def delete(id: String, user: User): Future[Try[Unit]] = {
    implicit val c: Connection = components.db.getConnection(false)
    dataRepo.delete(id, user).andThen {
      case Success(_) => {
        try {
          tagsRepo.delete(id)
          esClient.delete(id)
          c.commit()
        } finally {
          c.rollback()
        }
      }
    } andThen {
      case _ => c.close()
    }
  }

  override def update(data: CardData, user: User): Future[Try[Unit]] = {
    val context = CardUpdateContext(user, components.clock.now)
    implicit val c: Connection = components.db.getConnection(false)
    dataRepo.update(data, context).andThen {
      case Success(_) => {
        try {
          tagsRepo.update(data)
          esClient.update(data, context)
          c.commit()
        } finally {
          c.rollback()
        }
      }
    }
  }

  override def getAllTags(user: User): Future[List[String]] = {
    implicit val c: Connection = components.db.getConnection(false)
    dataRepo.getAllTags(user).andThen(_ => c.close())
  }

}
