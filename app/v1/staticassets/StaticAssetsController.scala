package v1.staticassets

import com.google.inject.Inject
import play.api.mvc.ControllerComponents
import play.api.mvc.BaseController
import com.mohiva.play.silhouette.api.Silhouette
import v1.auth.DefaultEnv
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import services.filerepository.FileRepositoryLike
import services.UUIDGeneratorLike
import play.api.Logger
import utils.RequestExtractorHelper
import services.resourcepermissionregistry.ResourcePermissionRegistryLike
import play.api.libs.json.Json
import v1.auth.CookieUserIdentifierLike

class StaticAssetsController @Inject() (
    val controllerComponents: ControllerComponents,
    val silhouette: Silhouette[DefaultEnv],
    val fileRepository: FileRepositoryLike,
    val uuidGenerator: UUIDGeneratorLike,
    val resourcePermissionRegistry: ResourcePermissionRegistryLike,
    val cookieUserIdentifier: CookieUserIdentifierLike
)(implicit
    ec: ExecutionContext
) extends BaseController {

  val logger = Logger(getClass)

  /**
    * Serves a static file by it's key
    */
  def store() =
    silhouette.SecuredAction.async { implicit request =>
      Future {
        RequestExtractorHelper.singleFile(request) match {
          case None => {
            logger.info("Received invalid body data")
            Future.successful(
              BadRequest("Expected multipart form with a single file")
            )
          }
          case Some(file) => {
            val key = uuidGenerator.generate()
            logger.info(
              f"Handling valid body data with single file and assigned key ${key}"
            )
            fileRepository.store(key, file).flatMap { _ =>
              resourcePermissionRegistry
                .grantAccess(request.identity, key)
                .map { _ =>
                  Ok(Json.obj("key" -> key))
                }
            }
          }
        }
      }.flatten
    }

  /**
    * Retrieves the static file (if the user has access)
    */
  def retrieve(key: String) =
    silhouette.UserAwareAction.async { implicit request =>
      Future {
        //We need to recover the user by cookie because this is a request sent by <img> tags.
        cookieUserIdentifier.identifyUser(request).flatMap {
          case None => Future.successful(Unauthorized)
          case Some(user) =>
            resourcePermissionRegistry.hasAccess(user, key).flatMap {
              case false => Future.successful(NotFound)
              case true =>
                fileRepository.read(key).map { x =>
                  Ok.sendFile(x)
                }
            }
        }
      }.flatten
    }
}
