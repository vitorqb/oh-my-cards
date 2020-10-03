import org.scalatestplus.play.PlaySpec

import org.scalatestplus.play._
import org.scalatestplus.mockito.MockitoSugar

import org.mockito.Mockito._
import org.mockito.{ ArgumentMatchersSugar }
import org.joda.time.DateTime
import v1.auth.OneTimePasswordInfo
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}



class OneTimePasswordInfoSpec
    extends PlaySpec
    with MockitoSugar
    with ArgumentMatchersSugar {

  "OneTimePasswordInfo.isValid" should {
    val clockMock = mock[SilhouetteClock]
    when(clockMock.now).thenReturn(DateTime.parse("2019-01-01T0:00:00"))

    val validOneTimePasswordInfo = OneTimePasswordInfo(
      "1",
      "a@b.com",
      "foo",
      clockMock.now.plus(100),
      false,
      false
    )

    "Be valid if has not been used, invalidated nor expired" in {
      validOneTimePasswordInfo.isValid(clockMock) mustBe true
    } 

    "Be invalid if has been used" in {
      validOneTimePasswordInfo.copy(hasBeenUsed=true).isValid(clockMock) mustBe false
    }

    "Be invalid if has been invalidated" in {
      validOneTimePasswordInfo.copy(hasBeenInvalidated=true).isValid(clockMock) mustBe false
    }
  }

}
