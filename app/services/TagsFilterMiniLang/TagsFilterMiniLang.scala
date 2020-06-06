package services.TagsFilterMiniLang

import services.TagsFilterMiniLang.Nodes._
import services.TagsFilterMiniLang.Helpers._

import org.parboiled.scala._
import org.parboiled.errors.{ErrorUtils, ParsingException}
import scala.util.Try
import scala.util.Failure
import scala.util.Success

/**
  * Custom exceptions
  */
final case class ParsingError(
  val message: String,
  val cause: Throwable = None.orNull
) extends Exception(message, cause)


/** Helpers Class to host the parsing result.
  * 
  * @param sql The sql-like string statement for filtering cardId.
  * @param params A map of ParamName -> ParamValue for the sql statement.
  */
sealed case class Result(sql: String, params: Map[String, String])


/** Object providing the interface for clients.
  * 
  */
object TagsFilterMiniLang {

  /** Parses a statement in the tags mini language.
    * 
    * Transforms a statement in the tags mini language into a sql statement and params
    * that can be used to filter the cards ids. See the tests for examples.
    * 
    * @param statement The statement in the tags query mini language.
    */
  def parse(statement: String): Try[Result] = {
    val parser = new TagsFilterParser() { override val buildParseTree = true }
    val result = ReportingParseRunner(parser.InputLine).run(statement)
    result.result match {
      case None => Failure(new ParsingError(ErrorUtils.printParseErrors(result)))
      case Some(node) => {
        val paramsGen = new SqlParamNameGenerator()
        val sqlStatement = node.serialize(paramsGen)
        val params = (paramsGen.generated zip node.getParams()).toMap
        Success(Result(sqlStatement, params))
      }
    }
  }
}

/** Main parser class for the tags mini language.
  * 
  * This class implements all the logic to parse from the tags minilang to sql, using
  * parboiled.
  */
class TagsFilterParser() extends Parser {

  def InputLine: Rule1[ConnectorNode] = rule { WhiteSpace ~ Connector ~ EOI }

  def Connector: Rule1[ConnectorNode] = rule { And | Or }

  def And = rule {
    (
      WhiteSpace ~ "(" ~ WhiteSpace ~
        zeroOrMore( FilterExpr | Connector, separator=AndSeparator) ~
        WhiteSpace ~ ")" ~ WhiteSpace
    ~~> AndNode
    )
  }

  def AndSeparator = rule { WhiteSpace ~ ignoreCase("and") ~ WhiteSpace }

  def Or = rule {
    (
      WhiteSpace ~ "(" ~ WhiteSpace ~
        zeroOrMore( FilterExpr | Connector, separator=OrSeparator) ~
        WhiteSpace ~ ")" ~ WhiteSpace
    ~~> OrNode
    )
  }

  def OrSeparator = rule { WhiteSpace ~ ignoreCase("or") ~ WhiteSpace }

  def FilterExpr = rule {
    (
      "(" ~ WhiteSpace ~
      Tags ~ WhiteSpace ~ optional(Not) ~ WhiteSpace ~ Contains ~ WhiteSpace ~ String
      ~ WhiteSpace ~ ")"
    ) ~~> FilterExprNode
  }

  def Not = rule { ignoreCase("not") ~ push(NotNode()) }

  def Tags = rule { ignoreCase("tags") ~ push(TagsNode()) }

  def Contains = rule { ignoreCase("contains") ~ push(ContainsNode()) }

  def String = rule { "'" ~ zeroOrMore(!"'" ~ ANY) ~> StringNode ~ "'" }

  def WhiteSpace = rule { zeroOrMore(anyOf(" \n\r\t\f")) }
}
