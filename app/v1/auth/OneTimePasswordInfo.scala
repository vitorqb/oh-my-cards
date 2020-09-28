package v1.auth

import org.joda.time.DateTime
import com.mohiva.play.silhouette.api.AuthInfo
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}


/**
  * This is the Authentication Info (AuthInfo) for a oneTimePassword.
  */
case class OneTimePasswordInfo(
  id: String,
  email: String,
  oneTimePassword: String,
  expirationDateTime: DateTime,
  hasBeenUsed: Boolean,
  hasBeenInvalidated: Boolean,
) extends AuthInfo {

  /**
    * Returns whether the OneTimePasswordInfo is valid for a login.
    */
  def isValid(clock: SilhouetteClock): Boolean = {
    ! hasBeenInvalidated && ! hasBeenUsed && ! clock.now.isAfter(expirationDateTime)
  }

  def matches(candidate: String): Boolean = candidate == oneTimePassword

}
