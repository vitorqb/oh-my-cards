package utils

import java.io.PrintWriter
import play.api.libs.Files.SingletonTemporaryFileCreator

object TempFileWritter {

  def write(contents: String) = {
    val tempFi = SingletonTemporaryFileCreator.create("foo", "bar")
    tempFi.deleteOnExit()
    new PrintWriter(tempFi) {
      try {
        write(contents)
      } finally {
        close()
      }
    }
    tempFi
  }

}
