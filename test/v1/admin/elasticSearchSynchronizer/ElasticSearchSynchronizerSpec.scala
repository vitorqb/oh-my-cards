package v1.admin.elasticSearchSynchronizer

import org.scalatestplus.play.PlaySpec
import v1.admin.testUtils.TestEsClient
import test.utils.TestUtils
import play.api.db.Database
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import v1.auth.User
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import test.utils.FunctionalTestsTag
import org.scalatest.BeforeAndAfter
import play.api.inject.bind
import v1.card.CardFormInput
import v1.card.CardRepositoryLike
import org.scalatest.concurrent.ScalaFutures
import v1.card.CardElasticClientLike
import v1.card.elasticclient.CardElasticClientImpl
import v1.card.CardCreationContext
import com.mohiva.play.silhouette.api.util.{Clock => SilhouetteClock}
import services.UUIDGeneratorLike
import services.referencecounter.ReferenceCounterLike

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
      .overrides(bind[CardElasticClientLike].to[CardElasticClientImpl])
      .build()

  before {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
    cleanIndex()
    refreshIdx()
  }

  after {
    TestUtils.cleanupDb(app.injector.instanceOf[Database])
    cleanIndex()
    refreshIdx()
  }

  "run" should {

    lazy val cardInput1 = CardFormInput("t1", Some("b1"), Some(List("A")))
    lazy val cardInput2 = CardFormInput("t2", Some("b2"), Some(List()))
    lazy val cardInput3 = CardFormInput("t3", Some("b3"), Some(List("a", "B")))

    lazy val user = User("a", "b")

    def createThreeCardsOnDb() = {
      val repository: CardRepositoryLike = app.injector.instanceOf[CardRepositoryLike]
      val clock = app.injector.instanceOf[SilhouetteClock]
      val uuidGenerator = app.injector.instanceOf[UUIDGeneratorLike]
      val refGenerator = app.injector.instanceOf[ReferenceCounterLike]
      //!!!! TODO How could we make this nicer?
      val context1 = CardCreationContext(user, clock.now, uuidGenerator.generate, refGenerator.nextRef)
      val idOne = repository.create(cardInput1, context1).futureValue
      val context2 = CardCreationContext(user, clock.now, uuidGenerator.generate, refGenerator.nextRef)
      val idTwo = repository.create(cardInput2, context2).futureValue
      val context3 = CardCreationContext(user, clock.now, uuidGenerator.generate, refGenerator.nextRef)
      val idThree = repository.create(cardInput3, context3).futureValue
      (idOne, idTwo, idThree)
    }

    "Send all items to es client" taggedAs(FunctionalTestsTag) in {
      val (idOne, idTwo, idThree) = createThreeCardsOnDb()
      cleanIndex()
      refreshIdx()

      val synchronizer = app.injector.instanceOf[ElasticSearchSynchornizer]
      synchronizer.deleteStaleEntries().await
      synchronizer.updateAllEntries().await
      refreshIdx()

      val hits = client.execute {
        search(index).matchAllQuery()
      }.await.result.hits.hits

      hits.map(_.sourceAsMap("title")) mustEqual List("t1", "t2", "t3")
      hits.map(_.id) mustEqual List(idOne, idTwo, idThree)
      hits.map(_.sourceAsMap("tags")) mustEqual List(List("a"), List(), List("a", "b"))
    }

    "Erases stale items that do not exist in the db" taggedAs(FunctionalTestsTag) in {
      cleanIndex()
      client.execute {
        indexInto(index).id("FOO")
      }.await
      refreshIdx()

      val getRequestBefore = client.execute(get(index, "FOO")).await.result
      getRequestBefore.found mustEqual true

      val synchronizer = app.injector.instanceOf[ElasticSearchSynchornizer]
      synchronizer.run().await
      refreshIdx()

      val getRequestAfter = client.execute(get(index, "FOO")).await.result
      getRequestAfter.found mustEqual false
    }
  }

}
