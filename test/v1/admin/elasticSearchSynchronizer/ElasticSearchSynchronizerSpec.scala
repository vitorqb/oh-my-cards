package v1.admin.elasticSearchSynchronizer

import org.scalatestplus.play.PlaySpec
import v1.admin.testUtils.TestEsClient
import test.utils.TestUtils
import play.api.db.Database
import v1.card.CardData
import org.joda.time.DateTime
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._
import services.UUIDGenerator
import v1.card.CardRepository
import v1.card.TagsRepository
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

class ElasticSearchSynchronizerSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with TestEsClient
    with BeforeAndAfter {

  import com.sksamuel.elastic4s.ElasticDsl._

  override def fakeApplication: Application =
    new GuiceApplicationBuilder()
      .overrides(new TestEsFakeModule)
      .overrides(bind[CardElasticClient].to[CardElasticClientImpl])
      .build()

  before {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
  }

  after {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
  }

  "run" should {

    lazy val datetime1 = new DateTime(2020, 1, 1, 0, 0, 0)
    lazy val datetime2 = new DateTime(2020, 1, 2, 0, 0, 0)

    lazy val cardData1 = CardData(None, "t1", "b1", List(), Some(datetime1), Some(datetime1))
    lazy val cardData2 = CardData(None, "t2", "b2", List(), Some(datetime1), Some(datetime2))
    lazy val cardData3 = CardData(None, "t3", "b3", List(), None, None)

    lazy val user = User("a", "b")

    def createThreeCardsOnDb() = {
      val repository: CardRepository = app.injector.instanceOf[CardRepository]
      val idOne = repository.create(cardData1, user).get
      val idTwo = repository.create(cardData2, user).get
      val idThree = repository.create(cardData3, user).get
      (idOne, idTwo, idThree)
    }

    "Send all items to es client" taggedAs(FunctionalTestsTag) in {
      cleanIndex("cards")
      refreshIdx("cards")
      val (idOne, idTwo, idThree) = createThreeCardsOnDb()
      cleanIndex("cards")
      refreshIdx("cards")

      val synchronizer = app.injector.instanceOf[ElasticSearchSynchornizer]
      synchronizer.deleteStaleEntries().await
      synchronizer.updateAllEntries().await
      refreshIdx("cards")

      val titles = client.execute {
        search("cards").matchAllQuery()
      }.await.result.hits.hits.map(_.sourceAsMap("title"))

      titles mustEqual List("t1", "t2", "t3")

      val ids = client.execute {
        search("cards").matchAllQuery()
      }.await.result.hits.hits.map(_.id)
      ids mustEqual List(idOne, idTwo, idThree)
    }

    "Erases stale items that do not exist in the db" taggedAs(FunctionalTestsTag) in {
      cleanIndex("cards")
      client.execute {
        indexInto("cards").id("FOO")
      }.await
      refreshIdx("cards")

      val getRequestBefore = client.execute(get("cards", "FOO")).await.result
      getRequestBefore.found mustEqual true

      val synchronizer = app.injector.instanceOf[ElasticSearchSynchornizer]
      synchronizer.run().await
      refreshIdx("cards")

      val getRequestAfter = client.execute(get("cards", "FOO")).await.result
      getRequestAfter.found mustEqual false
    }
  }

}
