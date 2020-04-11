package utils

import play.api.data.Form
import play.api.data.Mapping

object TagsUtils {
  private val tagMinLength = 1
  private val tagMaxLength = 100

  object Forms {
    import play.api.data.Forms.{list => fList, _}
    val tags: Mapping[List[String]] = fList(text(tagMinLength, tagMaxLength))
  }
}
