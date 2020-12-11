package v1.auth

import scala.concurrent.Future
import com.google.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.db.Database
import anorm.{SQL, RowParser, Macro, Column, MetaDataItem}
import play.api.Logger
import utils.anorm.RelatedObjectDoesNotExist
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import anorm.TypeDoesNotMatch
import utils.anorm.AnormUtils

class UserRepository @Inject() (val db: Database)(implicit
    ec: ExecutionContext
) extends AnormUtils {

  private val logger: Logger = Logger(getClass)

  private val userParser: RowParser[User] = Macro.namedParser[User]

  def findById(id: String): Future[Option[User]] =
    Future {
      logger.info("Finding user with id " + id)
      db.withConnection { implicit e =>
        SQL(
          "SELECT id, email, isAdmin FROM users WHERE id={id}"
        ).on("id" -> id)
          .as(userParser.*)
          .headOption
          .map { user =>
            logger.info("Found user " + user)
            user
          }
      }
    }

  def findByEmail(email: String): Future[Option[User]] =
    Future {
      logger.info("Finding user with email " + email)
      db.withConnection { implicit e =>
        SQL(
          "SELECT id, email, isAdmin FROM users WHERE email={email}"
        ).on("email" -> email)
          .as(userParser.*)
          .headOption
          .map { user =>
            logger.info("Found user " + user)
            user
          }
      }
    }

  def add(user: User): Future[User] =
    Future {
      logger.info("Adding user " + user)
      db.withConnection { implicit e =>
        SQL(
          "INSERT INTO users(id, email, isAdmin) VALUES ({id}, {email}, FALSE)"
        ).on(
          "id" -> user.id,
          "email" -> user.email
        ).executeInsert()
        user
      }
    }
}

/**
  * A helper object used to serialize an user from an user id.
  */
class IdColumnToUserConverter(private val userRepository: UserRepository)(
    implicit val ec: ExecutionContext
) {

  /**
    * Implements the logic to return an user from an user id.
    * This can be given to a implicit def for columnToUser:
    */
  def convert: Column[User] = {
    Column.nonNull1 { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case id: String => {
          val futureUser = userRepository.findById(id).map {
            case Some(user) => Right(user)
            case None =>
              throw RelatedObjectDoesNotExist(
                "Could not find user with id " + id
              )
          }
          Await.result(futureUser, Duration.Inf)
        }
        case _ =>
          Left(
            TypeDoesNotMatch(
              s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to User for column $qualified"
            )
          )
      }
    }
  }
}

object IdColumnToUserConverter {
  def apply(u: UserRepository)(implicit ec: ExecutionContext) =
    new IdColumnToUserConverter(u)
}
