package v1.card

import javax.inject.Inject
import scala.util.{Try,Success,Failure}
import play.api.libs.json.{Json,Format}

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

  def find: Iterable[CardResource] = {
    //!!!! TODO -> Implemente find.
    List(CardResource("1", "/foo", "Foo", "Foo Bar Baz"))
  }

  def create(input: CardFormInput) = {
    val cardData = CardData(None, input.title, input.body)
    repository.create(cardData).flatMap(createdDataId =>
      get(createdDataId) match {
        case Some(cardResource) => Success(cardResource)
        case None => Failure(new Exception("Could not find created resource!"))
      }
    )
  }

  def get(id: String) = {
    repository.get(id).map(CardResource.fromCardData)
  }
}
