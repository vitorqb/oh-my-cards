package v1.card
 
import javax.inject.Inject
import scala.util.{Try,Success,Failure}
import play.api.libs.json.{Json,Format}
import v1.auth.User
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.joda.time.DateTime
import utils.JodaToJsonUtils._

/**
  * Custom exceptions.
  */
final case class InvalidCardData(
  private val message: String = "The data for a card was not valid.",
  private val cause: Throwable = None.orNull
) extends Exception(message, cause)

object InvalidCardData {
  def emptyTitle = InvalidCardData(message="Title can not be empty!")
}


//!!!! TODO Remove link
/**
  * Data transfer object for a card.
  */
case class CardResource(
  id: String,
  link: String,
  title: String,
  body: String,
  tags: List[String],
  createdAt: Option[DateTime],
  updatedAt: Option[DateTime],
  ref: Int
) {

  def asCardData: CardData = CardData(id, title, body, tags, ref=ref)

  def updateWith(cardInput: CardFormInput): Try[CardResource] = {
    if (cardInput.title == "") Failure(InvalidCardData.emptyTitle)
    else Success(this.copy(title=cardInput.title, body=cardInput.getBody, tags=cardInput.getTags))
  }

}

object CardResource {

  implicit val format: Format[CardResource] = Json.format

  def fromCardData(cardData: CardData) = {
    CardResource(
      cardData.id,
      "",
      cardData.title,
      cardData.body,
      cardData.tags,
      cardData.createdAt,
      cardData.updatedAt,
      cardData.ref
    )
  }

}

/**
  * Data transfer object for card metadata.
  */
case class CardMetadataResource(tags: List[String])

object CardMetadataResource {

  implicit val format: Format[CardMetadataResource] = Json.format

}

/**
  * Represents a request for a list of CardResource.
  */
case class CardListRequest(
  page: Int,
  pageSize: Int,
  userId: String,
  tags: List[String],
  tagsNot: List[String],
  query: Option[String],
  searchTerm: Option[String] = None
)


/**
  * Represents a response for a paginated list of CardResource.
  */
case class CardListResponse(
  page: Int,
  pageSize: Int,
  items: Iterable[CardResource],
  countOfItems: Int)

object CardListResponse {

  implicit val format: Format[CardListResponse] = Json.format

  def fromRequest(
    req: CardListRequest,
    cards: Iterable[CardResource],
    countOfCards: Int): CardListResponse = {
    CardListResponse(req.page, req.pageSize, cards, countOfCards)
  }

}

/**
  * A resource handler for Cards.
  */
class CardResourceHandler @Inject()(
  val repository: CardRepositoryLike
)(
  implicit val ec: ExecutionContext){

  def find(cardListReq: CardListRequest): Future[CardListResponse] = for {
    findResult       <- repository.find(cardListReq)
    cardDataList     = findResult.cards
    countOfCards     = findResult.countOfItems
    cardResourceList = cardDataList.map(CardResource.fromCardData(_))
    cardListResponse = CardListResponse.fromRequest(cardListReq, cardResourceList, countOfCards)
  } yield cardListResponse

  def create(input: CardFormInput, user: User): Future[CardResource] = {
    repository.create(input, user).flatMap(createdDataId =>
      get(createdDataId, user).map {
        case Some(cardResource) => cardResource
        case None => throw new RuntimeException("Could not find created resource!")
      }
    )
  }

  def delete(id: String, user: User): Future[Unit] = {
    repository.get(id, user).map {
      case Some(cardData) => repository.delete(cardData, user)
      case None           => throw new CardDoesNotExist
    }
  }

  def get(id: String, user: User): Future[Option[CardResource]] = {
    repository.get(id, user).map(_.map(CardResource.fromCardData))
  }

  def update(id: String, input: CardFormInput, user: User): Future[CardResource] =
    get(id, user).flatMap {
      case Some(oldCardResource) => oldCardResource.updateWith(input) match {
        case Success(newCardResource) => {
          repository.update(
            oldCardResource.asCardData,
            newCardResource.asCardData,
            user
          ) flatMap { _ =>
            get(id, user).map(_.get)
          }
        }
        case Failure(e) => throw e
      }
      case None => throw new CardDoesNotExist
    }

  def getMetadata(user: User): Future[CardMetadataResource] = {
    repository.getAllTags(user).map(CardMetadataResource(_))
  }
}
