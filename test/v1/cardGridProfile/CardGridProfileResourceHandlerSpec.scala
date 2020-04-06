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

  "create" should {

    "fail if name already exists" in {
      val user = User("user-id", "user@email")
      val repository = mock[CardGridProfileRepository]
      when(repository.userHasProfileWithName(user, "ProfileName"))
        .thenReturn(Future.successful(true))
      val handler = new CardGridProfileResourceHandler(repository)
      val input = mock[CardGridProfileInput]
      when(input.name).thenReturn("ProfileName")

      handler.create(input, user).failed.futureValue mustBe a [ProfileNameAlreadyExists]
    }

  }

}
