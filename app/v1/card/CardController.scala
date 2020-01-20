package v1.card

import scala.util.{Try,Success,Failure}

import javax.inject.Inject
import play.api.mvc._
import play.api.Logger
import play.api.libs.json.Json
import play.api.data.Form

/**
  * Represents the user-inputted data for a card.
  */
case class CardFormInput(title: String, body: String)

/**
  * A controller for cards, defining the actions for the cards.
  */
class CardController @Inject()(
  val controllerComponents: ControllerComponents,
  val resourceHandler: CardResourceHandler)
    extends BaseController {

  private val logger = Logger(getClass)

  private val form: Form[CardFormInput] = {
    import play.api.data.Forms._

    Form(
      mapping(
        "title" -> nonEmptyText,
        "body" -> text
      )(CardFormInput.apply)(CardFormInput.unapply)
    )

  }

  //!!!! TODO -> Implement real get.
  def index = Action {
    logger.info("Handling index message...")
    Ok(Json.toJson(resourceHandler.find))
  }

  def create = Action { implicit request =>
    logger.info("Handling create card action...")
    form.bindFromRequest().fold(
      _ => BadRequest("Invalid post data!"),
      cardFormInput => resourceHandler.create(cardFormInput) match {
        case Success(card) => Ok(Json.toJson(card))
        case Failure(e) => handleCreateFailure(e)
      }
    )
  }

  private def handleCreateFailure(e: Throwable): Result = {
    logger.error("Card could not be created", e)
    BadRequest("Unable to create the card")
  }

  def get(id: String) = Action { implicit request =>
    resourceHandler.get(id) match {
      case Some(card) => Ok(Json.toJson(card))
      case None => NotFound
    }
  }
}
