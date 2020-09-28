package v1.auth

import com.google.inject.Inject
import services.RandomStringGenerator
import services.UUIDGenerator
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}


/**
  * Generates new OneTimePasswordInfos
  */
class OneTimePasswordInfoGenerator @Inject()(
  val clock: SilhouetteClock,
  val randomStringGenerator: RandomStringGenerator,
  val uuidGenerator: UUIDGenerator) {

  /**
    * Length used to generate a OneTimePasswordInfo.
    */
  val length: Int = 8

  /**
    * Number of minutes the password is valid.
    */
  val validForMinutes: Int = 15

  /**
    * Generates a new OneTimePasswordInfo based on a OneTimePasswordInput.
    */
  def generate(input: OneTimePasswordInput): OneTimePasswordInfo = {
    OneTimePasswordInfo(
      uuidGenerator.generate(),
      input.email,
      randomStringGenerator.generate(length),
      clock.now.plusMinutes(validForMinutes),
      false,
      false
    )
  }

}
