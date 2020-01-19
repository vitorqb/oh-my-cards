package v1.card

import play.api.libs.json.{Json,Format}

/**
  * Data transfer object for a card.
  */
case class CardResource(id: String, link: String, title: String, body: String)

object CardResource {
  implicit val format: Format[CardResource] = Json.format
}

class CardResourceHandler {

  def find: Iterable[CardResource] = {
    //!!!! TODO -> Implemente find.
    List(CardResource("1", "/foo", "Foo", "Foo Bar Baz"))
  }

  def create(input: CardFormInput) = {
    //!!!! TODO -> Implement create.
    CardResource("2", "/2", input.title, input.body)
  }

  def get(id: String) = {
    //!!!! TODO -> Implement create.
    if (id.startsWith("1")) Some(CardResource(id, f"/$id", f"Card $id", ""))
    else None
  }
}
