package v1.cardGridProfile

import play.api.db.Database
import v1.auth.User
import scala.concurrent.Future
import com.google.inject.Inject
import scala.concurrent.ExecutionContext
import anorm.SQL
import services.UUIDGenerator
import java.sql.Connection

/**
  * Represents a CardGridProfile
  */
case class CardGridProfileData(id: String, userId: String, name: String, config: CardGridConfigData)

/**
  * Represents a CardGridConfig
  */
case class CardGridConfigData(
  id: String,
  page: Option[Int],
  pageSize: Option[Int],
  includeTags: Option[List[String]],
  excludeTags: Option[List[String]]
)

/**
  * Repository for CardGridProfiles
  */
class CardGridProfileRepository @Inject()(
  db: Database,
  uuidGenerator: UUIDGenerator)(
  implicit val ec: ExecutionContext
) {

  /**
    * Creates the CardGridProfile.
    */
  def create(input: CardGridProfileInput, user: User): Future[CardGridProfileData] = {
    Future {
      db.withTransaction { c =>
        val configId = uuidGenerator.generate()
        val profileId = uuidGenerator.generate()
        val creator = new CardGridProfileCreator(c, input, user, profileId, configId)
        creator.createProfile
        creator.createConfig
        creator.createIncludeTags
        creator.createExcludeTags
        read(profileId)
      }
    }.flatten
  }

  /**
    * Reads a CardGridProfile.
    */
  def read(id: String): Future[CardGridProfileData] = Future {
    db.withConnection { implicit c =>
      val reader = new CardGridProfileReader(id)
      val configId = reader.configId
      val includeTags = reader.readIncludeTags(configId)
      val excludeTags = reader.readExcludeTags(configId)
      val config = reader.readConfig(configId, includeTags, excludeTags)
      val profile = reader.readProfile(config)
      profile
    }
  }

  /**
    * Reads a CardGridProfile from name.
    */
  def readFromName(name: String, user: User): Future[Option[CardGridProfileData]] = {
    getProfileIdFromName(name, user).flatMap {
      case Some(id) => read(id).map(x => Some(x))
      case None => Future.successful(None)
    }
  }

  /**
    * Returns an `id` for a given `name` and `user`
    */
  def getProfileIdFromName(name: String, user: User): Future[Option[String]] = Future {
    import anorm.SqlParser._
    db.withConnection { implicit c =>
      SQL("SELECT id FROM cardGridProfiles WHERE name = {name} AND userId = {userId}")
        .on("name" -> name, "userId" -> user.id)
        .as(str(1).*)
        .headOption
    }
  }

  /**
    * Returns True if a user has a profile with a given name.
    */
  def userHasProfileWithName(user: User, name: String): Future[Boolean] =
    getProfileIdFromName(name, user).map(! _.isEmpty)

  /**
    * List the name of all profiles for an user.
    */
  def listNames(user: User): Future[List[String]] = Future {
    import anorm.SqlParser._
    db.withConnection { implicit c =>
      SQL("SELECT name FROM cardGridprofiles WHERE userId = {userId}")
        .on("userId" -> user.id)
        .as(str(1).*)
    }
  }
}

/**
  * A helper class exposing methods to create each part of a grid profile.
  */
class CardGridProfileCreator(
  c: Connection,
  input: CardGridProfileInput,
  user: User,
  profileId: String,
  configId: String
) {

  implicit val connection = c

  def createProfile: Unit = {
    SQL("""INSERT INTO cardGridProfiles(id,name,userId) VALUES ({id}, {name}, {userId})""")
      .on("id" -> profileId, "name" -> input.name, "userId" -> user.id)
      .executeInsert()
  }

  def createConfig: Unit = {
    val config = input.config
    SQL("""INSERT INTO cardGridConfigs(id, profileId, page, pageSize)
           VALUES ({configId}, {profileId}, {page}, {pageSize})""")
      .on("profileId" -> profileId,
          "configId" -> configId,
          "page" -> config.page,
          "pageSize" -> config.pageSize)
      .executeInsert()
  }

  def createIncludeTags: Unit = input.config.includeTags.getOrElse(List()).foreach { tag =>
    SQL("""INSERT INTO cardGridConfigIncludeTags(configId, tag) VALUES ({configId}, {tag})""")
      .on("configId" -> configId, "tag" -> tag)
      .executeInsert()
  }

  def createExcludeTags: Unit = input.config.excludeTags.map{ tags => tags.foreach{ tag =>
    SQL("""INSERT INTO cardGridConfigExcludeTags(configId, tag) VALUES ({configId}, {tag})""")
      .on("configId" -> configId, "tag" -> tag)
      .executeInsert()
  }}

}

/**
  * A helper class exposing methods to read each part of a grid profile.
  */
class CardGridProfileReader(profileId: String)(implicit val c: Connection) {
  import anorm.SqlParser._
  import anorm.~

  def configId: String =
    SQL("SELECT id FROM cardGridConfigs WHERE profileId = {profileId}")
      .on("profileId" -> profileId)
      .as(str(1).single)

  def readProfile(config: CardGridConfigData): CardGridProfileData = {
    SQL("SELECT name, userId FROM cardGridProfiles WHERE id = {id}")
      .on("id" -> profileId)
      .as((str(1) ~ str(2)).single) match {
        case name ~ userId => CardGridProfileData(profileId, userId, name, config)
      }
  }

  def readConfig(
    configId: String,
    includeTags: Option[List[String]],
    excludeTags: Option[List[String]]
  ): CardGridConfigData = {
    SQL("SELECT page, pageSize FROM cardGridConfigs WHERE id = {id}")
      .on("id" -> configId)
      .as((get[Option[Int]]("page") ~ get[Option[Int]]("pageSize")).single) match {
        case page ~ pageSize =>
          CardGridConfigData(configId, page, pageSize, includeTags, excludeTags)
      }
  }

  def readIncludeTags(configId: String): Option[List[String]] = {
    SQL("SELECT tag FROM cardGridConfigIncludeTags WHERE configId = {id}")
      .on("id" -> configId)
      .as(str(1).*) match {
        case Nil => None
        case x => Some(x)
      }
  }

  def readExcludeTags(configId: String): Option[List[String]] = {
    SQL("SELECT tag FROM cardGridConfigExcludeTags WHERE configId = {id}")
      .on("id" -> configId)
      .as(str(1).*) match {
        case Nil => None
        case x => Some(x)
      }
  }

}
