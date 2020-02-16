package test.utils

import play.api.db.Database
import play.api.db.Databases
import play.api.db.evolutions.Evolutions


/**
  * Utils for testing.
  */
object TestUtils {

  /**
    * Used as a context manager for tests with db.
    */
  def testDB[T](block: Database => T) = {
    Databases.withDatabase("org.sqlite.JDBC", "jdbc:sqlite:test.sqlite") { db =>
      db.withTransaction { conn =>
        try {
          Evolutions.applyEvolutions(db)
          block(db)
        } finally {
          Evolutions.cleanupEvolutions(db)
          conn.rollback
        }
      }
    }
  }

}
