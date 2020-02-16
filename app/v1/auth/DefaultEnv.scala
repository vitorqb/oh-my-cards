package v1.auth

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.DummyAuthenticator


class DefaultEnv extends Env {
  type I = User
  type A = DummyAuthenticator
}
