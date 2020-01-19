package v1.card

import play.api.libs.json.{Json,Format}

/**
  * Data transfer object for a card.
  */
case class CardResource(id: String, link: String, title: String, body: String)

object CardResource {
  implicit val format: Format[CardResource] = Json.format
}
