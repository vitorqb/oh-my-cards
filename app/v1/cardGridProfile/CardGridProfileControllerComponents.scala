package v1.cardGridProfile

import play.api.mvc.ActionBuilder
import com.google.inject.Inject
import play.api.mvc.Request
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import scala.concurrent.ExecutionContext
import play.api.http.FileMimeTypes
import play.api.i18n.Langs
import play.api.i18n.MessagesApi
import play.api.mvc.PlayBodyParsers
import com.mohiva.play.silhouette.api.Silhouette
import v1.auth.DefaultEnv
import play.api.mvc.DefaultActionBuilder
import play.api.mvc.Result
import play.api.Logger
import play.api.mvc.Results
import play.api.libs.json.Json

/**
  * Plugs together all dependencies for the controller
  */
class CardGridProfileControllerComponents @Inject()(
  val actionBuilder: DefaultActionBuilder,
  val executionContext: ExecutionContext,
  val fileMimeTypes: FileMimeTypes,
  val langs: Langs,
  val messagesApi: MessagesApi,
  val parsers: PlayBodyParsers,
  val silhouette: Silhouette[DefaultEnv],
  val handler: CardGridProfileResourceHandler
) extends ControllerComponents
