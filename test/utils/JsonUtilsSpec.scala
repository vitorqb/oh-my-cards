package utils

import org.scalatestplus.play.PlaySpec
import utils.JodaToJsonUtils._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone


class JsonUtilsSpec extends PlaySpec {

  val timeZone = DateTimeZone.forID("Europe/Madrid")

  "convertToDatetime" should {

    "convert to datetime with timezone" in {
      (convertToDatetime("2020-05-11T20:05:41.394+02:00")
        mustEqual
        new DateTime(2020, 5, 11, 20, 5, 41, 394, timeZone))
    }

    "convert to datetime without timezone" in {
      convertToDatetime("2020-05-11T20:05:41.394") mustEqual new DateTime(2020, 5, 11, 20, 5, 41, 394)
    }

    "convert to datetime without milliseconds" in {
      convertToDatetime("2020-05-11T20:05:41") mustEqual new DateTime(2020, 5, 11, 20, 5, 41)
    }

    "convert to datetime without milliseconds but with timezone" in {
      convertToDatetime("2020-05-11T20:05:41+02:00") mustEqual new DateTime(2020, 5, 11, 20, 5, 41)
    }
  }

}
