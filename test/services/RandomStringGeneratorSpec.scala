package services

import org.scalatestplus.play._
import org.scalatestplus.play.guice._

class RandomStringGeneratorSpec extends PlaySpec {

  val alphanumeric = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

  "RandomStringGeneratorSpec.generate" should {

    "Without given seed" should {
      val len: Int = 30
      val generated: String = (new RandomStringGenerator).generate(len)

      "Generate a string of a given length" in {
        generated.length mustBe len
      }

      "With alphanumeric values only" in {
        generated.foreach { x =>
          generated must contain (x)
        }
      }
    }

    "With a given seed" should {
      val seed: Int = 111
      val len: Int = 20
      val generated: String = (new RandomStringGenerator(Some(seed))).generate(len)

      "Generates expected string" in {
        generated mustBe "7EZr1mu8JD6INWYdaAQV"
      }
    }

  }

}
