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
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import services.InputParser

/**
  * Represents the user-inputted data for a card.
  */
case class CardFormInput(title: String, body: Option[String]) {

  def asCardData: CardData = CardData(None, title, body.getOrElse(""))

}


/**
  * Represents the user-inputted data for a request for a list of cards.
  */
case class CardListRequestInput(page: Int, pageSize: Int) {

  def toCardListRequest(u: User) = CardListRequest(page, pageSize, u.id)

}


/**
  * A controller for cards, defining the actions for the cards.
  */
class CardController @Inject()(
  val controllerComponents: ControllerComponents,
  val resourceHandler: CardResourceHandler,
  val silhouette: Silhouette[DefaultEnv])(
  implicit val ec: ExecutionContext)
    extends BaseController with play.api.i18n.I18nSupport {

  private val logger = Logger(getClass)

  private val form: Form[CardFormInput] = {
    import play.api.data.Forms._

    Form(
      mapping(
        "title" -> nonEmptyText,
        "body" -> optional(text)
      )(CardFormInput.apply)(CardFormInput.unapply)
    )

  }

  /**
    * Endpoint used to query for a list of pages.
    */
  def list(page: Option[String], pageSize: Option[String]) = silhouette.SecuredAction {
    implicit request =>
    import CardListRequestParser._

    logger.info(s"Getting cards for user ${request.identity} with page=$page, pageSize=$pageSize")
    CardListRequestParser.parse(page, pageSize) match {
      case Bad(msg) => BadRequest(msg)
      case Good(input: CardListRequestInput) => {
        val user = request.identity
        val cards = resourceHandler.find(input.toCardListRequest(user))
        Ok(Json.toJson(cards))
      }
    }
  }

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

  def update(id: String) = silhouette.SecuredAction.async { implicit request =>
    import InputParser._
    logger.info(s"Updating card with id ${id}")
    parseUUID(id) match {
      case Bad(x) => Future(NotFound)
      case Good(id) => form.bindFromRequest().fold(
        f => Future(BadRequest(f.errorsAsJson)),
        cardFormInput => resourceHandler.update(id, cardFormInput, request.identity).map {
          case Failure(e: CardDoesNotExist) => NotFound
          case Failure(e) => unknownError(e)
          case Success(card) => Ok(Json.toJson(card))
        }
      )
    }
  }

  def delete(id: String) = silhouette.SecuredAction.async { implicit request =>
    import InputParser._
    logger.info(s"Handling delete card action for id $id...")
    parseUUID(id) match {
      case Bad(x) => Future(NotFound(x))
      case Good(x) => resourceHandler.delete(x, request.identity).map {
        case Success(_) => NoContent
        case Failure(e: CardDoesNotExist) => NotFound
        case Failure(e) => unknownError(e)
      }
    }
  }

  private def handleCreateFailure(e: Throwable): Result = {
    logger.error("Card could not be created", e)
    BadRequest("Unable to create the card")
  }

  private def unknownError(e: Throwable) = {
    logger.error("Unexpected error during update", e)
    InternalServerError("Unknown error!")
  }

  def get(id: String) = silhouette.SecuredAction { implicit request =>
    resourceHandler.get(id, request.identity) match {
      case Some(card) => Ok(Json.toJson(card))
      case None => NotFound
    }
  }
}


/**
  * Helper object to parse CardListRequest inputs.
  */
object CardListRequestParser {

  /**
    * Parsing result. Good means a valid parsed value, Bad means an error msg.
    */
  sealed trait Result
  case class Good(parsed: CardListRequestInput) extends Result
  case class Bad(message: String) extends Result

  val missingPage = Bad("Missing page.")
  val missingPageSize = Bad("Missing page size.")
  val genericError = Bad("Invalid parameters.")

  private val form: Form[CardListRequestInput] = {
    import play.api.data.Forms._

    Form(
      mapping(
        "page" -> number,
        "pageSize" -> number
      )(CardListRequestInput.apply)(CardListRequestInput.unapply)
    )
  }

  def parse(page: Option[String], pageSize: Option[String]): Result = {
    (page, pageSize) match {
      case (None, Some(_)) | (None, None) => missingPage
      case (Some(_), None) => missingPageSize
      case (Some(x), Some(y)) => form.bind(Map("page" -> x, "pageSize" -> y)).fold(
        _ => genericError,
        cardListReq => Good(cardListReq)
      )
    }

  }

}
