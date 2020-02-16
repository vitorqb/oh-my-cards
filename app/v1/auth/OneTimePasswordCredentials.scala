package v1.auth

case class OneTimePasswordCredentials(email: String, oneTimePassword: String)

object OneTimePasswordCredentials {

  def fromTokenInput(input: TokenInput): OneTimePasswordCredentials = {
    new OneTimePasswordCredentials(input.email, input.oneTimePassword)
  }

}
