package v1.auth

import org.scalatestplus.play._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import com.mohiva.play.silhouette.api.LoginInfo
import services.UUIDGeneratorLike

class UserServiceSpec extends PlaySpec with MockitoSugar with ScalaFutures {

  "UserService.retrieve" should {

    "Delegate to UserRepository" in {
      val user = User("1", "a@b.com")
      val loginInfo = LoginInfo(OneTimePasswordProvider.ID, user.email)

      val userRepositoryMock = mock[UserRepository]
      when(userRepositoryMock.findByEmail(user.email))
        .thenReturn(Future.successful(Some(user)))

      val uuidGeneratorMock = mock[UUIDGeneratorLike]
      when(uuidGeneratorMock.generate).thenReturn("foo")

      ((new UserService(userRepositoryMock, uuidGeneratorMock))
        .retrieve(loginInfo)
        .futureValue
        mustEqual Some(user))
    }

  }

  "UserService.add" should {

    "Delegate to UserRepository" in {
      val user = User("1", "a@b.com")
      val loginInfo = LoginInfo(OneTimePasswordProvider.ID, user.email)

      val uuidGeneratorMock = mock[UUIDGeneratorLike]
      when(uuidGeneratorMock.generate).thenReturn(user.id)

      val userRepositoryMock = mock[UserRepository]
      when(userRepositoryMock.add(user)).thenReturn(Future.successful(user))

      ((new UserService(userRepositoryMock, uuidGeneratorMock))
        .add(loginInfo)
        .futureValue
        mustEqual user)
    }

  }

}
