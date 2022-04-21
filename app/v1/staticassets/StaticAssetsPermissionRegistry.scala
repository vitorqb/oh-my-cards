package v1.staticassets

import scala.concurrent.Future
import v1.auth.User
import services.resourcepermissionregistry.ResourcePermissionRegistryLike
import play.api.db.Database

trait StaticAssetsPermissionRegistryLike {
  def hasAccess(user: User, key: String): Future[Boolean]
  def grantAccess(user: User, key: String): Future[Unit]
}

class StaticAssetsPermissionRegistry(
    registry: ResourcePermissionRegistryLike,
    db: Database
) extends StaticAssetsPermissionRegistryLike {

  override def hasAccess(user: User, key: String): Future[Boolean] =
    db.withConnection { implicit c =>
      registry.hasAccess(user, key)
    }

  override def grantAccess(user: User, key: String): Future[Unit] =
    db.withConnection { implicit c =>
      registry.grantAccess(user, key)
    }

}
