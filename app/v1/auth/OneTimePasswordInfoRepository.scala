package v1.auth

import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.{AuthInfo, LoginInfo}
import scala.concurrent.Future
import scala.reflect.ClassTag
import com.mohiva.play.silhouette.api.{AuthInfo, LoginInfo}
import scala.concurrent.Future
import play.api.Logger
import com.google.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.db.Database
import anorm.{SQL,RowParser,Macro}
import anorm.JodaParameterMetaData._
import utils.anorm.{AnormUtils}

class OneTimePasswordInfoRepository @Inject()(
  val db: Database)(
 implicit val ec: ExecutionContext)
    extends AuthInfoRepository with AnormUtils {

  private val logger = Logger(getClass)

  private val oneTimePasswordInfoParser: RowParser[OneTimePasswordInfo] =
    Macro.namedParser[OneTimePasswordInfo]

  def find[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Option[T]] = {
    Future {
      db.withConnection { implicit e =>
        logger.info("Retrieving OneTimePasswordInfo for " + loginInfo)
        SQL(
          "SELECT id, email, oneTimePassword, expirationDateTime, hasBeenUsed, hasBeenInvalidated "
            + "FROM oneTimePasswords "
            + "WHERE email = {email} "
            + "ORDER BY expirationDateTime DESC "
            + "LIMIT 1;"
        ).on("email" -> loginInfo.providerKey)
          .as(oneTimePasswordInfoParser.*)
          .headOption
          .map(_.asInstanceOf[T])
          .map(x => {
            logger.info("Found " + x)
            x
          })
      }
    }
  }

  def add[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T] = Future {
    val oneTimePasswordInfo: OneTimePasswordInfo = authInfo.asInstanceOf[OneTimePasswordInfo]
    db.withConnection { implicit e =>
      logger.info("Adding OneTimePasswordInfo " + authInfo + " for " + loginInfo)
      SQL(
        "INSERT INTO oneTimePasswords(id, email, oneTimePassword, expirationDateTime, hasBeenUsed"
          + ", hasBeenInvalidated) VALUES ({id}, {email}, {oneTimePassword}, {expirationDateTime}"
          + ", {hasBeenUsed}, {hasBeenInvalidated});"
      ).on(
        "id" -> oneTimePasswordInfo.id,
        "email" -> oneTimePasswordInfo.email,
        "oneTimePassword" -> oneTimePasswordInfo.oneTimePassword,
        "expirationDateTime" -> oneTimePasswordInfo.expirationDateTime,
        "hasBeenUsed" -> oneTimePasswordInfo.hasBeenUsed,
        "hasBeenInvalidated" -> oneTimePasswordInfo.hasBeenInvalidated
      ).executeInsert()
    }
    oneTimePasswordInfo.asInstanceOf[T]
  }

  /**
    * Updates a oneTimePassword.
    */
  def update[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T] = Future {
    val oneTimePasswordInfo: OneTimePasswordInfo = authInfo.asInstanceOf[OneTimePasswordInfo]
    db.withConnection {implicit e =>
      logger.info("Updating OneTimePasswordInfo for " + loginInfo + " to " + authInfo)
      SQL(
        "UPDATE oneTimePasswords SET "
          + "  expirationDateTime={expirationDateTime}"
          + ", hasBeenUsed={hasBeenUsed}"
          + ", hasBeenInvalidated={hasBeenInvalidated}"
          + "  WHERE id={id}"
      ).on(
        "id" -> oneTimePasswordInfo.id,
        "expirationDateTime" -> oneTimePasswordInfo.expirationDateTime,
        "hasBeenUsed" -> oneTimePasswordInfo.hasBeenUsed,
        "hasBeenInvalidated" -> oneTimePasswordInfo.hasBeenInvalidated
      ).executeUpdate()
      oneTimePasswordInfo.asInstanceOf[T]
    }
  }

  def save[T <: AuthInfo](loginInfo: LoginInfo, authInfo: T): Future[T] = {
    throw  new NotImplementedError
  }

  def remove[T <: AuthInfo](loginInfo: LoginInfo)(implicit tag: ClassTag[T]): Future[Unit] = {
    throw  new NotImplementedError
  }

}
