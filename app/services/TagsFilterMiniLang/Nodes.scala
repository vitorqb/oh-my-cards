package services.TagsFilterMiniLang.Nodes

import services.TagsFilterMiniLang.Helpers._

/**
  * The base class for AST nodes.
  */
sealed abstract class AstNode

/**
  * A class for a Serializable AST node.
  */
sealed abstract class SerializableNode extends AstNode {
  def serialize(paramsGen: SqlParamNameGenerator): String
  def getParams(): List[String]
}

/**
  * A class for a connector node (AND/OR)
  */
sealed abstract class ConnectorNode(members: List[AstNode]) extends SerializableNode

/**
  * A node representing a simple string.
  */
case class StringNode(text: String) extends AstNode

/**
  * A node representing negation (NOT)
  */
case class NotNode() extends AstNode

/**
  * A node representing the word `tags`
  */
case class TagsNode() extends AstNode

/**
  * A node represneting the word `contains`.
  */
case class ContainsNode() extends AstNode

/**
  * A node representing an `and` with many expressions.
  */
case class AndNode(members: List[SerializableNode]) extends ConnectorNode(members) {

  def serialize(paramGen: SqlParamNameGenerator): String =
    "SELECT * FROM (" + members.map(_.serialize(paramGen)).mkString(" INTERSECT ") + ")"

  def getParams(): List[String] = members.map(_.getParams).flatMap(identity)
}

/**
  * A node representing an `or` with many expressions.
  */
case class OrNode(members: List[SerializableNode]) extends ConnectorNode(members) {

  def serialize(paramGen: SqlParamNameGenerator): String =
    "SELECT * FROM (" + members.map(_.serialize(paramGen)).mkString(" UNION ") + ")"

  def getParams(): List[String] = members.map(_.getParams).flatMap(identity)
}

/**
  * A node representing a filter expresion of the form (tags [NOT] CONTAINS "string").
  */
case class FilterExprNode(
  tags: TagsNode,
  not: Option[NotNode],
  contains: ContainsNode,
  string: StringNode
) extends SerializableNode {

  def serialize(paramNameGen: SqlParamNameGenerator): String = {
    val paramName = paramNameGen.gen()
    var result = s"SELECT cardId FROM cardsTags WHERE LOWER(tag) = {$paramName}"
    if (not.isDefined) result = s"SELECT id FROM cards WHERE id NOT IN ($result)"
    result
  }

  def getParams(): List[String] = List(string.text.toLowerCase())
}
