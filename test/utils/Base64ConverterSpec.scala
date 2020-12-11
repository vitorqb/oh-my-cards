package utils

import org.scalatestplus.play.PlaySpec

class Base64ConverterSpec extends PlaySpec {

  "encode and decode" should {
    "Encode byte array" in {
      val encoded = Base64Converter.encode("foo".getBytes())
      val decoded = Base64Converter.decode(encoded)
      decoded mustEqual "foo".getBytes()
    }
    "Encode string" in {
      val encoded = Base64Converter.encode("bar")
      val decoded = Base64Converter.decode(encoded)
      decoded mustEqual "bar".getBytes()
    }
    "Decode string" in {
      val encoded = "YmF6"
      val decoded = Base64Converter.decode(encoded)
      decoded mustEqual "baz".getBytes()
    }
  }

  "encodeString and decodeString" should {
    "Encode string" in {
      Base64Converter.encodeToString("foo") mustEqual "Zm9v"
    }
    "Decode string" in {
      Base64Converter.decodeToString("Zm9v") mustEqual "foo"
    }
  }

}
