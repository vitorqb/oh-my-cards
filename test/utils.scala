package test.utils

import play.api.db.Database
import play.api.db.Databases
import play.api.db.evolutions.Evolutions
import anorm.{SQL}


/**
  * Utils for testing.
  */
object TestUtils {

  private var dbInitialized = false

  /**
    * Cleans all tables from db.
    */
  def cleanupDb(db: Database) = {
    db.withConnection { implicit c =>
      SQL("DELETE FROM cards").execute()
      SQL("DELETE FROM oneTimePasswords").execute()
      SQL("DELETE FROM users").execute()
      SQL("DELETE FROM userTokens").execute()
      SQL("DELETE FROM cardsTags").execute()
    }
  }

  /**
    * Used as a context manager for tests with db.
    */
  def testDB[T](block: Database => T) = {
    Databases.withDatabase("org.sqlite.JDBC", "jdbc:sqlite:test.sqlite") { db =>
      if (! dbInitialized) {
        Evolutions.cleanupEvolutions(db)
        Evolutions.applyEvolutions(db)
        dbInitialized = true
      }
      try {
        block(db)
      } finally {
        cleanupDb(db)
      }
    }
  }

}

/**
  * Utils for testing with json objects.
  */
trait JsonUtils {
  import play.api.libs.json._
  implicit class EnrichedJsonForTest(val js: JsValue) {
    def hasKey(key: String): Boolean = (js \ key) match {
      case JsDefined(_) => true
      case JsUndefined() => false
    }
  }
}

/**
  * String comparison utils.
  */
trait StringUtils {

  implicit class EnrichedStringForTest(val s: String) {
    def cleanForComparison: String = s.trim.replaceAll("\\n+", "").replaceAll(" +", " ")
  }

}
