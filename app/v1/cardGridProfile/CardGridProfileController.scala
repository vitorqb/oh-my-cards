package v1.cardGridProfile

import play.api.mvc.BaseController
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.Silhouette
import v1.auth.DefaultEnv
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import play.api.data.Form
import play.api.i18n.{MessagesProvider, I18nSupport}
import play.api.mvc.Request
import play.api.mvc.AnyContent
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import play.api.mvc.Result
import scala.util.Success
import scala.util.Failure
import play.api.libs.json.Json
import play.api.Logger

/**
  * A controller for the Cards Grid Profiles.
  */
class CardGridProfileController @Inject()(
  val controllerComponents: CardGridProfileControllerComponents
) extends BaseController with I18nSupport {

  val silhouette = controllerComponents.silhouette
  val handler = controllerComponents.handler
  implicit val ec = controllerComponents.executionContext
  val logger = Logger(getClass())

  /**
    * Creates a CardGridProfile from a request.
    */
  def create = silhouette.SecuredAction.async { implicit request => {

    def handleValidInput(input: CardGridProfileInput) = {
      logger.info(s"Valid input: $input")
      handler.create(input, request.identity).map(resourceToResult _).recover(resultFromError _)
    }

    logger.info(s"Trying to create from request $request")
    CardGridProfileInput.form.bindFromRequest.fold(handleInvalidInput, handleValidInput)
  }}

  /**
    * Reads a CardGridProfile from a request.
    */
  def read(name: String) = silhouette.SecuredAction.async { implicit request => {
    logger.info(s"Getting from name $name for user ${request.identity}")
    handler.read(name, request.identity).map(maybeResourceToResult _).recover(resultFromError _)
  }}

  /**
    * Updates a CardGridProfile from a request.
    */
  def update(name: String) = silhouette.SecuredAction.async { implicit request =>

    def handleValidInput(input: CardGridProfileInput) = {
      logger.info(s"Valid input: $input")
      handler
        .update(name, request.identity, input)
        .map(maybeResourceToResult _)
        .recover(resultFromError _)
    }

    logger.info(s"Updating profile with name [$name] for user [$request.identity].")
    CardGridProfileInput.form.bindFromRequest.fold(handleInvalidInput, handleValidInput)
  }

  /**
    * Returns a list with the name for all CardGridProfiles.
    */
  def listNames = silhouette.SecuredAction.async { implicit request =>
    handler.listNames(request.identity).map(names => Ok(Json.obj("names" -> names)))
  }

  /**
    * Maps an error during the handling of a request to a Result.
    */
  def resultFromError(error: Throwable): Result = {
    logger.info(s"Handling error $error")
    error match {
      case e: ProfileNameAlreadyExists => BadRequest(Json.obj("error" -> e.message))
      case _ => InternalServerError(Json.obj("error" -> "Unknown Error"))
    }
  }

  /**
    * Maps a resource into a Result.
    */
  def resourceToResult(resource: CardGridProfileResource): Result = {
    logger.info(s"Returning resource: %resource")
    Ok(Json.toJson(resource))
  }

  /**
    * Maps a maybe resource into a Result.
    */
  def maybeResourceToResult(maybeResource: Option[CardGridProfileResource]) = {
    maybeResource match {
      case Some(resource) => resourceToResult(resource)
      case None => NotFound
    }
  }

  /**
    * Handles when an invalid input was received.
    */
  def handleInvalidInput[A](invalidForm: Form[A])(implicit r: Request[AnyContent]) = {
    logger.info(s"Invalid input: $invalidForm")
    Future.successful(BadRequest(invalidForm.errorsAsJson))
  }
}

/**
  * Input object for a Cards Grid Profile
  */
case class CardGridProfileInput(name: String, config: CardGridConfigInput) {

  def copyConfig(
    page: Option[Int] = config.page,
    pageSize: Option[Int] = config.pageSize,
    includeTags: Option[List[String]] = config.includeTags,
    excludeTags: Option[List[String]] = config.excludeTags,
    query: Option[String] = config.query
  ): CardGridProfileInput =
    this.copy(config=config.copy(page, pageSize, includeTags, excludeTags, query))

}

object CardGridProfileInput {

  val form: Form[CardGridProfileInput] = {
    import play.api.data.Forms.{list => fList, _}
    import utils.TagsUtils.Forms._
    Form(
      mapping(
        "name" -> text,
        "config" -> mapping(
          "page" -> optional(number),
          "pageSize" -> optional(number),
          "includeTags" -> optional(tags),
          "excludeTags" -> optional(tags),
          "query" -> optional(text)
        )(CardGridConfigInput.apply)(CardGridConfigInput.unapply)
      )(CardGridProfileInput.apply)(CardGridProfileInput.unapply)
    )
  }

  def fromData(d: CardGridProfileData): CardGridProfileInput = CardGridProfileInput(
    d.name,
    CardGridConfigInput.fromData(d.config)
  )
}

/**
  * Input object for a Cards Grid Configuration.
  */
case class CardGridConfigInput(
  page: Option[Int],
  pageSize: Option[Int],
  includeTags: Option[List[String]],
  excludeTags: Option[List[String]],
  query: Option[String])

object CardGridConfigInput {

  def fromData(d: CardGridConfigData): CardGridConfigInput = CardGridConfigInput(
    d.page,
    d.pageSize,
    d.includeTags,
    d.excludeTags,
    d.query
  )

}
