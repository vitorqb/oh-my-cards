package v1.card

import java.sql.Connection
import org.joda.time.DateTime
import v1.auth.User
import scala.util.Try
import scala.concurrent.Future
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike

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
}

/**
  * A trait for card data repository.
  */
trait CardDataRepositoryLike {
  def create(cardFormInput: CardFormInput, context: CardCreationContext)(implicit c: Connection): Try[String]
  def get(id: String, user: User): Option[CardData]
  def find(request: CardListRequest): Future[FindResult]
  def delete(id: String, user: User): Future[Try[Unit]]
  def update(data: CardData, user: User): Future[Try[Unit]]
  def getAllTags(user: User): Future[List[String]]
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
case class CardCreationContext(
  user: User,
  now: DateTime,
  id: String,
  ref: Int
)

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
    dataRepo.get(id, user)

  override def find(request: CardListRequest): Future[FindResult] =
    dataRepo.find(request)

  override def delete(id: String, user: User): Future[Try[Unit]] =
    dataRepo.delete(id, user)

  override def update(data: CardData, user: User): Future[Try[Unit]] =
    dataRepo.update(data, user)

  override def getAllTags(user: User): Future[List[String]] =
    dataRepo.getAllTags(user)

}
