package modules

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import services.{RandomStringGenerator, UUIDGeneratorLike, Clock}
import services.MailService
import scala.concurrent.ExecutionContext
import play.api.libs.ws.WSClient
import play.api.Configuration
import services.{MailGunMailServiceImpl,MailServiceFakeImpl}
import com.google.inject.Provides
import v1.auth.TokenEncrypter
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticProperties
import com.sksamuel.elastic4s.http.JavaClient
import v1.card.elasticclient.{CardElasticClientMock,CardElasticClientImpl}
import services.SendgridMailServiceImpl
import v1.card.CardRefGenerator.CardRefGenerator
import v1.card.CardRefGenerator.CardRefGeneratorLike
import play.api.db.Database
import v1.card.cardrepositorycomponents.CardRepositoryComponentsLike
import v1.card.cardrepositorycomponents.CardRepositoryComponents
import v1.card.tagsrepository.TagsRepository
import v1.card.TagsRepositoryLike
import v1.card.CardRepositoryLike
import v1.card.CardDataRepositoryLike
import v1.card.CardRepository
import v1.card.CardDataRepository
import v1.card.CardElasticClientLike
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}
import services.UUIDGenerator
import v1.card.CardHistoryRecorderLike
import v1.card.historytracker.CardHistoryTracker
import v1.card.historytracker.HistoricalEventCoreRepositoryLike
import v1.card.historytracker.CardUpdateDataRepositoryLike
import v1.card.historytracker.HistoricalEventCoreRepository
import v1.card.historytracker.CardUpdateDataRepository

class Module extends AbstractModule with ScalaModule {

  /**
    * Simple configurations.
    */
  override def configure() = {
    bind[RandomStringGenerator].toInstance(new RandomStringGenerator)
    bind[UUIDGeneratorLike].toInstance(new UUIDGenerator)
    bind[SilhouetteClock].toInstance(new Clock)
  }

  /**
    * Provider for the MailService.
    */
  @Provides
  def provideMailService(
    implicit ec: ExecutionContext,
    ws: WSClient,
    conf: Configuration): MailService = {

    if (conf.get[String]("test") == "1")
      new MailServiceFakeImpl(conf)

    //Give preference to sendgrid
    else if (Helpers.shouldUseSendgrid(conf))
      new SendgridMailServiceImpl(ws, conf)

    //Fallback
    else
      new MailGunMailServiceImpl(ws, conf)
  }

  /**
    * Provider for the TokenEncrypter.
    */
  @Provides
  def provideTokenEncrypter(conf: Configuration): TokenEncrypter = {
    new TokenEncrypter(conf.get[String]("play.http.secret.key"))
  }

  /**
    * Provider for ElasticSearch
    */
  @Provides
  def elasticSearch(conf: Configuration): ElasticClient = {
    // See https://github.com/sksamuel/elastic4s
    val host = conf.get[String]("elasticsearch.host")
    val port = conf.get[Int]("elasticsearch.port")
    val elasticProperties = ElasticProperties(s"http://${host}:${port}")
    val javaClient = JavaClient(elasticProperties)
    ElasticClient(javaClient)
  }

  @Provides
  def cardElasticSearch(
    conf: Configuration,
    elasticClient: ElasticClient
  )(
    implicit ec: ExecutionContext
  ): CardElasticClientLike = {
    if (conf.get[String]("test") == "1")
      new CardElasticClientMock()
    else
      new CardElasticClientImpl(elasticClient)
  }

  @Provides
  def cardRefGenerator(db: Database): CardRefGeneratorLike = new CardRefGenerator(db)

  @Provides
  def tagsRepository(): TagsRepositoryLike = new TagsRepository()

  @Provides
  def historicalEventCoreRepositoryLike(): HistoricalEventCoreRepositoryLike =
    new HistoricalEventCoreRepository

  @Provides
  def cardUpdateDataRepositoryLike(
    uuidGenerator: UUIDGeneratorLike,
  ): CardUpdateDataRepositoryLike =
    new CardUpdateDataRepository(uuidGenerator)

  @Provides
  def cardHistoryRecorder(
    uuidGenerator: UUIDGeneratorLike,
    coreRepo: HistoricalEventCoreRepositoryLike,
    updateRepo: CardUpdateDataRepositoryLike
  ): CardHistoryRecorderLike =
    new CardHistoryTracker(uuidGenerator, coreRepo, updateRepo)

  @Provides
  def cardRepository(
    dataRepo: CardDataRepositoryLike,
    tagsRepo: TagsRepositoryLike,
    esClient: CardElasticClientLike,
    historyRecorder: CardHistoryRecorderLike,
    components: CardRepositoryComponentsLike
  )(
    implicit ec: ExecutionContext
  ): CardRepositoryLike =
    new CardRepository(dataRepo, tagsRepo, esClient, historyRecorder, components)

  @Provides
  def cardRepositoryComponents(
    db: Database,
    uuidGenerator: UUIDGeneratorLike,
    refGenerator: CardRefGeneratorLike,
    clock: SilhouetteClock
  ): CardRepositoryComponentsLike =
    new CardRepositoryComponents(db, uuidGenerator, refGenerator, clock)

  @Provides
  def cardDataRepository()(implicit ec: ExecutionContext): CardDataRepositoryLike =
    new CardDataRepository
}

protected object Helpers {

  def shouldUseSendgrid(conf: Configuration) =
    conf.getOptional[String]("sendgrid.key").filterNot(_ == "").isDefined

}
