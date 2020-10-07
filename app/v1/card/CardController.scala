package v1.card
 

import javax.inject.Inject
import play.api.mvc._
import play.api.Logger
import play.api.libs.json.{Json,JsValue}
import play.api.data.Form
import com.mohiva.play.silhouette.api.Silhouette

import v1.auth.{DefaultEnv}
import v1.auth.User
import v1.card.historytrackerhandler.HistoryTrackerHandlerLike
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import services.InputParser
import play.api.i18n.MessagesProvider
import utils.StringUtils
import org.joda.time.DateTime

/**
  * Represents the user-inputted data for a card.
  */
case class CardFormInput(title: String, body: Option[String], tags: Option[List[String]]) {

  def asCardData(id: String, createdAt: Option[DateTime], updatedAt: Option[DateTime], ref: Int): CardData =
    CardData(id, getTitle(), getBody(), getTags(), createdAt, updatedAt, ref)
  def asCardData(creationContext: CardCreationContext): CardData =
    asCardData(creationContext.id, Some(creationContext.now), Some(creationContext.now), creationContext.ref)
  def getTitle(): String = title
  def getBody(): String = body.getOrElse("")
  def getTags(): List[String] = tags.getOrElse(List())

}

object CardFormInput {
  val form: Form[CardFormInput] = {
    import play.api.data.Forms.{list => _, _}
    import utils.TagsUtils.Forms._
    Form(
      mapping(
        "title" -> nonEmptyText,
        "body" -> optional(text),
        "tags" -> optional(tags)
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
  tagsNot: Option[String],
  query: Option[String],
  searchTerm: Option[String] = None) {

  def tagsList: List[String] = tags.map(StringUtils.splitByComma).getOrElse(List())
  def tagsNotList: List[String] = tagsNot.map(StringUtils.splitByComma).getOrElse(List())
  def toCardListRequest(u: User): CardListRequest =
    CardListRequest(page, pageSize, u.id, tagsList, tagsNotList, query, searchTerm)

}

/**
  * A controller for cards, defining the actions for the cards.
  */
class CardController @Inject()(
  val controllerComponents: ControllerComponents,
  val resourceHandler: CardResourceHandler,
  val silhouette: Silhouette[DefaultEnv],
  val historyTrackerHandler: HistoryTrackerHandlerLike
)(
  implicit val ec: ExecutionContext
)
    extends BaseController with play.api.i18n.I18nSupport {

  private val logger = Logger(getClass)

  /**
    * Endpoint used to query for a list of pages.
    */
  def list() = silhouette.SecuredAction.async {
    implicit request =>

    logger.info(s"Getting cards for user ${request.identity}")
    CardListRequestParser.parse() match {
      case Right(js) => Future.successful(BadRequest(js))
      case Left(input: CardListRequestInput) => {
        val user = request.identity
        val cardListRequest = input.toCardListRequest(user)
        resourceHandler.find(cardListRequest).map(cards => Ok(Json.toJson(cards)))
      }.recover(handleError _)
    }
  }

  /**
    * Endpoint used to create a new card.
    */
  def create = silhouette.SecuredAction.async { implicit request =>
    logger.info("Handling create card action...")
    CardFormInput.form.bindFromRequest().fold(
      f => Future.successful(BadRequest(f.errorsAsJson)),
      cardFormInput => resourceHandler.create(cardFormInput, request.identity) map {
        card => Ok(Json.toJson(card))
      } recover {
        e => handleError(e)
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
        cardFormInput => resourceHandler.update(id, cardFormInput, request.identity) map {
          card => Ok(Json.toJson(card))
        } recover {
          e => handleError(e)
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
        _ => {
          logger.info(s"Deleted id $id!")
          NoContent
        }
      } recover {
        e => handleError(e)
      }
    }
  }

  /**
    * Returns the metadata for the cards.
    */
  def getMetadata() = silhouette.SecuredAction.async { implicit request =>
    logger.info(s"Getting metadata for cards...")

    def serialize(m: CardMetadataResource) = Ok(Json.toJson(m))

    resourceHandler.getMetadata(request.identity).map(serialize _).recover(handleError _)
  }

  /**
    * Returns the history for a card.
    */
  def getHistory(id: String) = silhouette.SecuredAction.async { implicit request =>
    resourceHandler.get(id, request.identity) flatMap {
      case None    => throw new CardDoesNotExist
      case Some(_) => historyTrackerHandler.get(id).map(x => Ok(Json.toJson(x)))
    } recover {
      e => handleError(e)
    }
  }

  /**
    * Handles errors.
    */
  private def handleError(error: Throwable): Result = error match {
    case userErr: CardRepositoryUserException => {
      logger.error("A card repository user error was thrown", userErr)
      userErr match {
        case CardDoesNotExist(_, _) => NotFound
        case TagsFilterMiniLangSyntaxError(m, _) => BadRequest(Json.obj("message" -> m))
      }
    }
    case _ => {
      logger.error("Unexpected error!", error)
      InternalServerError("Unknown error!")
    }
  }

  def get(id: String) = silhouette.SecuredAction.async { implicit request =>
    resourceHandler.get(id, request.identity) map {
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
        "tagsNot" -> optional(text),
        "query" -> optional(text),
        "searchTerm" -> optional(text)
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
