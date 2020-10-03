package services


/**
  * Define helper methods to parse user inputs.
  */
class InputParser {

}

object InputParser {

  /**
    * The result for the parsing.
    */
  sealed trait Result[A]
  case class Good[A](parsedValue: A) extends Result[A]
  case class Bad[A](errorMsg: String) extends Result[A]

  val INVALID_UUID = Bad[String]("Invalid uuid")

  /**
    * Parses an user-inputed uuid.
    */
  def parseUUID(input: String): Result[String] = {
    val pattern = "^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$".r
    input match {
      case pattern(_) => Good(input)
      case _ => INVALID_UUID
    }
  }

}
