package services.referencecounter

import play.api.db.Database
import anorm.{SQL, SqlParser}

/**
  * Generates a next unique increasing integer reference.
  */
trait ReferenceCounterLike {
  def nextRef(): Int
}

/**
  * Base implementation for the reference counter. Uses a custom table
  * in the sql db for generating increating integers.
  */
class ReferenceCounter(db: Database) extends ReferenceCounterLike {

  def nextRef(): Int = {
    val parser = SqlParser.int("value").single
    db.withTransaction { implicit t =>
      SQL(f"UPDATE counters SET value = value + 1 WHERE id = 'baseCounter'")
        .execute()
      SQL(f"SELECT value FROM counters WHERE id = 'baseCounter'").as(parser)
    }
  }
}
