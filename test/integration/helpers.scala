package tests.integration

import play.api.db.Database
import org.scalatest.concurrent.ScalaFutures
import anorm.{SQL}


/**
  * Helper class that provides a valid user token for testing.
  */
class TestTokenProviderSvc(db: Database) extends ScalaFutures {

  private var token: Option[String] = None

  def getToken(): String = token match {
    case Some(x) => x
    case None => {
      db.withConnection { implicit c =>
        SQL("""INSERT INTO users(id, email, isAdmin) VALUES(1, "test@test.com", FALSE)""").executeInsert()
        SQL("""
            | INSERT INTO userTokens(userId, token, expirationDateTime, hasBeenInvalidated)
            | VALUES(1, "FOOBARBAZ", "4102444800000", false)
            """.stripMargin).executeInsert()
        token = Some("FOOBARBAZ")
        getToken()
      }
    }
  }
}
