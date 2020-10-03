package v1.auth

import org.scalatestplus.play.PlaySpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.joda.time.DateTime
import org.mockito.Mockito._

import services.RandomStringGenerator
import org.scalatest.PrivateMethodTester
import scala.concurrent.Future
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}



class TokenServiceSpec
    extends PlaySpec
    with ScalaFutures
    with MockitoSugar
    with PrivateMethodTester {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global  

  "TokenService.generateTokenForUser" should {

    "Delegate to userTokenRepository.add" in {
      val clockMock = mock[SilhouetteClock]
      when(clockMock.now).thenReturn(DateTime.parse("2020-01-01T00:00:00"))

      val randomStringGeneratorMock = mock[RandomStringGenerator]
      when(randomStringGeneratorMock.generate(TokenService.length)).thenReturn("foo")

      val user = User("foo", "bar@haz.com")
      val userToken = UserToken(user, "foo", DateTime.parse("2020-01-02T00:00:00"), false)

      val userTokenRepositoryMock = mock[UserTokenRepository]
      when(userTokenRepositoryMock.add(userToken)).thenReturn(Future { userToken })

      val tokenService = new TokenService(
        randomStringGeneratorMock,
        userTokenRepositoryMock,
        clockMock
      )

      tokenService.generateTokenForUser(user).futureValue mustEqual userToken
    }

  }

  "TokenService.nowTokenFor" should {

    "generate a new token for an user" in {
      val clockMock = mock[SilhouetteClock]
      when(clockMock.now).thenReturn(DateTime.parse("2020-01-01T00:00:00"))

      val randomStringGeneratorMock = mock[RandomStringGenerator]
      when(randomStringGeneratorMock.generate(TokenService.length)).thenReturn("foo")

      val userTokenRepositoryMock = mock[UserTokenRepository]
      val tokenService = new TokenService(
        randomStringGeneratorMock,
        userTokenRepositoryMock,
        clockMock
      )
      val user = User("foo", "bar@haz.com")
      val newTokenFor = PrivateMethod[UserToken](Symbol("newTokenFor"))
      val result = tokenService invokePrivate newTokenFor(user)
      result mustEqual UserToken(user, "foo", DateTime.parse("2020-01-02T00:00:00"), false)
    }

  }

}
