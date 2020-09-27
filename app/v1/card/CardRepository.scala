package v1.card

import javax.inject.Inject
import java.util.UUID.randomUUID
import scala.util.{Try,Success,Failure}
import anorm.{SQL,RowParser,Macro,SqlParser,SeqParameter}
import play.api.db.Database
import v1.auth.User
import services.UUIDGenerator
import anorm.`package`.SqlStringInterpolation
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.sql.Connection
import anorm.SimpleSql
import anorm.Row
import services.Clock
import org.joda.time.DateTime
import anorm.JodaParameterMetaData._
import v1.card.CardRefGenerator.CardRefGeneratorLike
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike


/**
  * A trait for all known user exceptions.
  */
sealed trait CardRepositoryUserException { val message: String }


/**
  * Custom exception signaling that a card does not exist.
  */
final case class CardDoesNotExist(
  val message: String = "The required card does not exist.",
  val cause: Throwable = None.orNull
) extends Exception(message, cause) with CardRepositoryUserException

/**
  * Custom exception signaling that an error ocurred when parsing the tags mini lang.
  */
final case class TagsFilterMiniLangSyntaxError(
  val message: String,
  val cause: Throwable = None.orNull
) extends Exception(message, cause) with CardRepositoryUserException

/**
  * The data for a Card.
  */
final case class CardData(
  id: String,
  title: String,
  body: String,
  tags: List[String],
  createdAt: Option[DateTime] = None,
  updatedAt: Option[DateTime] = None,
  ref: Int
)

/**
  * The result of a find query.
  */
final case class FindResult(cards: Seq[CardData], countOfItems: Int)

object FindResult {

  /**
    * Alternative constructor from the results of the query for the IDs and the query for
    * the data.
    */
  def fromQueryResults(
    cardData: Seq[CardData],
    idsResult: CardElasticIdFinder.Result
  ): FindResult = {
    val cardDataById = cardData.map(x => (x.id, x)).toMap
    val sortedCardData = idsResult.ids.map(x => cardDataById.get(x)).flatten
    FindResult(sortedCardData, idsResult.countOfIds)
  }

}

/**
  * An implementation for a card repository.
  */
class CardDataRepository @Inject()(
  components: CardRepositoryComponentsLike,
  tagsRepo: TagsRepository,
  cardElasticClient: CardElasticClient
)(
  implicit val ec: ExecutionContext
) {

  /**
    * The statement used to get a card.
    */
  val sqlGetStatement: String = s"SELECT id, title, body, updatedAt, createdAt, ref FROM cards "

  /**
    * A parser for CardData that also queries for the tags from TagsRepo.
    */
  private def cardDataParser()(implicit c: Connection): RowParser[CardData] = {
    import anorm._

    (
      SqlParser.str("id") ~
        SqlParser.str("title") ~
        SqlParser.str("body") ~
        SqlParser.get[Option[DateTime]]("createdAt") ~
        SqlParser.get[Option[DateTime]]("updatedAt") ~
        SqlParser.int("ref")
    ) map {
      case id ~ title ~ body ~ createdAt ~ updatedAt ~ ref =>
        CardData(id, title, body, tagsRepo.get(id), createdAt, updatedAt, ref)
    }
  }

  //!!!! TODO Return Future
  def create(cardFormInput: CardFormInput, user: User): Try[String] = {
    val now = components.clock.now()
    val id = components.uuidGenerator.generate
    val title = cardFormInput.getTitle()
    val body = cardFormInput.getBody()
    val tags = cardFormInput.getTags()
    val ref = components.refGenerator.nextRef()

    components.db.withTransaction { implicit c =>
      SQL(
        """INSERT INTO cards(id, userId, title, body, createdAt, updatedAt, ref)
             VALUES ({id}, {userId}, {title}, {body}, {now}, {now}, {ref})"""
      ).on(
        "id" -> id,
        "userId" -> user.id,
        "title" -> title,
        "body" -> body,
        "now" -> now,
        "ref" -> ref
      ).executeInsert()
      tagsRepo.create(id, tags)
      cardElasticClient.create(cardFormInput, id, now, user)
      Success(id)
    }
  }

  def get(id: String, user: User): Option[CardData] = components.db.withConnection { implicit c =>
    SQL(s"${sqlGetStatement} WHERE userId = {userId} AND id = {id}")
      .on("id" -> id, "userId" -> user.id)
      .as(cardDataParser.*)
      .headOption
  }

  /**
    * Finds a list of cards for a given user.
    */
  def find(request: CardListRequest): Future[FindResult] = {
    for {
      esIdsResult <- cardElasticClient.findIds(request)
      query = SQL(s"${sqlGetStatement} WHERE id IN ({ids})").on("ids" -> esIdsResult.ids)
    } yield {
      components.db.withConnection { implicit c =>
        val sqlQueryResult = query.as(cardDataParser.*)
        FindResult.fromQueryResults(sqlQueryResult, esIdsResult)
      }
    }
  }

  /**
    * Deletes a card by it's id.
    */
  def delete(id: String, user: User): Future[Try[Unit]] = Future {
    get(id, user) match {
      case None => Failure(new CardDoesNotExist)
      case Some(_) => components.db.withTransaction { implicit c =>
        SQL(s"DELETE FROM cards WHERE userId = {userId} AND id = {id}")
          .on("id" -> id, "userId" -> user.id)
          .executeUpdate()
        tagsRepo.delete(id)
        cardElasticClient.delete(id)
        Success(())
      }
    }
  }

  /**
    * Updates a card.
    */
  def update(data: CardData, user: User): Future[Try[Unit]] =
    Future {
      Try {
        components.db.withTransaction {
          implicit c =>
          val now = components.clock.now()
          SQL("""
        UPDATE cards SET title={title}, body={body}, updatedAt={now}
        WHERE id={id} AND userId={userId}
       """)
            .on(
              "title" -> data.title,
              "body" -> data.body,
              "id" -> data.id,
              "userId" -> user.id,
              "now" -> now
            )
            .executeUpdate()
          tagsRepo.delete(data.id)
          tagsRepo.create(data.id, data.tags)
          cardElasticClient.update(data, now, user)
        }
      }
    }

  /**
    * Returns all tags for a given user.
    */
  def getAllTags(user: User): Future[List[String]] =
    Future {
      components.db.withTransaction { implicit c =>
        import anorm.SqlParser._
        SQL"""
       SELECT tag
       FROM cardsTags
       JOIN cards ON cards.id = cardId
       WHERE cards.userId = ${user.id}
    """.as(str(1).*)
      }
    }
}

/**
  * Helper object manage cards tags.
  */
protected class TagsRepository {

  /**
    * Delets all tags for a given card id.
    */
  def delete(cardId: String)(implicit c:Connection): Unit = {
    SQL"DELETE FROM cardsTags WHERE cardId = ${cardId}".execute()
  }

  /**
    * Create all tags for a given card id.
    */
  def create(cardId: String, tags: List[String])(implicit c: Connection): Unit = {
    tags.foreach { tag =>
      SQL"INSERT INTO cardsTags(cardId, tag) VALUES (${cardId}, ${tag})".executeInsert()
    }
  }

  /**
    * Returns all tags for a given card id.
    */
  def get(cardId: String)(implicit c: Connection): List[String] = {
    SQL"SELECT tag FROM cardsTags WHERE cardId = ${cardId} ORDER BY tag"
      .as(SqlParser.scalar[String].*)
  }
}
