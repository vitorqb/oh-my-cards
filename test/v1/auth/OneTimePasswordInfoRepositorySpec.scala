package v1.auth

import org.scalatestplus.play._

import com.mohiva.play.silhouette.api.LoginInfo
import org.joda.time.DateTime
import org.scalatest.concurrent.ScalaFutures

class OneTimePasswordInfoRepositorySpec extends PlaySpec with ScalaFutures {

  implicit val ec: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.global

  "OneTimePasswordInfoRepositorySpec add and find" should {

    "Be able to add and find an instance" in {
      test.utils.TestUtils.testDB { db =>
        val loginInfo = LoginInfo("foo", "a@b.com")
        val authInfo = OneTimePasswordInfo(
          "1",
          "a@b.com",
          "foo",
          DateTime.parse("2020-01-01T00:00:00"),
          false,
          false
        )

        val repository = new OneTimePasswordInfoRepository(db)
        val added = repository.add(loginInfo, authInfo).futureValue
        val found = repository.find(loginInfo).futureValue
        added mustEqual authInfo
        Some(added) mustEqual found
      }

    }

    "Find should return most recent one" in {
      test.utils.TestUtils.testDB { db =>
        val loginInfo = LoginInfo("foo", "a@b.com")
        val olderAuthInfo = OneTimePasswordInfo(
          "1",
          "a@b.com",
          "foo",
          DateTime.parse("2020-01-01T00:00:00"),
          false,
          false
        )
        val authInfo = OneTimePasswordInfo(
          "2",
          "a@b.com",
          "bar",
          DateTime.parse("2020-01-01T00:00:01"),
          true,
          true
        )

        val repository = new OneTimePasswordInfoRepository(db)
        val olderAdded = repository.add(loginInfo, olderAuthInfo).futureValue
        val added = repository.add(loginInfo, authInfo).futureValue
        val found = repository.find(loginInfo).futureValue
        Some(authInfo) mustEqual found
      }

    }

    "OneTimePasswordInfoRepository.update" should {

      "Update and retrieve updated instance" in {
        test.utils.TestUtils.testDB { db =>
          val loginInfo = LoginInfo("foo", "a@b.com")
          val authInfo = OneTimePasswordInfo(
            "1",
            "a@b.com",
            "foo",
            DateTime.parse("2020-01-01T00:00:00"),
            false,
            false
          )

          val repository = new OneTimePasswordInfoRepository(db)
          val added = repository.add(loginInfo, authInfo).futureValue
          added mustEqual authInfo

          val newAuthInfo = authInfo.copy(
            hasBeenUsed = true,
            hasBeenInvalidated = true,
            expirationDateTime = DateTime.parse("2021-02-02T01:01:01")
          )
          val updated = repository.update(loginInfo, newAuthInfo).futureValue
          updated mustEqual newAuthInfo

          val retrieved = repository.find(loginInfo).futureValue
          Some(updated) mustEqual retrieved
        }
      }

    }

  }
}
