package v1.card.CardRefGenerator

import play.api.db.Database
import anorm._

/**
  * A generator of `references` for new cards.
  * Each references has to be a unique integer.
  * Cards with creation date more recent must have higher refs.
  */
trait CardRefGeneratorLike {
  def nextRef(): Int
}

/**
  * An implementation that queries the db for the next ref to use.
  */
class CardRefGenerator(db: Database) extends CardRefGeneratorLike {

  override def nextRef(): Int = db.withConnection { implicit c =>
    SQL("""SELECT MAX(cards.ref) as x FROM cards""")
      .as(SqlParser.scalar[Int].singleOpt)
      .getOrElse(0) + 1
  }

}
