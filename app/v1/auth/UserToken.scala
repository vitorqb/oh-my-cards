package v1.auth

import org.joda.time.DateTime
import com.mohiva.play.silhouette.api.LoginInfo
import services.Clock
import play.api.libs.json.Writes

case class UserToken(
  user: User,
  token: String,
  expirationDateTime: DateTime,
  hasBeenInvalidated: Boolean
) {

  def toLoginInfo(id: String): LoginInfo = LoginInfo(id, user.email)

  def isValid(clock: Clock): Boolean = !hasBeenInvalidated && clock.now.isBefore(expirationDateTime)
}

object UserToken {
  implicit val writes: Writes[UserToken] = new Writes[UserToken] {
    import play.api.libs.json._

    def writes(x: UserToken) = Json.obj("value" -> x.token)
  }
}
