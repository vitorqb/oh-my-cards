package services.TagsFilterMiniLang

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
    val paramsGen = new SqlParamNameGenerator()
    val parser = new TagsFilterParser(paramsGen) { override val buildParseTree = true }
    val result = ReportingParseRunner(parser.InputLine).run(statement)
    result.result match {
      case None => Failure(new ParsingError(ErrorUtils.printParseErrors(result)))
      case Some(node) => {
        val sqlStatement = node.serialize()
        val params = (paramsGen.generated zip node.getParams()).toMap
        Success(Result(sqlStatement, params))
      }
    }
  }
}

/** Param name generator for unique param names in queries.
  * 
  * This helper object generates "unique" strings that can be used by the TagsFilterMiniLang
  * to use as names for the sql parameters it generates in it's Result.
  */
class SqlParamNameGenerator() {

  var count: Int = 0
  var generated: List[String] = List()

  /**
    * Generates a unique name for a parameter.
    */
  def gen(): String = {
    val count_ = count
    count += 1
    val result = s"__TAGSMINILANG_PARAM_${count_}__"
    generated = generated ::: List(result)
    result
  }

}

/** Main parser class for the tags mini language.
  * 
  * This class implements all the logic to parse from the tags minilang to sql, using
  * parboiled.
  */
class TagsFilterParser(paramNameGen: SqlParamNameGenerator) extends Parser {

  /**
   * These case classes form the nodes of the AST.
   */
  sealed abstract class AstNode
  sealed abstract class SerializableNode extends AstNode {
    def serialize(): String
    def getParams(): List[String]
  }
  sealed abstract class ConnectorNode(members: List[AstNode]) extends SerializableNode

  case class FilterExprNode(
    tags: TagsNode,
    not: Option[NotNode],
    contains: ContainsNode,
    string: StringNode
  ) extends SerializableNode {

    def serialize(): String = {
      val paramName = paramNameGen.gen()
      var result = s"SELECT cardId FROM cardsTags WHERE LOWER(tag) = {$paramName}"
      if (not.isDefined) result = s"SELECT id FROM cards WHERE id NOT IN ($result)"
      result
    }

    def getParams(): List[String] = List(string.text.toLowerCase())
  }

  case class AndNode(members: List[SerializableNode]) extends ConnectorNode(members) {
    def serialize(): String = "SELECT * FROM (" + members.map(_.serialize).mkString(" INTERSECT ") + ")"
    def getParams(): List[String] = members.map(_.getParams).flatMap(identity)
  }

  case class OrNode(members: List[SerializableNode]) extends ConnectorNode(members) {
    def serialize(): String = "SELECT * FROM (" + members.map(_.serialize).mkString(" UNION ") + ")"
    def getParams(): List[String] = members.map(_.getParams).flatMap(identity)
  }

  case class StringNode(text: String) extends AstNode

  case class NotNode() extends AstNode

  case class TagsNode() extends AstNode

  case class ContainsNode() extends AstNode

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
