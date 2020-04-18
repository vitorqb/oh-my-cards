package services.TagsFilterMiniLang

import org.scalatestplus.play.PlaySpec
import test.utils._

class TagsFilterMiniLangSped extends PlaySpec with StringUtils {

  "parse" should {

    "parse a simple sentence" in {
      val statement = """((tags CONTAINS 'FOO'))"""
      val result = TagsFilterMiniLang.parse(statement)
      val expStatement = """
        SELECT * FROM (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_0__})
      """.cleanForComparison
      val expParams = Map("__TAGSMINILANG_PARAM_0__" -> "foo")
      result.get mustEqual Result(expStatement, expParams)
    }

    "parse a simple sentence with a negative" in {
      val statement = """((tags NOT CONTAINS 'foo'))"""
      val result = TagsFilterMiniLang.parse(statement)
      val expStatement = """
        SELECT * FROM 
        (SELECT id FROM cards WHERE id NOT IN 
           (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_0__}))
      """.cleanForComparison
      val expParams = Map("__TAGSMINILANG_PARAM_0__" -> "foo")
      result.get mustEqual Result(expStatement, expParams)
    }

    "parse a simple sentence with and" in {
      val statement = """((tags NOT CONTAINS 'foo') AND (tags NOT CONTAINS 'bar'))"""
      val result = TagsFilterMiniLang.parse(statement)
      val expectedStatement =
        """SELECT * FROM 
           (SELECT id FROM cards WHERE id NOT IN 
              (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_0__})
            INTERSECT
            SELECT id FROM cards WHERE id NOT IN
              (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_1__}))
        """.cleanForComparison
      val expParams = Map("__TAGSMINILANG_PARAM_0__" -> "foo", "__TAGSMINILANG_PARAM_1__" -> "bar")
      result.get mustEqual Result(expectedStatement, expParams)
    }

    "parse a simple sentence with or" in {
      val statement = """((tags NOT CONTAINS 'foo') OR (tags CONTAINS 'bar'))"""
      val result = TagsFilterMiniLang.parse(statement)
      val expectedStatement =
        """SELECT * FROM
           (SELECT id FROM cards WHERE id NOT IN
              (SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_0__})
            UNION
            SELECT cardId FROM cardsTags WHERE LOWER(tag) = {__TAGSMINILANG_PARAM_1__})
        """.cleanForComparison
      val expParams = Map("__TAGSMINILANG_PARAM_0__" -> "foo", "__TAGSMINILANG_PARAM_1__" -> "bar")
      result.get mustEqual Result(expectedStatement, expParams)
    }

    "parse a simple sentence with or and and" in {
      val statement =
        """(((tags CONTAINS 'OhMyCards') AND (tags NOT CONTAINS 'done'))
            OR
            ((tags CONTAINS 'HighPriority') AND (tags NOT CONTAINS 'done'))
            OR
            (tags CONTAINS 'InProgress'))""".cleanForComparison
      val result = TagsFilterMiniLang.parse(statement)
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
      result.get mustEqual Result(expectedStatement, expParams)
    }
  }

  "fails with invalid input" in {
    val statement = "((foo CONTAINS 'bar'))"
    val result = TagsFilterMiniLang.parse(statement)
    val experrmsg = """Invalid input 'f', expected WhiteSpace, Tags, FilterExpr,
                       Connector or ')' (line 1, pos 3)""".cleanForComparison
    val errmsg = result.failed.get.asInstanceOf[ParsingError].message
    errmsg.cleanForComparison mustEqual errmsg.cleanForComparison
  }

}

class SqlParamNameGeneratorSpec extends PlaySpec {

  "gen" should {
    "generate two names" in {
      val generator = new SqlParamNameGenerator()
      generator.gen() mustEqual "__TAGSMINILANG_PARAM_0__"
      generator.gen() mustEqual "__TAGSMINILANG_PARAM_1__"
      generator.count mustEqual 2
      generator.generated mustEqual List("__TAGSMINILANG_PARAM_0__", "__TAGSMINILANG_PARAM_1__")
    }
  }

}
