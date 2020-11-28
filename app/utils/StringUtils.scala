package utils

object StringUtils {

  def splitByComma(s: String): List[String] =
    if (s == "") List()
    else List.from(s.split(",").map(_.trim))

}
