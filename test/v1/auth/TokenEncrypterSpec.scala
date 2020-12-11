package v1.auth

import org.scalatestplus.play._
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.Mockito._

class TokenEncrypterSpec extends PlaySpec with MockitoSugar {

  "TokenEncoderSpec" should {

    "Encrypt and decrypt" in {
      val token = mock[UserToken]
      when(token.token).thenReturn("TOKEN")
      val encrypter = new TokenEncrypter("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
      val encrypted = encrypter.encrypt(token)
      val decrypted = encrypter.decrypt(encrypted)
      decrypted.map(_.map(_.toChar).mkString) mustEqual Some("TOKEN")
    }
  }

}
