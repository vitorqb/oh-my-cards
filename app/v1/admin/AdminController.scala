package v1.admin

import play.api.mvc.BaseController
import com.google.inject.Inject
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class AdminController @Inject() (
    val controllerComponents: AdminControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseController {

  def index() =
    Action.async { implicit request =>
      ???
    }

  def synchronizeElasticSearch() =
    Action.async { implicit request =>
      controllerComponents.elasticSearchSynchronizer.run()
      Future.successful(Ok)
    }

  def version() =
    controllerComponents.silhouette.UserAwareAction.async { implicit request =>
      Future.successful {
        Ok(
          controllerComponents.config
            .getOptional[String]("app.version")
            .getOrElse("")
        )
      }
    }
}
