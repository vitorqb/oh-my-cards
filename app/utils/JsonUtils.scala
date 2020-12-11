package utils

import play.api.libs.json.Writes
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import play.api.libs.json.JsString
import play.api.libs.json.Format
import play.api.libs.json.Reads
import play.api.libs.json.JsValue
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsError
import org.joda.time.format.DateTimeFormatterBuilder
import org.joda.time.DateTimeZone

object JodaToJsonUtils {

  def convertToDatetime(s: String): DateTime = {
    val parsers = Array(
      ISODateTimeFormat.dateTime().getParser(),
      ISODateTimeFormat.dateTimeNoMillis().getParser()
    )
    new DateTimeFormatterBuilder()
      .append(null, parsers)
      .toFormatter()
      .parseDateTime(s)
      .withZone(DateTimeZone.UTC)
  }

  def validateDateTime(s: String): Boolean = {
    try {
      convertToDatetime(s)
      return true
    } catch {
      case (e: IllegalArgumentException) => return false
    }
  }

  implicit val reads: Reads[DateTime] = new Reads[DateTime] {
    def reads(js: JsValue) =
      js match {
        case JsString(s) if validateDateTime(s) =>
          JsSuccess(convertToDatetime(s))
        case JsString(s) => JsError("error.expected.isoDateTime")
        case _           => JsError("error.expected.jsstring")
      }
  }

  implicit val writes: Writes[DateTime] = new Writes[DateTime] {
    def writes(d: DateTime) = JsString(ISODateTimeFormat.dateTime().print(d))
  }

  implicit val format: Format[DateTime] = Format(reads, writes)

}
