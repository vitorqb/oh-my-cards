package v1.card

import javax.inject.Inject
import play.api.mvc._
import play.api.Logger
import play.api.libs.json.Json

class CardController @Inject()(val controllerComponents: ControllerComponents)
    extends BaseController {

  private val logger = Logger(getClass)

  def index = Action {
    logger.info("Handling index message...")
    val allCards = (new CardResourceHandler).find
    Ok(Json.toJson(allCards))
  }

}
