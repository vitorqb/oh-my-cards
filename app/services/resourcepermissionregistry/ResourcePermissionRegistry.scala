package services.resourcepermissionregistry

import play.api.db.Database
import v1.auth.User
import scala.concurrent.Future
import anorm._
import scala.concurrent.ExecutionContext

trait ResourcePermissionRegistryLike {

  /**
    * Checks whether an user can access a resoruce.
    */
  def hasAccess(user: User, key: String): Future[Boolean]

  /**
    * Grants an user access to a resource.
    */
  def grantAccess(user: User, key: String): Future[Unit]

}

class ResourcePermissionRegistry(db: Database)(implicit val ec: ExecutionContext)
    extends ResourcePermissionRegistryLike {

  override def hasAccess(user: User, key: String): Future[Boolean] = Future {
    db.withConnection { implicit c =>
      SQL(
        "SELECT 1 FROM resourceUserPermissions WHERE userId={userId} AND resourceKey={key}"
      )
        .on("userId" -> user.id, "key" -> key)
        .as(SqlParser.int(1).*)
        .headOption
        .isDefined
    }
  }

  override def grantAccess(user: User, key: String): Future[Unit] = Future {
    db.withConnection { implicit c =>
      SQL(
        """INSERT INTO resourceUserPermissions(resourceKey, userId)
           VALUES({key}, {userId})"""
      )
        .on("userId" -> user.id, "key" -> key)
        .executeInsert()
    }
  }


}
