package v1.auth

import org.scalatestplus.play.PlaySpec
import org.scalatest.concurrent.ScalaFutures

class UserRepositorySpec extends PlaySpec with ScalaFutures {

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  "UserRepository" should {

    "Add and retrieve an user" in {
      test.utils.TestUtils.testDB { db =>
        val user = User("foo", "a@b.com")

        val userRepository = new UserRepository(db)

        val added = userRepository.add(user).futureValue
        added mustEqual user

        val foundById = userRepository.findById(user.id).futureValue
        foundById mustEqual Some(user)

        val foundByEmail = userRepository.findByEmail(user.email).futureValue
        foundByEmail mustEqual Some(user)
      }
    }

  }

}
