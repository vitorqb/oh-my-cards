package v1.auth

import org.scalatest._
import org.scalatestplus.play._
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import org.mockito.{ ArgumentMatchersSugar }

import org.scalatestplus.play.PlaySpec
import play.api.mvc.Headers
import play.api.mvc.Request
import scala.concurrent.Future
import org.scalatest.concurrent.ScalaFutures
import org.joda.time.DateTime
import play.api.mvc.AnyContent
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}



class BearerTokenRequestProviderSpec
    extends PlaySpec
    with MockitoSugar
    with ScalaFutures {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  "authenticate" should {
    val clock = mock[SilhouetteClock]
    when(clock.now).thenReturn(DateTime.parse("2019-01-01"))

    val tokenValue = "foo"
    val user = User("bar", "baz@baz.baz")
    val validUserToken = UserToken(user, tokenValue, clock.now.plusMinutes(60), false)
    val invalidUserToken = validUserToken.copy(expirationDateTime = clock.now.minusMinutes(60))

    "Bring authenticated user if finds valid token" in {
      val userTokenRepositoryMock = mock[UserTokenRepository]
      when(userTokenRepositoryMock.findByTokenValue("foo"))
        .thenReturn(Future { Some(validUserToken) })

      val headers = mock[Headers]
      when(headers.get("Authorization")).thenReturn(Some("Bearer " + tokenValue))

      val request = mock[Request[AnyContent]]
      when(request.headers).thenReturn(headers)

      val bearerTokenProvider = new BearerTokenRequestProvider(userTokenRepositoryMock, clock)
      val result = bearerTokenProvider.authenticate(request)
      result.futureValue mustEqual Some(LoginInfo(BearerTokenRequestProvider.ID, user.email))
    }

    "Does not bring authenticated user if invalid token" in {
      val userTokenRepositoryMock = mock[UserTokenRepository]
      when(userTokenRepositoryMock.findByTokenValue(tokenValue))
        .thenReturn(Future { Some(invalidUserToken) })

      val headers = mock[Headers]
      when(headers.get("Authorization")).thenReturn(Some("Bearer " + tokenValue))

      val request = mock[Request[AnyContent]]
      when(request.headers).thenReturn(headers)

      val bearerTokenProvider = new BearerTokenRequestProvider(userTokenRepositoryMock, clock)
      val result = bearerTokenProvider.authenticate(request)
      result.futureValue mustEqual None
    }

    "Does not bring authenticated user if no token" in {
      val userTokenRepositoryMock = mock[UserTokenRepository]

      val headers = mock[Headers]
      when(headers.get("Authorization")).thenReturn(None)

      val request = mock[Request[AnyContent]]
      when(request.headers).thenReturn(headers)

      val bearerTokenProvider = new BearerTokenRequestProvider(userTokenRepositoryMock, clock)
      val result = bearerTokenProvider.authenticate(request)
      result.futureValue mustEqual None
    }

  }

  "extractTokenValue" should {

    "Extract valid value" in {
      val headers = mock[Headers]
      when(headers.get("Authorization")).thenReturn(Some("Bearer foobarbaz"))

      val request = mock[Request[AnyRef]]
      when(request.headers).thenReturn(headers)

      BearerTokenRequestProvider.extractTokenValue(request) mustEqual Some("foobarbaz")
    }

    "Return None on invalid value" in {
      val headers = mock[Headers]
      when(headers.get("Authorization")).thenReturn(None)

      val request = mock[Request[AnyRef]]
      when(request.headers).thenReturn(headers)

      BearerTokenRequestProvider.extractTokenValue(request) mustEqual None
    }

  }

}
