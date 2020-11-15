package utils

import play.api.mvc.Request
import play.api.mvc.AnyContent
import java.io.File


object RequestExtractorHelper {

  /**
    * Extracts a single file form a request.
    */
  def singleFile(request: Request[AnyContent]): Option[File] = {
    request.body.asMultipartFormData match {
      case Some(data) if data.files.length == 1 => Some(data.files.head.ref)
      case _ => None
    }
  }

}
