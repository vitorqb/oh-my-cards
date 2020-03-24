package v1.card

import org.scalatestplus.play._
import play.api.test.FakeRequest
import play.api.libs.json.JsValue
import play.api.libs.json.JsDefined
import play.api.libs.json.JsUndefined
import test.utils.JsonUtils
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.api.i18n.Lang
import play.api.i18n.DefaultMessagesApi
import v1.auth.User

trait CardListRequestParserTestUtils extends JsonUtils {
  import CardListRequestParser._

  implicit class EnrichedResult[T](private val x: Either[CardListRequestInput, JsValue]) {
    def isBadWithJsKey(k: String) = x match {
      case Left(_) => false
      case Right(x: JsValue) => x hasKey k
      case Right(x) => false
    }
  }

}

class CardListRequestInputSpec extends PlaySpec {

  "ToCardListRequest" should {
    val user = User("A", "B")

    "Without tags" in {
      (CardListRequestInput(10, 20, None, None).toCardListRequest(user)
        mustEqual CardListRequest(10, 20, "A", List(), List()))
    }

    "With tags" in {
      (CardListRequestInput(10, 20, Some("foo,bar, baz"), None).toCardListRequest(user)
        mustEqual CardListRequest(10, 20, "A", List("foo", "bar", "baz"), List()))
    }

    "With tagsNot" in {
      (CardListRequestInput(10, 20, Some("foo,bar, baz"), Some("B,C")).toCardListRequest(user)
        mustEqual CardListRequest(10, 20, "A", List("foo", "bar", "baz"), List("B", "C")))
    }

  }

}

class CardListRequestParserSpec extends PlaySpec with CardListRequestParserTestUtils {

  implicit val messagesProvider: MessagesProvider = MessagesImpl(Lang("en"), new DefaultMessagesApi)
  val pageVal = 2
  val page = Some(pageVal.toString)
  val pageSizeVal = 5
  val pageSize = Some(pageSizeVal.toString)

  "CardListRequestParser.parse with implicit request" should {
    import CardListRequestParser._

    "Base" in {
      implicit val request = FakeRequest("GET", "/foo?page=1&pageSize=2")
      CardListRequestParser.parse() mustEqual Left(CardListRequestInput(1,2,None,None))
    }

    "Return error if page is missing" in {
      implicit val request = FakeRequest("GET", "/foo?pageSize=1")
      CardListRequestParser.parse().isBadWithJsKey("page") mustEqual true
    }

    "Return error if pageSize is missing" in {
      implicit val request = FakeRequest("GET", "/foo?page=1")
      CardListRequestParser.parse().isBadWithJsKey("pageSize") mustEqual true
    }

    "Return error if page is not a number" in {
      implicit val request = FakeRequest("GET", "/foo?page=aaa&pageSize=2")
      CardListRequestParser.parse().isBadWithJsKey("page") mustEqual true
    }

    "Returns success if all good" in {
      implicit val request = FakeRequest("GET", "/foo?page=2&pageSize=2")
      CardListRequestParser.parse() mustEqual Left(CardListRequestInput(2,2,None,None))
    }

    "With tags work fine" in {
      implicit val request = FakeRequest("GET", "/foo?page=2&pageSize=2&tags=foo,bar")
      (CardListRequestParser.parse()
        mustEqual
        Left(CardListRequestInput(2,2,Some("foo,bar"),None)))
    }

    "With tagsNot work fine" in {
      implicit val request = FakeRequest("GET", "/foo?page=2&pageSize=2&tags=foo,bar&tagsNot=a")
      (CardListRequestParser.parse()
        mustEqual
        Left(CardListRequestInput(2,2,Some("foo,bar"),Some("a"))))
    }

  }

}

