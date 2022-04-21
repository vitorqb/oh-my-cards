package v1.card.userpermissionmanager

import v1.card.repository.UserCardPermissionManagerLike
import scala.concurrent.Future
import v1.auth.User

class UserCardPermissionManager extends UserCardPermissionManagerLike {

  override def givePermission(user: User, cardId: String): Future[Unit] =
    Future.successful(())

}
