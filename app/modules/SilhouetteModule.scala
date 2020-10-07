package modules

import com.google.inject.AbstractModule
import net.codingwell.scalaguice.ScalaModule
import com.google.inject.Provides
import com.mohiva.play.silhouette.api.services.IdentityService
import v1.auth.{User,UserService,BearerTokenRequestProvider}
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.impl.authenticators.DummyAuthenticator
import com.mohiva.play.silhouette.api.EventBus
import com.mohiva.play.silhouette.api.Environment
import v1.auth.{DefaultEnv,SilhouetteEnvWrapper}
import scala.concurrent.ExecutionContext.Implicits.global
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.SilhouetteProvider
import com.mohiva.play.silhouette.impl.authenticators.DummyAuthenticatorService
import v1.auth.UserTokenRepository
import com.mohiva.play.silhouette.api.util.{Clock=>SilhouetteClock}


/**
  * The Guice module for Silhouette
  */
class SilhouetteModule extends AbstractModule with ScalaModule {

  /**
    * Simple configurations.
    */
  override def configure() = {
    bind[Silhouette[DefaultEnv]].to[SilhouetteProvider[DefaultEnv]]
    bind[AuthenticatorService[DummyAuthenticator]].toInstance(new DummyAuthenticatorService)
    bind[IdentityService[User]].to[UserService]
  }

  @Provides
  def providesBearerTokenRequestProvider(userTokenRepository: UserTokenRepository, clock: SilhouetteClock) = {
    new BearerTokenRequestProvider(userTokenRepository, clock)
  }

  /**
    * Provides the Slhouette Environment for the App.
    */
  @Provides
  def provideEnvironment(
    identityService: IdentityService[User],
    authenticatorService: AuthenticatorService[DummyAuthenticator],
    bearerTokenRequestProvider: BearerTokenRequestProvider,
    eventBus: EventBus): Environment[DefaultEnv] = {

    Environment[DefaultEnv](
      identityService,
      authenticatorService,
      Seq(bearerTokenRequestProvider),
      eventBus
    )
  }

  /**
    * Used for tests when we need to inject Silhouette[DefaultEnv]
    * see https://github.com/playframework/playframework/issues/6174
    */
  @Provides
  def provideSilhouette(s: Silhouette[DefaultEnv]): SilhouetteEnvWrapper =
    new SilhouetteEnvWrapper { val silhouette = s }

}
