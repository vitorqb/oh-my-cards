package v1.card

import javax.inject.Inject
import scala.util.{Try,Success,Failure}
import play.api.libs.json.{Json,Format}
import v1.auth.User
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

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


/**
  * Data transfer object for a card.
  */
case class CardResource(id: String, link: String, title: String, body: String, tags: List[String]) {

  def asCardData: CardData = CardData(Some(id), title, body, tags)

  def updateWith(cardInput: CardFormInput): Try[CardResource] = {
    if (cardInput.title == "") Failure(InvalidCardData.emptyTitle)
    else Success(this.copy(title=cardInput.title, body=cardInput.getBody, tags=cardInput.getTags))
  }

}

object CardResource {

  implicit val format: Format[CardResource] = Json.format

  def fromCardData(cardData: CardData) = {
    CardResource(cardData.id.fold("")(x => x), "", cardData.title, cardData.body, cardData.tags)
  }

}


/**
  * Represents a request for a list of CardResource.
  */
case class CardListRequest(page: Int, pageSize: Int, userId: String, tags: List[String])


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
  val repository: CardRepositoryImpl)(
  implicit val ec: ExecutionContext){

  def find(cardListReq: CardListRequest): CardListResponse = {
    val cards = repository.find(cardListReq).map(CardResource.fromCardData(_))
    val countOfCards = repository.countItemsMatching(cardListReq)
    CardListResponse.fromRequest(cardListReq, cards, countOfCards)
  }

  def create(input: CardFormInput, user: User): Try[CardResource] = {
    repository.create(input.asCardData, user).flatMap(createdDataId =>
      get(createdDataId, user) match {
        case Some(cardResource) => Success(cardResource)
        case None => Failure(new Exception("Could not find created resource!"))
      }
    )
  }

  def delete(id: String, user: User): Future[Try[Unit]] = {
    repository.delete(id, user)
  }

  def get(id: String, user: User): Option[CardResource] = {
    repository.get(id, user).map(CardResource.fromCardData)
  }

  def update(id: String, input: CardFormInput, user: User): Future[Try[CardResource]] = {
    repository.get(id, user) match {
      case Some(cardData) => CardResource.fromCardData(cardData).updateWith(input) match {
        case Success(cardResource) => repository.update(cardResource.asCardData, user).map {
          case Success(_) => Success(cardResource)
          case Failure(e) => Failure(e)
        }
        case Failure(e) => Future(Failure(e))
      }
      case None => Future(Failure(new CardDoesNotExist))
    }
  }
}
