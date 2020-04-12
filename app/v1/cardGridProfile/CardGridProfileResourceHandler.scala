package v1.cardGridProfile

import v1.auth.User

import scala.util.Try
import scala.concurrent.Future
import com.google.inject.Inject
import scala.concurrent.ExecutionContext
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.Writes

/**
  * Custom exceptions
  */
final case class ProfileNameAlreadyExists(
  val message: String = "A profile with this name already exists",
  val cause: Throwable = None.orNull
) extends Exception(message, cause)


/**
  * A resource is a User representation of a CardGridProfile.
  */
case class CardGridProfileResource(profile: CardGridProfileData)

object CardGridProfileResource {

  /**
    * The implicit json writter so we can serialize it.
    */
  implicit val writes: Writes[CardGridProfileResource] = {
    import play.api.libs.json._
    import play.api.libs.functional.syntax._
    (
      (JsPath \ "name").write[String] and
      (JsPath \ "config" \ "page").write[Option[Int]] and
      (JsPath \ "config" \ "pageSize").write[Option[Int]] and
      (JsPath \ "config" \ "includeTags").writeNullable[List[String]] and
      (JsPath \ "config" \ "excludeTags").writeNullable[List[String]]
    )(r => (
      r.profile.name,
      r.profile.config.page,
      r.profile.config.pageSize,
      r.profile.config.includeTags,
      r.profile.config.excludeTags
    ))
  }

  /**
    * Transforms a data into a resource
    */
  def fromData(x: CardGridProfileData): CardGridProfileResource = CardGridProfileResource(x)
}

/**
  * A resource handle for all card grid profiles.
  */
class CardGridProfileResourceHandler @Inject()(
  val repository: CardGridProfileRepository)(
  implicit val ec: ExecutionContext
) {

  def create(input: CardGridProfileInput, user: User): Future[CardGridProfileResource] = {
    repository.userHasProfileWithName(user, input.name).map { nameExists =>
      if (nameExists) throw ProfileNameAlreadyExists()
    }.flatMap { _ =>
      repository.create(input, user).map(CardGridProfileResource.fromData)
    }
  }

  def read(name: String, user: User): Future[Option[CardGridProfileResource]] =
    repository.readFromName(name, user).map(x => x.map(y => CardGridProfileResource.fromData(y)))

  /**
    * Updates a card grid profile, if found. If not found, returns None.
    */
  def update(
    name: String,
    user: User,
    input: CardGridProfileInput
  ): Future[Option[CardGridProfileResource]] = {

    def checkNewNameDoesNotExist: Future[Unit] = {
      val newName = input.name
      if (name == newName) Future.successful(())
      else for {
        hasProfileWithName <- repository.userHasProfileWithName(user, newName)
      } yield {
        if (hasProfileWithName) throw new ProfileNameAlreadyExists
        else ()
      }
    }

    repository.readFromName(name, user).flatMap {
      case None => Future.successful(None)
      case Some(existingData) => for {
        _ <- checkNewNameDoesNotExist
        updatedData <- repository.update(existingData, input)
        updatedResource = CardGridProfileResource.fromData(updatedData)
      } yield Some(updatedResource)
    }
  }

  def listNames(user: User): Future[List[String]] = repository.listNames(user)
}
