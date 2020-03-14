package services

import org.scalatestplus.play.PlaySpec

class InputParserSpec extends PlaySpec {

  "parseUUID" should {

    import InputParser._

    "accept a correct uuid" in {
      val uuid = "d0cf12dd-0bd1-44e0-b8d4-d511bf5d96b4"
      InputParser.parseUUID(uuid) mustEqual Good(uuid)
    }

    "fails with an incorrect uuid" in {
      InputParser.parseUUID("") mustEqual INVALID_UUID
      InputParser.parseUUID("d0cf12dd-0bd1-44e0-b8d4-d511bf5d96b") mustEqual INVALID_UUID
      InputParser.parseUUID("d0cf12dd0bd144e0b8d4d511bf5d96bs") mustEqual INVALID_UUID
    }

  }

}
