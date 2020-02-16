package v1.auth

import org.scalatestplus.play._
import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures

class UserTokenRepositorySpec extends PlaySpec with ScalaFutures {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  "UserTokenRepositorySpec add and find" should {
 
    "Create and find an user token" in {
      test.utils.TestUtils.testDB { db =>
        val userToken = UserToken(
          User("foo", "bar@baz.com"),
          "foo",
          DateTime.parse("1993-11-23T11:00:00"),
          false
        )
        val userRepository = new UserRepository(db)
        userRepository.add(userToken.user).futureValue

        val userTokenRepository = new UserTokenRepository(db, userRepository)

        val added = userTokenRepository.add(userToken).futureValue
        added mustEqual userToken

        val found = (userTokenRepository
          .findByTokenValue(userToken.token)
          .futureValue)
        found mustEqual Some(userToken)
      }
    }
  }

}
