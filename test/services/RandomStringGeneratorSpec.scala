package services

import org.scalatestplus.play._

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
        generated mustBe "dPuBwqCZDvS3sYJaU8nT"
      }
    }

    "With a set of valid characters" should {
      val len = 40
      val chars = "a1".toSet
      val seed = 1231231

      val generated: String = (new RandomStringGenerator(Some(seed)).generate(len, Some(chars)))

      "generates expexted string with valid chars only" in {
        generated mustEqual "a1111111111a1a111a1aa1aaa1111111a11aa1aa"
      }

    }

  }

}
