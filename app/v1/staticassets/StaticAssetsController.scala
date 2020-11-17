package v1.staticassets

import com.google.inject.Inject
import play.api.mvc.ControllerComponents
import play.api.mvc.BaseController
import com.mohiva.play.silhouette.api.Silhouette
import v1.auth.DefaultEnv
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import services.filerepository.FileRepositoryLike
import java.io.FileInputStream
import services.UUIDGeneratorLike
import play.api.Logger
import utils.RequestExtractorHelper
import services.resourcepermissionregistry.ResourcePermissionRegistryLike

class StaticAssetsController @Inject()(
  val controllerComponents: ControllerComponents,
  val silhouette: Silhouette[DefaultEnv],
  val fileRepository: FileRepositoryLike,
  val uuidGenerator: UUIDGeneratorLike,
  val resourcePermissionRegistry: ResourcePermissionRegistryLike
)(
  implicit ec: ExecutionContext
) extends BaseController {

  val logger = Logger(getClass)

  /**
    * Serves a static file by it's key
    */
  def store() = silhouette.SecuredAction.async { implicit request =>
    Future {
      RequestExtractorHelper.singleFile(request) match {
        case None => {
          logger.info("Received invalid body data")
          Future.successful(BadRequest("Expected multipart form with a single file"))
        }
        case Some(file) => {
          val key = uuidGenerator.generate()
          logger.info(f"Handling valid body data with single file and assigned key ${key}")
          val inputStream = new FileInputStream(file)
          fileRepository.upload(key, inputStream).flatMap { _ =>
            resourcePermissionRegistry.grantAccess(request.identity, key).map { _ =>
              Ok
            }
          }
        }
      }
    }.flatten
  }

}
