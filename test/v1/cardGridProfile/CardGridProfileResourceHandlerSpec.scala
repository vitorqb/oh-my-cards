package v1.cardGridProfile

import org.scalatestplus.play.PlaySpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.joda.time.DateTime
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext.Implicits.global
import v1.auth.User
import scala.concurrent.Future

class CardGridProfileResourceHandlerSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  val user = User("user-id", "user@email")

  "create" should {

    "fail if name already exists" in {
      val repository = mock[CardGridProfileRepository]
      when(repository.userHasProfileWithName(user, "ProfileName"))
        .thenReturn(Future.successful(true))
      val handler = new CardGridProfileResourceHandler(repository)
      val input = mock[CardGridProfileInput]
      when(input.name).thenReturn("ProfileName")

      handler.create(input, user).failed.futureValue mustBe a [ProfileNameAlreadyExists]
    }

  }

  "update" should {

    "return none if not found" in {
      val input = mock[CardGridProfileInput]
      val repository = mock[CardGridProfileRepository]
      when(repository.readFromName("ProfileName", user)).thenReturn(Future.successful(None))

      val handler = new CardGridProfileResourceHandler(repository)
      handler.update("ProfileName", user, input).futureValue mustEqual None
    }

    "fail if new name is repeated" in {
      val input = mock[CardGridProfileInput]
      when(input.name).thenReturn("NewProfileName")

      val repository = mock[CardGridProfileRepository]
      when(repository.userHasProfileWithName(user, "NewProfileName"))
        .thenReturn(Future.successful(true))
      when(repository.readFromName("ProfileName", user))
        .thenReturn(Future.successful(Some(mock[CardGridProfileData])))

      val handler = new CardGridProfileResourceHandler(repository)
      (handler.update("ProfileName", user, input).failed.futureValue
        mustBe a [ProfileNameAlreadyExists])
    }

    "call repository modified and return new instance if found" in {
      val input = CardGridProfileInput(
        "ProfileName",
        CardGridConfigInput(
          Some(1),
          Some(2),
          Some(List("A")),
          Some(List("B"))
        )
      )
      val readData = Some(
        CardGridProfileData(
          "profileId",
          "userId",
          "ProfileName",
          CardGridConfigData(
            "configId",
            None,
            None,
            None,
            None
          )
        ))
      val updatedData = CardGridProfileData(
        "profileId",
        "userId",
        "ProfileName",
        CardGridConfigData(
          "configId",
          Some(1),
          Some(2),
          Some(List("A")),
          Some(List("B"))
        )
      )
      val expectedResource = CardGridProfileResource(
        readData.get.copy(
          config=readData.get.config.copy(
            page=Some(1),
            pageSize=Some(2),
            includeTags=Some(List("A")),
            excludeTags=Some(List("B"))
          )
        )
      )

      val repository = mock[CardGridProfileRepository]
      when(repository.readFromName("ProfileName", user)).thenReturn(Future.successful(readData))
      when(repository.update(readData.get, input, user)).thenReturn(Future.successful(updatedData))

      val handler = new CardGridProfileResourceHandler(repository)
      handler.update("ProfileName", user, input).futureValue mustEqual Some(expectedResource)
    }

  }

}
