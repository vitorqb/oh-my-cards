package services

import org.scalatestplus.play.PlaySpec


class CounterUUIDGeneratorSpec extends PlaySpec {

  "Generate" should {
    "generate strings from an increasing counter" in {
      val generator = new CounterUUIDGenerator
      generator.generate() mustEqual "1"
      generator.generate() mustEqual "2"
    }
  }
}
