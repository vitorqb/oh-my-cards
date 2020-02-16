package services

import scala.util.Random
import com.google.inject.Provides

/**
  * A helper service that generates a random string.
  */
class RandomStringGenerator(val seed: Option[Int] = None) {

  val random: Random = seed match {
    case None => new Random()
    case Some(x) => new Random(x)
  }

  def generate(length: Int=30): String = random.alphanumeric.take(length).mkString

}
