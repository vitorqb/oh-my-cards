package v1.auth

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.DummyAuthenticator
import com.mohiva.play.silhouette.api.Silhouette

class DefaultEnv extends Env {
  type I = User
  type A = DummyAuthenticator
}

/**
  * Used for tests when we need to inject Silhouette[DefaultEnv]
  * see https://github.com/playframework/playframework/issues/6174
  */
trait SilhouetteEnvWrapper {
  val silhouette: Silhouette[DefaultEnv]
}
