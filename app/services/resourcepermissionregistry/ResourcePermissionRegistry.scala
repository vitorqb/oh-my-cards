package services.resourcepermissionregistry

import v1.auth.User
import scala.concurrent.Future
import anorm._
import scala.concurrent.ExecutionContext
import java.sql.Connection

trait ResourcePermissionRegistryLike {

  /**
    * Checks whether an user can access a resoruce.
    */
  def hasAccess(user: User, key: String)(implicit
      c: Connection
  ): Future[Boolean]

  /**
    * Grants an user access to a resource.
    */
  def grantAccess(user: User, key: String)(implicit c: Connection): Future[Unit]

}

class ResourcePermissionRegistry(implicit val ec: ExecutionContext)
    extends ResourcePermissionRegistryLike {

  override def hasAccess(user: User, key: String)(implicit
      c: Connection
  ): Future[Boolean] =
    Future {
      SQL(
        "SELECT 1 FROM resourceUserPermissions WHERE userId={userId} AND resourceKey={key}"
      )
        .on("userId" -> user.id, "key" -> key)
        .as(SqlParser.int(1).*)
        .headOption
        .isDefined
    }

  override def grantAccess(user: User, key: String)(implicit
      c: Connection
  ): Future[Unit] =
    Future {
      SQL(
        """INSERT INTO resourceUserPermissions(resourceKey, userId)
           VALUES({key}, {userId})"""
      )
        .on("userId" -> user.id, "key" -> key)
        .executeInsert()
    }

}
