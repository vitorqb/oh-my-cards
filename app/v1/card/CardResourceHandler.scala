package v1.card

import javax.inject.Inject
import scala.util.{Try,Success,Failure}
import play.api.libs.json.{Json,Format}
import v1.auth.User

/**
  * Data transfer object for a card.
  */
case class CardResource(id: String, link: String, title: String, body: String)

object CardResource {

  implicit val format: Format[CardResource] = Json.format

  def fromCardData(cardData: CardData) = {
    CardResource(cardData.id.fold("")(x => x), "", cardData.title, cardData.body)
  }

}

class CardResourceHandler @Inject()(val repository: CardRepositoryImpl) {

  def find: Iterable[CardResource] = throw new NotImplementedError

  def create(input: CardFormInput, user: User) = {
    val cardData = CardData(None, input.title, input.body)
    repository.create(cardData, user).flatMap(createdDataId =>
      get(createdDataId, user) match {
        case Some(cardResource) => Success(cardResource)
        case None => Failure(new Exception("Could not find created resource!"))
      }
    )
  }

  def get(id: String, user: User) = {
    repository.get(id, user).map(CardResource.fromCardData)
  }
}
