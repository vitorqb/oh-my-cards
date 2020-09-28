package v1.card.cardrepositoryspec

import v1.card._
import org.scalatestplus.play.PlaySpec
import org.mockito.MockitoSugar
import org.mockito.Mockito._
import v1.auth.User
import services.UUIDGenerator
import v1.card.testUtils.ComponentsBuilder
import play.api.db.Database
import scala.util.Success
import java.sql.Connection
import org.mockito.{ ArgumentMatchersSugar }
import v1.card.testUtils.MockDb
import scala.util.Failure
import org.joda.time.DateTime
import v1.card.CardRefGenerator.CardRefGeneratorLike

class FindResultSpec extends PlaySpec {

  "fromQueryResults" should {
    val cardData1 = CardData("id1", "ONE", "TWO", List("a", "b"), ref=1)
    val cardData2 = CardData("id2", "one", "two", List("A", "B", "D"), ref=2)
    val cardData = List(cardData1, cardData2)
    val idsResult = CardElasticIdFinder.Result(Seq("id2", "id1"), 5)
    val findResult = FindResult.fromQueryResults(cardData, idsResult)

    "have the same countOfids from idsResult" in {
      findResult.countOfItems mustEqual 5
    }

    "sort the sequence of card data by the ids in the idsResult" in {
      findResult.cards mustEqual Seq(cardData2, cardData1)
    }
  }

}

case class TestContext(
  val dataRepo: CardDataRepositoryLike,
  val tagsRepo: TagsRepositoryLike,
  val esClient: CardElasticClient,
  val repo: CardRepositoryLike
)

class CardRepositorySpec extends PlaySpec with MockitoSugar with ArgumentMatchersSugar {

  val user = User("A", "A@B.com")
  val now = new DateTime(2000, 1, 1, 0, 0, 0)
  val context = CardCreationContext(user, now, "id", 1)
  val connection = mock[Connection]
  val db = new MockDb {
    override def withTransaction[A](block: Connection => A): A = block(connection)
  }
  val components = ComponentsBuilder().withDb(db).withContext(context).build()

  def testContext(block: TestContext => Any): Any = {
    val dataRepo = mock[CardDataRepositoryLike]
    when(dataRepo.create(any, any)(any)).thenReturn(Success("id"))
    val tagsRepo = mock[TagsRepositoryLike]
    val esClient = mock[CardElasticClient]
    val repo = new CardRepository(dataRepo, tagsRepo, esClient, components)
    TestContext(dataRepo, tagsRepo, esClient, repo)
  }

  "create" should {

    "send create msg to card data repository" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), None)
      c.repo.create(formInput, user)
      verify(c.dataRepo).create(formInput, context)(connection)
    }

    "send create msg to tags repo" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), Some(List("A")))
      c.repo.create(formInput, user)
      verify(c.tagsRepo).create("id", List("A"))(connection)
    }

    "send create data to es client" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), Some(List("A")))
      c.repo.create(formInput, user)
      verify(c.esClient).create(formInput, context)
    }

    "returns created id" in testContext { c =>
      val formInput = CardFormInput("title", Some("body"), None)
      c.repo.create(formInput, user).get mustEqual "id"
    }

  }

}
