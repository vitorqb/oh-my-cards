package v1.auth

import scala.concurrent.Future
import com.google.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.db.Database
import play.api.Logger
import anorm._
import anorm.JodaParameterMetaData._
import utils.anorm.{AnormUtils}
import anorm.Macro.ColumnNaming

class UserTokenRepository @Inject()(
  db: Database,
  userRepository: UserRepository)(
  implicit ec: ExecutionContext)
    extends AnormUtils{

  private val logger = Logger(getClass)

  private val userTokenParser: RowParser[UserToken] = Macro.namedParser[UserToken](
    ColumnNaming(x => x match {
      case "user" => "userId"
      case x => x
    })
  )

  /**
    * Custom conversion from an user id into an user using userRepository.
    */
  implicit def columnTouser: Column[User] = IdColumnToUserConverter(userRepository).convert

  /**
    * Adds and returns a userToken.
    */
  def add(userToken: UserToken): Future[UserToken] = Future {
    logger.info("Adding UserToken " + userToken)
    db.withConnection { implicit e =>
      SQL(
        "INSERT INTO userTokens(userId, token, expirationDateTime, hasBeenInvalidated) "
          + "VALUES({userId}, {token}, {expirationDateTime}, {hasBeenInvalidated})"
      ).on(
        "userId" -> userToken.user.id,
        "token" -> userToken.token,
        "expirationDateTime" -> userToken.expirationDateTime,
        "hasBeenInvalidated" -> userToken.hasBeenInvalidated
      ).executeInsert()
      userToken
    }
  }

  /**
    * Finds and returns an user token given the user and the token string value.
    */
  def findByTokenValue(tokenValue: String): Future[Option[UserToken]] = Future {
    logger.info("Finding UserToken for token value " + tokenValue)
    db.withConnection { implicit e =>
      SQL(
        "SELECT userId, token, expirationDateTime, hasBeenInvalidated FROM userTokens "
          + "WHERE token={token} "
          + "LIMIT 1"
      ).on(
        "token" -> tokenValue
      ).as(userTokenParser.*)
        .headOption
        .map{ x =>
          logger.info("Found userToken " + x)
          x
        }
    }
  }

}
