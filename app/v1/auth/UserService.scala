package v1.auth

import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.LoginInfo
import scala.concurrent.Future
import com.google.inject.Inject
import services.UUIDGenerator

class UserService @Inject()(
  userRepository: UserRepository,
  uuidGenerator: UUIDGenerator)
    extends IdentityService[User] {

  def retrieve(loginInfo: LoginInfo): Future[Option[User]] = {
    userRepository.findByEmail(loginInfo.providerKey)
  }

  def add(loginInfo: LoginInfo): Future[User] = {
    userRepository.add(User(uuidGenerator.generate, loginInfo.providerKey))
  }

}
