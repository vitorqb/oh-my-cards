package modules

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import services.{RandomStringGenerator, UUIDGenerator, Clock}
import java.util.UUID
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
import v1.card.CardElasticClientMock
import v1.card.CardElasticClientImpl
import v1.card.CardElasticClient

class Module extends AbstractModule with ScalaModule {

  /**
    * Simple configurations.
    */
  override def configure() = {
    bind[RandomStringGenerator].toInstance(new RandomStringGenerator)
    bind[UUIDGenerator].toInstance(new UUIDGenerator)
    bind[Clock].toInstance(new Clock)
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
  ): CardElasticClient = {
    if (conf.get[String]("test") == "1")
      new CardElasticClientMock()
    else
      new CardElasticClientImpl(elasticClient)
  }

}
