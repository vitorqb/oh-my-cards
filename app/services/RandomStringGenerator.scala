package services

import scala.util.Random

/**
  * A helper service that generates a random string.
  */
class RandomStringGenerator(val seed: Option[Int] = None) {

  import RandomStringGenerator._

  val random: Random = seed match {
    case None => new Random()
    case Some(x) => new Random(x)
  }

  /**
    * Generates a string made of characters in source.
    */
  def generate(length: Int=30, source: Option[Set[Char]]=None): String = {
    val indexedSource = source.getOrElse(alphaNumericChars).toIndexedSeq
    val indexes = (1 to length).map(_ => random.between(0, indexedSource.length))
    val values = indexes.map(indexedSource)
    values.mkString
  }

}

/**
  * Companion object
  */
object RandomStringGenerator {
  val alphaNumericChars = (('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')).toSet
}
