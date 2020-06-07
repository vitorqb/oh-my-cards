package services.TagsFilterMiniLang

import org.scalatestplus.play.PlaySpec
import test.utils._
import com.sksamuel.elastic4s.requests.searches.queries.Query
import play.api.libs.json.Json
import play.api.libs.json.JsValue

class TagsFilterMiniLangSped extends PlaySpec with StringUtils {

  val statement1 = """((tags CONTAINS 'FOO'))"""
  val statement2 = """((tags NOT CONTAINS 'foo'))"""
  val statement3 = """((tags NOT CONTAINS 'foo') AND (tags NOT CONTAINS 'bar'))"""
  val statement4 = """((tags NOT CONTAINS 'foo') OR (tags CONTAINS 'bar'))"""
  val statement5 =
    """(((tags CONTAINS 'OhMyCards') AND (tags NOT CONTAINS 'done'))
        OR
        ((tags CONTAINS 'HighPriority') AND (tags NOT CONTAINS 'done'))
        OR
        (tags CONTAINS 'InProgress'))"""

  "parseAsSql" should {

    "parse a simple sentence" in {
      val result = TagsFilterMiniLang.parseAsSql(statement1)
      val expStatement = """
        SELECT * FROM (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_0__})
      """.cleanForComparison
      val expParams = Map("__TAGSMINILANG_PARAM_0__" -> "foo")
      result.get mustEqual SqlResult(expStatement, expParams)
    }

    "parse a simple sentence with a negative" in {
      val result = TagsFilterMiniLang.parseAsSql(statement2)
      val expStatement = """
        SELECT * FROM 
        (SELECT id FROM cards WHERE id NOT IN 
           (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_0__}))
      """.cleanForComparison
      val expParams = Map("__TAGSMINILANG_PARAM_0__" -> "foo")
      result.get mustEqual SqlResult(expStatement, expParams)
    }

    "parse a simple sentence with and" in {
      val result = TagsFilterMiniLang.parseAsSql(statement3)
      val expectedStatement =
        """SELECT * FROM
           (SELECT id FROM cards WHERE id NOT IN 
              (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_0__})
            INTERSECT
            SELECT id FROM cards WHERE id NOT IN
              (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_1__}))
        """.cleanForComparison
      val expParams = Map("__TAGSMINILANG_PARAM_0__" -> "foo", "__TAGSMINILANG_PARAM_1__" -> "bar")
      result.get mustEqual SqlResult(expectedStatement, expParams)
    }

    "parse a simple sentence with or" in {
      val result = TagsFilterMiniLang.parseAsSql(statement4)
      val expectedStatement =
        """SELECT * FROM
           (SELECT id FROM cards WHERE id NOT IN
              (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_0__})
            UNION
            SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_1__})
        """.cleanForComparison
      val expParams = Map("__TAGSMINILANG_PARAM_0__" -> "foo", "__TAGSMINILANG_PARAM_1__" -> "bar")
      result.get mustEqual SqlResult(expectedStatement, expParams)
    }

    "parse a simple sentence with or and and" in {
      val result = TagsFilterMiniLang.parseAsSql(statement5)
      val expectedStatement =
        """SELECT * FROM
            (SELECT * FROM 
             (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_0__}
              INTERSECT
              SELECT id FROM cards WHERE id NOT IN
                (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_1__}))
             UNION SELECT * FROM
             (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_2__}
              INTERSECT
              SELECT id FROM cards WHERE id NOT IN
                (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_3__}))
             UNION
             SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_4__})
        """.cleanForComparison
      val expParams = Map(
        "__TAGSMINILANG_PARAM_0__" -> "ohmycards",
        "__TAGSMINILANG_PARAM_1__" -> "done",
        "__TAGSMINILANG_PARAM_2__" -> "highpriority",
        "__TAGSMINILANG_PARAM_3__" -> "done",
        "__TAGSMINILANG_PARAM_4__" -> "inprogress"
      )
      result.get mustEqual SqlResult(expectedStatement, expParams)
    }
    "fails with invalid input" in {
      val statement = "((foo CONTAINS 'bar'))"
      val result = TagsFilterMiniLang.parseAsSql(statement)
      val experrmsg = """Invalid input 'f', expected WhiteSpace, Tags, FilterExpr,
                       Connector or ')' (line 1, pos 3)""".cleanForComparison
      val errmsg = result.failed.get.asInstanceOf[ParsingError].message
      errmsg.cleanForComparison mustEqual errmsg.cleanForComparison
    }
  }

  "parseAsES" should {

    def toJson(aQuery: Query): JsValue = {
      import com.sksamuel.elastic4s.ElasticDsl._
      Json.parse(search("cards").query(aQuery).request.entity.get.get)
    }

    def run(statement: String): JsValue = toJson(TagsFilterMiniLang.parseAsES(statement).get)

    "simple query" in {
      val result = run(statement1)
      val expJson = Json.parse("""
        {"query": {"bool": {"must": [{"term": {"tags": {"value": "foo"}}}]}}}
      """)
      result mustEqual expJson
    }

    "simple sentence with negation" in {
      val result = run(statement2)
      val expJson = Json.parse("""
        {"query": {"bool": {"must": [{"bool": {"must_not": [{"term": {"tags": {"value": "foo"}}}]}}]}}}
      """)
      result mustEqual expJson
    }

    "statement with `and` and `not`" in {
      val result = run(statement3)
      val expJson = Json.parse("""
        {"query": {"bool": {"must": 
           [{"bool": {"must_not": [{"term": {"tags": {"value": "foo"}}}]}},
            {"bool": {"must_not": [{"term": {"tags": {"value": "bar"}}}]}}]
        }}}
      """)
      result mustEqual expJson
    }

    "statement with `or` and `not`" in {
      val result = run(statement4)
      val expJson = Json.parse("""
        {"query": {
           "bool": {
             "should": [
               {"bool": {"must_not": [{"term": {"tags": {"value": "foo"}}}]}},
               {"term": {"tags": {"value": "bar"}}}
             ],
             "minimum_should_match": "1"
           }
          }
        }
      """)
      result mustEqual expJson
    }

    "complex query with and and ors" in {
      val result = run(statement5)
      val expJson = Json.parse("""
        {"query": {
           "bool": {
             "should": [
               {"bool": {
                 "must": [
                   {"term": {"tags": {"value": "ohmycards"}}},
                   {"bool": {"must_not": [{"term": {"tags": {"value": "done"}}}]}}
                 ]
               }},
               {"bool": {
                 "must": [
                   {"term": {"tags": {"value": "highpriority"}}},
                   {"bool": {"must_not": [{"term": {"tags": {"value": "done"}}}]}}
                 ]
               }},
               {"term": {"tags": {"value": "inprogress"}}}
             ],
             "minimum_should_match": "1"
           }
          }
        }
      """)
      result mustEqual expJson
    }

  }

}
