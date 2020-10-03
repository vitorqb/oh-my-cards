package v1.admin

import play.api.mvc.ControllerComponents
import com.google.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.http.FileMimeTypes
import play.api.i18n.Langs
import play.api.i18n.MessagesApi
import play.api.mvc.PlayBodyParsers
import com.mohiva.play.silhouette.api.Silhouette
import v1.auth.DefaultEnv
import scala.concurrent.Future
import play.api.mvc.Result
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import play.api.mvc.ActionFilter

import v1.admin.elasticSearchSynchronizer.ElasticSearchSynchornizer
import play.api.Configuration

/**
  * Just a helper to declare the SecuredRequestWrapper type somewhere.
  */
object AdminAction {
  type SecuredRequestWrapper[B] = SecuredRequest[DefaultEnv, B]
}


/**
  * An action that is only allowed if the user is an admin.
  */
class AdminAction[A] @Inject()(
  implicit ec: ExecutionContext
) extends ActionFilter[AdminAction.SecuredRequestWrapper]
    with play.api.mvc.Results{

  override def executionContext: ExecutionContext = ec

  override def filter[A](request: AdminAction.SecuredRequestWrapper[A]): Future[Option[Result]] = Future {
    if (request.identity.isAdmin) None
    else Some(Forbidden)
  }
}


/**
  * Plugs together all dependencies for the controller
  */
class AdminControllerComponents @Inject()(
  val executionContext: ExecutionContext,
  val fileMimeTypes: FileMimeTypes,
  val langs: Langs,
  val messagesApi: MessagesApi,
  val parsers: PlayBodyParsers,
  val silhouette: Silhouette[DefaultEnv],
  val elasticSearchSynchronizer: ElasticSearchSynchornizer,
  val config: Configuration
)(
  implicit val ec: ExecutionContext
) extends ControllerComponents {

  val actionBuilder = silhouette.SecuredAction.andThen(new AdminAction)

}
