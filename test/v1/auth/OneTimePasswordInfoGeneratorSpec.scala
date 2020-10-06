package v1.auth

import org.scalatestplus.play._
import org.scalatestplus.mockito.MockitoSugar

import org.mockito.Mockito._
import org.mockito.{ ArgumentMatchersSugar }

import org.joda.time.DateTime
import services.RandomStringGenerator
import services.UUIDGeneratorLike
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}


class OneTimePasswordInfoGeneratorSpec
    extends PlaySpec
    with MockitoSugar
    with ArgumentMatchersSugar{

  "OneTimePasswordInfoGenerator.generate" should {

    "Generates a new OneTimePasswordInfo" in {
      val clockMock = mock[SilhouetteClock]
      when(clockMock.now).thenReturn(DateTime.parse("2019-01-01T00:00:00"))

      val randomStringGeneratorMock = mock[RandomStringGenerator]
      when(randomStringGeneratorMock.generate(2)).thenReturn("foo")

      val uuidGeneratorMock = mock[UUIDGeneratorLike]
      when(uuidGeneratorMock.generate).thenReturn("bar")

      val generator = new OneTimePasswordInfoGenerator(
        clockMock,
        randomStringGeneratorMock,
        uuidGeneratorMock
      ) {
        override val validForMinutes = 2
        override val length = 2
      }

      generator.generate(OneTimePasswordInput("a@b.com")) mustEqual OneTimePasswordInfo(
        "bar",
        "a@b.com",
        "foo",
        DateTime.parse("2019-01-01T00:02:00"),
        false,
        false
      )
    }

  }

}
