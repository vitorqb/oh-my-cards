package v1.admin.elasticSearchSynchronizer

import org.scalatestplus.play.PlaySpec
import v1.admin.testUtils.TestEsClient
import test.utils.TestUtils
import play.api.db.Database
import v1.card.CardData
import org.joda.time.DateTime
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import v1.card.CardDataRepository
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import v1.auth.User
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import com.sksamuel.elastic4s.ElasticClient
import test.utils.FunctionalTestsTag
import org.scalatest.BeforeAndAfter
import v1.card.CardElasticClient
import play.api.inject.bind
import v1.card.CardElasticClientImpl
import v1.card.testUtils.CardFixture
import v1.card.CardFormInput
import v1.card.CardRepositoryLike
import org.scalatest.concurrent.ScalaFutures

class ElasticSearchSynchronizerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with TestEsClient
    with BeforeAndAfter
    with ScalaFutures
{

  import com.sksamuel.elastic4s.ElasticDsl._

  val index = "cards"

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .overrides(new TestEsFakeModule)
      .overrides(bind[CardElasticClient].to[CardElasticClientImpl])
      .build()

  before {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
    cleanIndex(index)
    refreshIdx(index)
  }

  after {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
    cleanIndex(index)
    refreshIdx(index)
  }

  "run" should {

    lazy val cardInput1 = CardFormInput("t1", Some("b1"), Some(List("A")))
    lazy val cardInput2 = CardFormInput("t2", Some("b2"), Some(List()))
    lazy val cardInput3 = CardFormInput("t3", Some("b3"), Some(List("a", "B")))

    lazy val user = User("a", "b")

    def createThreeCardsOnDb() = {
      val repository: CardRepositoryLike = app.injector.instanceOf[CardRepositoryLike]
      val idOne = repository.create(cardInput1, user).futureValue
      val idTwo = repository.create(cardInput2, user).futureValue
      val idThree = repository.create(cardInput3, user).futureValue
      (idOne, idTwo, idThree)
    }

    "Send all items to es client" taggedAs(FunctionalTestsTag) in {
      val (idOne, idTwo, idThree) = createThreeCardsOnDb()
      cleanIndex(index)
      refreshIdx(index)

      val synchronizer = app.injector.instanceOf[ElasticSearchSynchornizer]
      synchronizer.deleteStaleEntries().await
      synchronizer.updateAllEntries().await
      refreshIdx(index)

      val hits = client.execute {
        search(index).matchAllQuery()
      }.await.result.hits.hits

      hits.map(_.sourceAsMap("title")) mustEqual List("t1", "t2", "t3")
      hits.map(_.id) mustEqual List(idOne, idTwo, idThree)
      hits.map(_.sourceAsMap("tags")) mustEqual List(List("a"), List(), List("a", "b"))
    }

    "Erases stale items that do not exist in the db" taggedAs(FunctionalTestsTag) in {
      cleanIndex(index)
      client.execute {
        indexInto(index).id("FOO")
      }.await
      refreshIdx(index)

      val getRequestBefore = client.execute(get(index, "FOO")).await.result
      getRequestBefore.found mustEqual true

      val synchronizer = app.injector.instanceOf[ElasticSearchSynchornizer]
      synchronizer.run().await
      refreshIdx(index)

      val getRequestAfter = client.execute(get(index, "FOO")).await.result
      getRequestAfter.found mustEqual false
    }
  }

}
