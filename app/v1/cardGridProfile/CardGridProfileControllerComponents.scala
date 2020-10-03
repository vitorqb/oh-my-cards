package v1.cardGridProfile

import com.google.inject.Inject
import play.api.mvc.ControllerComponents
import scala.concurrent.ExecutionContext
import play.api.http.FileMimeTypes
import play.api.i18n.Langs
import play.api.i18n.MessagesApi
import play.api.mvc.PlayBodyParsers
import com.mohiva.play.silhouette.api.Silhouette
import v1.auth.DefaultEnv
import play.api.mvc.DefaultActionBuilder

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
