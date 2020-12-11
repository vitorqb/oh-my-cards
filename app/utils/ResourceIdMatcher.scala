package utils.resourceidmatcher

import scala.util.matching.Regex

sealed trait ResourceIdentifier
final case class UUID(val value: String) extends ResourceIdentifier
final case class IntRef(val value: Int) extends ResourceIdentifier
final case class UnknownRef() extends ResourceIdentifier

trait ResourceIdMatcherLike {
  def run(x: String): ResourceIdentifier
}

class ResourceIdMatcher extends ResourceIdMatcherLike {

  override def run(x: String): ResourceIdentifier = {
    val uuidReg: Regex =
      "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$".r
    val intReg: Regex = "^[0-9]*$".r
    x match {
      case uuidReg(_*) => UUID(x)
      case intReg(_*)  => IntRef(x.toInt)
      case _           => UnknownRef()
    }
  }

}

object ResourceIdMatcher {
  def run(x: String) = new ResourceIdMatcher().run(x)
}
