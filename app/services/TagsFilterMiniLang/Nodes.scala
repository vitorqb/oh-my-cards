package services.TagsFilterMiniLang.Nodes

import services.TagsFilterMiniLang.Helpers._
import com.sksamuel.elastic4s.requests.searches.SearchRequest

/**
  * The base class for AST nodes.
  */
sealed abstract class AstNode

/**
  * The node factory, which must return a new instace of SerializableNode for each node type.
  */
abstract trait NodeFactory[A] {
  def genOrNode(members: List[SerializableNode[A]]): OrNode[A]
  def genAndNode(members: List[SerializableNode[A]]): AndNode[A]
  def genFilterExprNode(
    tags: TagsNode,
    not: Option[NotNode],
    contains: ContainsNode,
    string: StringNode
  ): FilterExprNode[A]
}

/**
  * A class for a Serializable AST node.
  */
sealed abstract class SerializableNode[A] extends AstNode {
  def serialize(): A
  def getParams(): List[String]
}

/**
  * A class for a connector node (AND/OR)
  */
sealed abstract class ConnectorNode[A](members: List[AstNode]) extends SerializableNode[A]

/**
  * A node representing an `and` expression.
  */
abstract class AndNode[A](members: List[SerializableNode[A]]) extends ConnectorNode[A](members)

/**
  * A node representing an `or` expression.
  */
abstract class OrNode[A](members: List[SerializableNode[A]]) extends ConnectorNode[A](members)

/**
  * A node representing a filter expresion of the form (tags [NOT] CONTAINS "string").
  */
abstract class FilterExprNode[A](
  tags: TagsNode,
  not: Option[NotNode],
  contains: ContainsNode,
  string: StringNode
) extends SerializableNode[A]

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


//
// Sql Implementation
//
/**
  * A node representing an `and` with many expressions for sql parsing.
  */
case class SqlAndNode(members: List[SerializableNode[String]]) extends AndNode(members) {

  def serialize(): String =
    "SELECT * FROM (" + members.map(_.serialize()).mkString(" INTERSECT ") + ")"

  def getParams(): List[String] = members.map(_.getParams).flatMap(identity)
}

/**
  * A node representing an `or` with many expressions for sql parsing.
  */
case class SqlOrNode(members: List[SerializableNode[String]]) extends OrNode(members) {

  def serialize(): String =
    "SELECT * FROM (" + members.map(_.serialize()).mkString(" UNION ") + ")"

  def getParams(): List[String] = members.map(_.getParams).flatMap(identity)
}

/**
  * A node representing a filter expresion of the form (tags [NOT] CONTAINS "string") for sql parsing.
  */
case class SqlFilterExprNode(
  tags: TagsNode,
  not: Option[NotNode],
  contains: ContainsNode,
  string: StringNode,
  paramNameGen: SqlParamNameGenerator
) extends FilterExprNode[String](tags, not, contains, string) {

  def serialize(): String = {
    val paramName = paramNameGen.gen()
    var result = s"SELECT cardId FROM cardsTags WHERE LOWER(tag) = {$paramName}"
    if (not.isDefined) result = s"SELECT id FROM cards WHERE id NOT IN ($result)"
    result
  }

  def getParams(): List[String] = List(string.text.toLowerCase())
}

/**
  * Factory for sql nodes.
  */
class SqlNodeFactory(paramsGen: SqlParamNameGenerator) extends NodeFactory[String] {

  def genOrNode(members: List[SerializableNode[String]]): OrNode[String] = SqlOrNode(members)

  def genAndNode(members: List[SerializableNode[String]]): AndNode[String] = SqlAndNode(members)

  def genFilterExprNode(
    tags: TagsNode,
    not: Option[NotNode],
    contains: ContainsNode,
    string: StringNode
  ): FilterExprNode[String] = SqlFilterExprNode(tags, not, contains, string, paramsGen)

}


//
// ES Implementation
//
/**
  * A node representing an `and` with many expressions for ES parsing.
  */
case class ESAndNode(members: List[SerializableNode[SearchRequest]]) extends AndNode(members) {

  override def serialize(): SearchRequest = ???

  override def getParams(): List[String] = ???

}

class ESNodeFactory() extends NodeFactory[SearchRequest] {

  def genOrNode(members: List[SerializableNode[SearchRequest]]): OrNode[SearchRequest] = ???

  def genAndNode(members: List[SerializableNode[SearchRequest]]): AndNode[SearchRequest] = ???

  def genFilterExprNode(tags: TagsNode, not: Option[NotNode], contains: ContainsNode, string: StringNode): FilterExprNode[SearchRequest] = ???

  
}
