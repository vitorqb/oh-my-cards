package utils

import org.scalatestplus.play.PlaySpec

class StringUtilsSpec extends PlaySpec {

  "splitByComma" in {
    StringUtils.splitByComma("") mustEqual List()
    StringUtils.splitByComma("a") mustEqual List("a")
    StringUtils.splitByComma(" a ") mustEqual List("a")
    StringUtils.splitByComma(" a, b ") mustEqual List("a", "b")
    StringUtils.splitByComma(" a,, b ") mustEqual List("a", "", "b")
  }

}
