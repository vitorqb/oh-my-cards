package v1.auth

import scala.concurrent.Future
import com.google.inject.Inject
import scala.concurrent.ExecutionContext
import services.RandomStringGenerator
import com.mohiva.play.silhouette.api.util.{Clock => SilhouetteClock}

class TokenService @Inject() (
    randomStringGenerator: RandomStringGenerator,
    userTokenRepository: UserTokenRepository,
    clock: SilhouetteClock
)(implicit val ec: ExecutionContext) {

  def generateTokenForUser(user: User): Future[UserToken] = {
    userTokenRepository.add(newTokenFor(user))
  }

  private def newTokenFor(user: User): UserToken = {
    UserToken(
      user,
      randomStringGenerator.generate(TokenService.length),
      clock.now.plusMinutes(TokenService.durationInMinutes),
      false
    )
  }

}

object TokenService {

  val length = 30
  val durationInMinutes = 60 * 24

}
