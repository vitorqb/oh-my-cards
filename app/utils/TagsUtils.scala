package utils

import play.api.data.Mapping
import play.api.data.validation._

object TagsUtils {
  private val tagMinLength = 1
  private val tagMaxLength = 100

  val noSpacesConstraint: Constraint[List[String]] =
    Constraint("constraints.nospaces")((tags) => {
      tags.find(_.contains(" ")) match {
        case None    => Valid
        case Some(_) => Invalid(Seq(ValidationError("Can not contain spaces")))
      }
    })

  object Forms {
    import play.api.data.Forms.{list => fList, _}
    val tags: Mapping[List[String]] =
      fList(text(tagMinLength, tagMaxLength)).verifying(noSpacesConstraint)
  }
}
