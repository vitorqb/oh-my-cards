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
case class CardGridProfileData(
  id: String,
  userId: String,
  name: String,
  config: CardGridConfigData
)

/**
  * Represents a CardGridConfig
  */
case class CardGridConfigData(
  id: String,
  page: Option[Int],
  pageSize: Option[Int],
  includeTags: Option[List[String]],
  excludeTags: Option[List[String]],
  query: Option[String]
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
      db.withTransaction { implicit c =>
        val configId = uuidGenerator.generate()
        val profileId = uuidGenerator.generate()
        val creator = new CardGridProfileCreator(user, input, profileId, configId)
        creator.execute()
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
      reader.execute()
    }
  }

  /**
    * Updates a CardGridProfile.
    */
  def update(
    existingData: CardGridProfileData,
    newInput: CardGridProfileInput,
    user: User
  ): Future[CardGridProfileData] = Future {
    db.withTransaction { implicit c =>
      val updater = new CardGridProfileUpdater(existingData, newInput, user)
      updater.execute()
      read(existingData.id)
    }
  }.flatten

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
  user: User,
  input: CardGridProfileInput,
  profileId: String,
  configId: String
)(
  implicit c: Connection
){

  def execute(): Unit = {
    createProfile()
    createConfig()
    createIncludeTags()
    createExcludeTags()
  }

  def createProfile(): Unit = {
    SQL("""INSERT INTO cardGridProfiles(id,name,userId) VALUES ({id}, {name}, {userId})""")
      .on(
        "id" -> profileId,
        "name" -> input.name,
        "userId" -> user.id
      )
      .executeInsert()
  }

  def createConfig(): Unit = {
    val config = input.config
    SQL(
      """INSERT INTO cardGridConfigs(id, profileId, page, pageSize, query)
         VALUES ({configId}, {profileId}, {page}, {pageSize}, {query})"""
    ).on(
        "profileId" -> profileId,
        "configId" -> configId,
        "page" -> config.page,
        "pageSize" -> config.pageSize,
        "query" -> config.query
      ).executeInsert()
  }

  def createIncludeTags(): Unit = input.config.includeTags.getOrElse(List()).foreach { tag =>
    SQL("""INSERT INTO cardGridConfigIncludeTags(configId, tag) VALUES ({configId}, {tag})""")
      .on("configId" -> configId, "tag" -> tag)
      .executeInsert()
  }

  def createExcludeTags(): Unit = input.config.excludeTags.map{ tags => tags.foreach{ tag =>
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

  def execute(): CardGridProfileData = {
      val includeTags = readIncludeTags(configId)
      val excludeTags = readExcludeTags(configId)
      val config = readConfig(configId, includeTags, excludeTags)
      readProfile(config)
  }

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

    val parser = (
      get[Option[Int]]("page") ~
        get[Option[Int]]("pageSize") ~
        get[Option[String]]("query")
    )

    SQL("SELECT page, pageSize, query FROM cardGridConfigs WHERE id = {id}")
      .on("id" -> configId)
      .as(parser.single) match {
        case page ~ pageSize ~ query =>
          CardGridConfigData(configId, page, pageSize, includeTags, excludeTags, query)
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

/**
  * A helper class exposing methods to update a cardGridProfile
  */
class CardGridProfileUpdater(
  existingData: CardGridProfileData,
  newInput: CardGridProfileInput,
  user: User
)(
  implicit c: Connection
) {

  def execute(): Unit = {
      updateProfile()
      updateConfig()
      updateIncludeTags()
      updateExcludeTags()
  }

  def updateProfile(): Unit = {
    SQL("UPDATE cardGridProfiles SET name={name} WHERE id={id}")
      .on("id" -> existingData.id, "name" -> newInput.name)
      .execute()
  }

  def updateConfig(): Unit = {
    SQL(
      """UPDATE cardGridConfigs
         SET page={page}, pageSize={pageSize}, query={query}
         WHERE id={id}"""
    ).on(
      "id" -> existingData.config.id,
      "page" -> newInput.config.page,
      "pageSize" -> newInput.config.pageSize,
      "query" -> newInput.config.query
    ).execute()
  }

  def updateIncludeTags(): Unit = {
    SQL("""DELETE FROM cardGridConfigIncludeTags WHERE configId={id}""")
      .on("id" -> existingData.config.id)
      .execute()
    creator.createIncludeTags
  }

  def updateExcludeTags(): Unit = {
    SQL("""DELETE FROM cardGridConfigExcludeTags WHERE configId={id}""")
      .on("id" -> existingData.config.id)
      .execute()
    creator.createExcludeTags
  }

  lazy val creator: CardGridProfileCreator =
    new CardGridProfileCreator(user, newInput, existingData.id, existingData.config.id)

}
