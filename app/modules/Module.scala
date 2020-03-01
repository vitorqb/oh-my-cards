package modules

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import services.{RandomStringGenerator, UUIDGenerator, Clock}
import java.util.UUID
import services.MailService
import scala.concurrent.ExecutionContext
import play.api.libs.ws.WSClient
import play.api.Configuration
import services.{MailServiceImpl,MailServiceFakeImpl}
import com.google.inject.Provides
import v1.auth.TokenEncrypter

class Module extends AbstractModule with ScalaModule {

  /**
    * Simple configurations.
    */
  override def configure() = {
    bind[RandomStringGenerator].toInstance(new RandomStringGenerator)
    bind[UUIDGenerator].toInstance(new UUIDGenerator)
    bind[Clock].toInstance(new Clock)
  }

  @Provides
  def provideMailService(
    implicit ec: ExecutionContext,
    ws: WSClient,
    conf: Configuration): MailService = {
    if (conf.get[String]("test") == "1") {
      new MailServiceFakeImpl(conf)
    }
    new MailServiceImpl(ws, conf)
  }

  @Provides
  def provideTokenEncrypter(conf: Configuration): TokenEncrypter = {
    new TokenEncrypter(conf.get[String]("play.http.secret.key"))
  }

}
