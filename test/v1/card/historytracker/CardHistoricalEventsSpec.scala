package v1.card.historytracker

import org.scalatestplus.play.PlaySpec

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
        ("field3", "OLD", "E"),
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
