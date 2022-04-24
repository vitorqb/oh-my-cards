package v1.card.userpermissionmanager

import v1.card.repository.UserCardPermissionManagerLike
import scala.concurrent.Future
import v1.auth.User
import java.sql.Connection
import services.resourcepermissionregistry.ResourcePermissionRegistryLike

class UserCardPermissionManager(registry: ResourcePermissionRegistryLike)
    extends UserCardPermissionManagerLike {

  override def hasPermission(user: User, cardId: String)(implicit
      c: Connection
  ): Future[Boolean] = registry.hasAccess(user, cardId)

  override def givePermission(user: User, cardId: String)(implicit
      c: Connection
  ): Future[Unit] = registry.grantAccess(user, cardId)

}
