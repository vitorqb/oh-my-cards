package v1.auth

import com.google.inject.Inject
import services.RandomStringGenerator
import services.UUIDGeneratorLike
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}


/**
  * Generates new OneTimePasswordInfos
  */
class OneTimePasswordInfoGenerator @Inject()(
  val clock: SilhouetteClock,
  val randomStringGenerator: RandomStringGenerator,
  val uuidGenerator: UUIDGeneratorLike) {

  import OneTimePasswordInfoGenerator._

  /**
    * Generates a new OneTimePasswordInfo based on a OneTimePasswordInput.
    */
  def generate(input: OneTimePasswordInput): OneTimePasswordInfo = {
    OneTimePasswordInfo(
      uuidGenerator.generate(),
      input.email,
      randomStringGenerator.generate(length, Some(chars)),
      clock.now.plusMinutes(validForMinutes),
      false,
      false
    )
  }

}

/**
  * Companion object with constants
  */
object OneTimePasswordInfoGenerator {
  /**
    * Length used to generate a OneTimePasswordInfo.
    */
  val length: Int = 10

  /**
    * Valid alphanumeric characters for the password.
    */
  val chars: Set[Char] =
    RandomStringGenerator.alphaNumericChars -- Set('I', '0', 'O', 'o', 'l')

  /**
    * Number of minutes the password is valid.
    */
  val validForMinutes: Int = 15
}
