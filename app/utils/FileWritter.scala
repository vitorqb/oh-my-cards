package utils

import java.io.PrintWriter
import play.api.Logger

trait FileWritterLike {
  def write(fileName: String, content: String): Unit
}

class FileWritter() extends FileWritterLike {

  val logger = Logger(getClass())

  def write(fileName: String, content: String) = {
    new PrintWriter(fileName) {
      try {
        logger.info(s"Writting to file ${fileName}...")
        write(content)
      } finally {
        close()
      }
    }
  }
}
