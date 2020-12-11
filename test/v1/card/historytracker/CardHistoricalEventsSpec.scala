package v1.card.historytracker

import org.scalatestplus.play.PlaySpec
import org.joda.time.DateTime
import play.api.libs.json.Json

class CardHistoricalEventLikeSpec extends PlaySpec {

  "json serialization" should {

    "serialize a card creation" in {
      val event: CardHistoricalEventLike = CardCreation(
        DateTime.parse("2020-01-01T01:01:01Z"),
        "1",
        "1"
      )
      Json.toJson(event) mustEqual Json.obj(
        "datetime" -> "2020-01-01T01:01:01.000Z",
        "eventType" -> "creation"
      )
    }

    "serialize a card update" in {
      val event: CardHistoricalEventLike = CardUpdate(
        DateTime.parse("2020-01-01T01:01:01Z"),
        "1",
        "1",
        Seq(
          StringFieldUpdate("title", "oldT", "newT"),
          StringFieldUpdate("body", "oldB", "newB"),
          TagsFieldUpdate("tags", List("A"), List("B"))
        )
      )
      Json.toJson(event) mustEqual Json.obj(
        "datetime" -> "2020-01-01T01:01:01.000Z",
        "eventType" -> "update",
        "fieldUpdates" -> Seq(
          Json.obj(
            "fieldName" -> "title",
            "oldValue" -> "oldT",
            "newValue" -> "newT",
            "fieldType" -> "string"
          ),
          Json.obj(
            "fieldName" -> "body",
            "oldValue" -> "oldB",
            "newValue" -> "newB",
            "fieldType" -> "string"
          ),
          Json.obj(
            "fieldName" -> "tags",
            "oldValue" -> Seq("A"),
            "newValue" -> Seq("B"),
            "fieldType" -> "tags"
          )
        )
      )
    }

    "serialize a card delete" in {
      val event: CardHistoricalEventLike = CardDeletion(
        DateTime.parse("2020-01-01T01:01:01Z"),
        "1",
        "1"
      )
      Json.toJson(event) mustEqual Json.obj(
        "datetime" -> "2020-01-01T01:01:01.000Z",
        "eventType" -> "deletion"
      )
    }

  }

}

class TagsFieldUpdateSpec extends PlaySpec {

  "fromRows" should {

    "return an empty sequence of rows" in {
      TagsFieldUpdate.fromRows(Seq()) mustEqual Seq()
    }

    "return two tag field updates" in {
      val rows = Seq(
        ("field1", "OLD", "A"),
        ("field1", "OLD", "B"),
        ("field1", "NEW", "C"),
        ("field2", "NEW", "D"),
        ("field3", "OLD", "E")
      )
      val updates = Seq(
        TagsFieldUpdate("field1", List("A", "B"), List("C")),
        TagsFieldUpdate("field2", List(), List("D")),
        TagsFieldUpdate("field3", List("E"), List())
      )
      TagsFieldUpdate.fromRows(rows) mustEqual updates
    }
  }

}
