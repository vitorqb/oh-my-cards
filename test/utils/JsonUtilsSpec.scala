package utils

import org.scalatestplus.play.PlaySpec
import utils.JodaToJsonUtils._
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.DateTimeComparator


class JsonUtilsSpec extends PlaySpec {

  "convertToDatetime" should {

    "convert to datetime with timezone" in {
      val result = convertToDatetime("2020-05-11T20:05:41.394+04:00")
      val expected = new DateTime(2020, 5, 11, 16, 5, 41, 394, DateTimeZone.UTC)
      result mustEqual expected        
    }

    "convert to datetime without milliseconds but with timezone" in {
      val result = convertToDatetime("2020-05-11T20:05:41+03:00")
      val expected = new DateTime(2020, 5, 11, 17, 5, 41, DateTimeZone.UTC)
      result mustEqual expected        
    }

    "fails to convert if no timezone" should {

      "no millis" in {
        try {
          convertToDatetime("2020-05-11T20:05:41")
          fail()
        } catch {
          case e: IllegalArgumentException => e.getMessage() must include ("Invalid format")
        }
      }

      "with millis" in {
        try {
          convertToDatetime("2020-05-11T20:05:41.123")
          fail()
        } catch {
          case e: IllegalArgumentException => e.getMessage() must include ("Invalid format")
        }
      }

      "No time" in {
        try {
          convertToDatetime("2020-05-11")
          fail()
        } catch {
          case e: IllegalArgumentException => e.getMessage() must include ("Invalid format")
        }
      }

    }
  }

}
