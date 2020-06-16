package modules

import org.scalatestplus.play.PlaySpec
import org.mockito.MockitoSugar
import org.mockito._
import play.api.Configuration

class HelpersSpec extends PlaySpec with MockitoSugar {

  "shouldUseSendrig" should {

    "be true if config has sendgrid key" in {
      val config = mock[Configuration]
      when(config.getOptional[String]("sendgrid.key")).thenReturn(Some("FOO"))
      Helpers.shouldUseSendgrid(config) mustEqual true
    }

    "be false if config does not has sendgrid key" in {
      val config = mock[Configuration]
      when(config.getOptional[String]("sendgrid.key")).thenReturn(None)
      Helpers.shouldUseSendgrid(config) mustEqual false
    }

    "be false if config has sendgrid key set to empty string" in {
      val config = mock[Configuration]
      when(config.getOptional[String]("sendgrid.key")).thenReturn(Some(""))
      Helpers.shouldUseSendgrid(config) mustEqual false
    }

  }

}
