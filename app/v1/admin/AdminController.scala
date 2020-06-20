package v1.admin

import play.api.mvc.BaseController
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.Silhouette
import v1.auth.{DefaultEnv}
import play.api.mvc.ControllerComponents
import scala.concurrent.Future

class AdminController @Inject()(
  val controllerComponents: AdminControllerComponents
) extends BaseController {

  def index() = Action.async { implicit request =>
    ???
  }

  def synchronizeElasticSearch() = Action.async { implicit request =>
    controllerComponents.elasticSearchSynchronizer.run()
    Future.successful(Ok)
  }

  def version() = controllerComponents.silhouette.UnsecuredAction.async { implicit request =>
    Future.successful(Ok(Option(getClass().getPackage().getImplementationVersion()).getOrElse("")))
  }
}
