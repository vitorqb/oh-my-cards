package v1.auth

import org.joda.time.DateTime
import com.mohiva.play.silhouette.api.LoginInfo
import services.Clock

case class UserToken(
  user: User,
  token: String,
  expirationDateTime: DateTime,
  hasBeenInvalidated: Boolean
) {

  def toLoginInfo(id: String): LoginInfo = LoginInfo(id, user.email)

  def isValid(clock: Clock): Boolean = !hasBeenInvalidated && clock.now.isBefore(expirationDateTime)
}
