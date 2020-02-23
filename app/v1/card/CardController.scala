package v1.card

import scala.util.{Try,Success,Failure}

import javax.inject.Inject
import play.api.mvc._
import play.api.Logger
import play.api.libs.json.Json
import play.api.data.Form
import com.mohiva.play.silhouette.api.Silhouette

import v1.auth.{DefaultEnv}
import v1.auth.User

/**
  * Represents the user-inputted data for a card.
  */
case class CardFormInput(title: String, body: String)

/**
  * A controller for cards, defining the actions for the cards.
  */
class CardController @Inject()(
  val controllerComponents: ControllerComponents,
  val resourceHandler: CardResourceHandler,
  val silhouette: Silhouette[DefaultEnv])
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

  def index = throw new NotImplementedError

  def create = silhouette.SecuredAction { implicit request =>
    logger.info("Handling create card action...")
    form.bindFromRequest().fold(
      _ => BadRequest("Invalid post data!"),
      cardFormInput => resourceHandler.create(cardFormInput, request.identity) match {
        case Success(card) => Ok(Json.toJson(card))
        case Failure(e) => handleCreateFailure(e)
      }
    )
  }

  private def handleCreateFailure(e: Throwable): Result = {
    logger.error("Card could not be created", e)
    BadRequest("Unable to create the card")
  }

  def get(id: String) = silhouette.SecuredAction { implicit request =>
    resourceHandler.get(id, request.identity) match {
      case Some(card) => Ok(Json.toJson(card))
      case None => NotFound
    }
  }
}
