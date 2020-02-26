package v1.card

import org.scalatestplus.play._


class CardListRequestParserSpec extends PlaySpec {

  val pageVal = 2
  val page = Some(pageVal.toString)
  val pageSizeVal = 5
  val pageSize = Some(pageSizeVal.toString)

  "CardListRequestParserSpec.parse" should {

    "Return error if page is missing" in {
      CardListRequestParser.parse(None, pageSize) mustEqual CardListRequestParser.missingPage
    }

    "Return error if pageSize is missing" in {
      CardListRequestParser.parse(page, None) mustEqual CardListRequestParser.missingPageSize
    }

    "Return error if page is not a number" in {
      (CardListRequestParser.parse(Some("FOO"), pageSize)
        mustEqual CardListRequestParser.genericError)
    }

    "Returns success if all good" in {
      import CardListRequestParser._
      CardListRequestParser.parse(page, pageSize) mustEqual Good(CardListRequestInput(2, 5))
    }

  }

}
