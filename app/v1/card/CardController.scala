package v1.card

import scala.util.{Try,Success,Failure}

import javax.inject.Inject
import play.api.mvc._
import play.api.Logger
import play.api.libs.json.{Json,JsValue}
import play.api.data.Form
import com.mohiva.play.silhouette.api.Silhouette

import v1.auth.{DefaultEnv}
import v1.auth.User
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import services.InputParser
import play.api.libs.json.JsPath.json
import play.api.i18n.MessagesProvider
import utils.StringUtils

/**
  * Represents the user-inputted data for a card.
  */
case class CardFormInput(title: String, body: Option[String], tags: Option[List[String]]) {

  def asCardData: CardData = CardData(None, title, getBody, getTags)
  def getTitle: String = title
  def getBody: String = body.getOrElse("")
  def getTags: List[String] = tags.getOrElse(List())

}

object CardFormInput {
  val tagMinLength = 1
  val tagMaxLength = 100

  val form: Form[CardFormInput] = {
    import play.api.data.Forms.{list => fList, _}
    Form(
      mapping(
        "title" -> nonEmptyText,
        "body" -> optional(text),
        "tags" -> optional(fList(text(CardFormInput.tagMinLength, CardFormInput.tagMaxLength)))
      )(CardFormInput.apply)(CardFormInput.unapply)
    )

  }
}

/**
  * Represents the user-inputted data for a request for a list of cards.
  */
case class CardListRequestInput(
  page: Int,
  pageSize: Int,
  tags: Option[String],
  tagsNot: Option[String]) {

  def tagsList: List[String] = tags.map(StringUtils.splitByComma).getOrElse(List())
  def tagsNotList: List[String] = tagsNot.map(StringUtils.splitByComma).getOrElse(List())
  def toCardListRequest(u: User): CardListRequest =
    CardListRequest(page, pageSize, u.id, tagsList, tagsNotList)

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

  /**
    * Endpoint used to query for a list of pages.
    */
  def list() = silhouette.SecuredAction {
    implicit request =>
    import CardListRequestParser._

    logger.info(s"Getting cards for user ${request.identity}")
    CardListRequestParser.parse() match {
      case Right(js) => BadRequest(js)
      case Left(input: CardListRequestInput) => {
        val user = request.identity
        val cards = resourceHandler.find(input.toCardListRequest(user))
        Ok(Json.toJson(cards))
      }
    }
  }

  /**
    * Endpoint used to create a new card.
    */
  def create = silhouette.SecuredAction { implicit request =>
    logger.info("Handling create card action...")
    CardFormInput.form.bindFromRequest().fold(
      _ => BadRequest("Invalid post data!"),
      cardFormInput => resourceHandler.create(cardFormInput, request.identity) match {
        case Success(card) => Ok(Json.toJson(card))
        case Failure(e) => handleCreateFailure(e)
      }
    )
  }

  /**
    * Endpoint used to update an existing card.
    */
  def update(id: String) = silhouette.SecuredAction.async { implicit request =>
    import InputParser._
    logger.info(s"Updating card with id ${id}")
    parseUUID(id) match {
      case Bad(x) => Future(NotFound)
      case Good(id) => CardFormInput.form.bindFromRequest().fold(
        f => Future(BadRequest(f.errorsAsJson)),
        cardFormInput => resourceHandler.update(id, cardFormInput, request.identity).map {
          case Failure(e: CardDoesNotExist) => NotFound
          case Failure(e) => unknownError(e)
          case Success(card) => Ok(Json.toJson(card))
        }
      )
    }
  }

  /**
    * Endpoint used to delete a card.
    */
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

  private val form: Form[CardListRequestInput] = {
    import play.api.data.Forms._

    Form(
      mapping(
        "page" -> number,
        "pageSize" -> number,
        "tags" -> optional(text),
        "tagsNot" -> optional(text)
      )(CardListRequestInput.apply)(CardListRequestInput.unapply)
    )
  }

  def parse()(
    implicit request: Request[AnyContent],
    mp: MessagesProvider
  ): Either[CardListRequestInput, JsValue] = {
    form.bindFromRequest().fold(f => Right(f.errorsAsJson), x => Left(x))
  }
}
