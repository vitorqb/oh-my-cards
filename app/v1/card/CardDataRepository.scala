package v1.card

import javax.inject.Inject
import java.util.UUID.randomUUID
import scala.util.{Try,Success,Failure}
import anorm.{SQL,RowParser,Macro,SqlParser,SeqParameter}
import play.api.db.Database
import v1.auth.User
import anorm.`package`.SqlStringInterpolation
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.sql.Connection
import anorm.SimpleSql
import anorm.Row
import org.joda.time.DateTime
import anorm.JodaParameterMetaData._
import v1.card.CardRefGenerator.CardRefGeneratorLike
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike

/**
  * An implementation for a card repository.
  */
class CardDataRepository @Inject()(
  //!!!! TODO One day we should stop take `components`, and simply take an implicit connection.
  //instead of using components.db
  components: CardRepositoryComponentsLike,
  //!!!! TODO Don't take tagsRepo nor es
  tagsRepo: TagsRepositoryLike,
  cardElasticClient: CardElasticClient
)(
  implicit val ec: ExecutionContext
) extends CardDataRepositoryLike {

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
  def create(
    cardFormInput: CardFormInput,
    context: CardCreationContext
  )(implicit c: Connection): Try[String] = {
    SQL(
      """INSERT INTO cards(id, userId, title, body, createdAt, updatedAt, ref)
             VALUES ({id}, {userId}, {title}, {body}, {now}, {now}, {ref})"""
    ).on(
      "id" -> context.id,
      "userId" -> context.user.id,
      "title" -> cardFormInput.getTitle(),
      "body" -> cardFormInput.getBody(),
      "now" -> context.now,
      "ref" -> context.ref
    ).executeInsert()
    Success(context.id)
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
          val now = components.clock.now
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
