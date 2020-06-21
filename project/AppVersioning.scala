import com.typesafe.config._
import java.io.File

/**
  * This allows us to read the variable `app.version` defined from the `application.conf`.
  * This means the CI system we can inject the version in `application.conf`, and we will
  * nicely read it here and `build.sbt` will properly set it there.
  */
object AppVersioning {
  val conf = ConfigFactory.parseFile(new File("conf/application.conf")).resolve()

  val appVersion =
    if (conf.hasPath("app.version"))
      conf.getString("app.version")
    else
      "0.0.0"
}
