package v1.auth

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import com.google.inject.Inject
import scala.util.Try

/**
  * A service dedicated to encoding and decode user tokens.
  */
class TokenEncrypter @Inject() (val secretKey: String) {

  private def secretBytes = new SecretKeySpec(secretKey.getBytes, "AES")
  private def getCipher = Cipher.getInstance("AES")

  def encrypt(token: UserToken): Array[Byte] = {
    val cipher = getCipher
    cipher.init(Cipher.ENCRYPT_MODE, secretBytes)
    cipher.doFinal(token.token.getBytes)
  }

  def decrypt(encryptedToken: Array[Byte]): Option[Array[Byte]] = {
    Try {
      val cipher = getCipher
      cipher.init(Cipher.DECRYPT_MODE, secretBytes)
      cipher.doFinal(encryptedToken)
    }.toOption
  }
}
