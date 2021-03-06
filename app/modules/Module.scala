package modules

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import services.{RandomStringGenerator, UUIDGeneratorLike, Clock}
import services.MailService
import scala.concurrent.ExecutionContext
import play.api.libs.ws.WSClient
import play.api.Configuration
import services.{MailGunMailServiceImpl, MailServiceFakeImpl}
import com.google.inject.Provides
import v1.auth.TokenEncrypter
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticProperties
import com.sksamuel.elastic4s.http.JavaClient
import v1.card.elasticclient.{CardElasticClientMock, CardElasticClientImpl}
import services.SendgridMailServiceImpl
import play.api.db.Database
import v1.card.datarepository.CardDataRepository
import v1.card.tagsrepository.TagsRepository
import v1.card.repository.{
  TagsRepositoryLike,
  CardRepositoryLike,
  CardDataRepositoryLike,
  CardRepository,
  CardElasticClientLike,
  CardHistoryRecorderLike
}
import com.mohiva.play.silhouette.api.util.{Clock => SilhouetteClock}
import services.UUIDGenerator
import v1.card.historytracker.CardHistoryTracker
import v1.card.historytracker.HistoricalEventCoreRepositoryLike
import v1.card.historytracker.CardUpdateDataRepositoryLike
import v1.card.historytracker.HistoricalEventCoreRepository
import v1.card.historytracker.CardUpdateDataRepository
import v1.card.historytrackerhandler.{
  HistoryTrackerHandlerLike,
  HistoryTrackerHandler,
  CardHistoryTrackerLike
}
import services.referencecounter.{ReferenceCounter, ReferenceCounterLike}
import services.filerepository.FileRepositoryLike
import services.filerepository.BackblazeS3Config
import services.filerepository.MockFileRepository
import services.resourcepermissionregistry.ResourcePermissionRegistryLike
import services.resourcepermissionregistry.ResourcePermissionRegistry
import services.filerepository.B2FileRepository
import v1.auth.CookieTokenManagerLike
import v1.auth.CookieTokenManager
import v1.auth.UserTokenRepository
import v1.auth.CookieUserIdentifierLike
import v1.auth.CookieUserIdentifier
import v1.card.CardResourceHandlerLike
import v1.card.CardResourceHandler

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
  def provideMailService(implicit
      ec: ExecutionContext,
      ws: WSClient,
      conf: Configuration
  ): MailService = {

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
  )(implicit
      ec: ExecutionContext
  ): CardElasticClientLike = {
    if (conf.get[String]("test") == "1")
      new CardElasticClientMock()
    else
      new CardElasticClientImpl(elasticClient)
  }

  @Provides
  def cardRefGenerator(db: Database): ReferenceCounterLike =
    new ReferenceCounter(db)

  @Provides
  def tagsRepository(): TagsRepositoryLike = new TagsRepository()

  @Provides
  def historicalEventCoreRepositoryLike(): HistoricalEventCoreRepositoryLike =
    new HistoricalEventCoreRepository

  @Provides
  def cardUpdateDataRepositoryLike(
      uuidGenerator: UUIDGeneratorLike
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
      db: Database
  )(implicit
      ec: ExecutionContext
  ): CardRepositoryLike =
    new CardRepository(dataRepo, tagsRepo, esClient, historyRecorder, db)

  @Provides
  def cardDataRepository()(implicit
      ec: ExecutionContext
  ): CardDataRepositoryLike =
    new CardDataRepository

  @Provides
  def cardHistoryTrackerLike(
      uuidGenerator: UUIDGeneratorLike,
      coreRepo: HistoricalEventCoreRepositoryLike,
      updateRepo: CardUpdateDataRepositoryLike
  ): CardHistoryTrackerLike =
    new CardHistoryTracker(uuidGenerator, coreRepo, updateRepo)

  @Provides
  def historyTrackerHandler(
      db: Database,
      tracker: CardHistoryTrackerLike
  )(implicit
      ec: ExecutionContext
  ): HistoryTrackerHandlerLike =
    new HistoryTrackerHandler(db, tracker)

  @Provides
  def fileRepository(
      config: Configuration
  )(implicit
      ec: ExecutionContext
  ): FileRepositoryLike = {
    config.get[String]("staticFilesRepositoryType") match {
      case "backblaze" => {
        val backblazeConfig = BackblazeS3Config(
          config.get[String]("backblaze.staticfiles.bucketId"),
          config.get[String]("backblaze.staticfiles.keyId"),
          config.get[String]("backblaze.staticfiles.key")
        )
        new B2FileRepository(backblazeConfig)
      }
      case "mock" => new MockFileRepository()
      case x => {
        throw new RuntimeException(
          f"Invalid value for key staticFilesRepositoryType: ${x}"
        )
      }
    }
  }

  @Provides
  def resourcePermissionRegistry(
      db: Database
  )(implicit
      ec: ExecutionContext
  ): ResourcePermissionRegistryLike =
    new ResourcePermissionRegistry(db)

  @Provides
  def cookieTokenManager(
      userTokenRepository: UserTokenRepository,
      tokenEncrypter: TokenEncrypter
  ): CookieTokenManagerLike =
    new CookieTokenManager(
      userTokenRepository,
      tokenEncrypter,
      "OHMYCARDS_AUTH"
    )

  @Provides
  def cookieUserIdentifier(
      cookieTokenManager: CookieTokenManagerLike,
      clock: SilhouetteClock
  )(implicit
      ec: ExecutionContext
  ): CookieUserIdentifierLike =
    new CookieUserIdentifier(cookieTokenManager, clock)

  @Provides
  def cardResourceHandler(
      repository: CardRepositoryLike,
      clock: SilhouetteClock,
      refCounter: ReferenceCounterLike,
      uuidGenerator: UUIDGeneratorLike
  )(implicit
      ex: ExecutionContext
  ): CardResourceHandlerLike =
    new CardResourceHandler(repository, clock, refCounter, uuidGenerator)
}

protected object Helpers {

  def shouldUseSendgrid(conf: Configuration) =
    conf.getOptional[String]("sendgrid.key").filterNot(_ == "").isDefined

}
