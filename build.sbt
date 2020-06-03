name := """oh-my-cards"""
organization := "com.example"

version := "0.6.0"

//Some globals
lazy val shoudlRunFunctionalTests =
  sys.env.getOrElse("OHMYCARDS_TEST_RUN_FUNCTIONAL_TESTS", 0) == 1

//Project
lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    //Skip functional tests unless explicitly asked
    Test / testOptions := {
      val old = (Test / testOptions).value
      if (shoudlRunFunctionalTests)
        old
      else
        old ++ Seq(Tests.Argument("-l", "tags.FunctionalTests"))
    }
  )

scalaVersion := "2.13.1"

resolvers ++= Seq(
  "Atlassian Releases" at "https://maven.atlassian.com/public/",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  Resolver.sonatypeRepo("snapshots")
 )

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
libraryDependencies += "com.mohiva" %% "play-silhouette-testkit" % "6.1.0" % Test
libraryDependencies ++= Seq(evolutions, jdbc)
libraryDependencies ++= Seq(
  "org.xerial" % "sqlite-jdbc" % "3.30.1",
  "org.playframework.anorm" %% "anorm" % "2.6.5",
  "com.mohiva" %% "play-silhouette" % "6.1.0",
  "net.codingwell" %% "scala-guice" % "4.2.6",
  "joda-time" % "joda-time" % "2.10.5",
  "org.mockito" %% "mockito-scala" % "1.11.2",
  "com.lihaoyi" %% "requests" % "0.5.1",
  "org.parboiled" %% "parboiled-scala" % "1.3.1"
)

//ElasticSearch setup
val elastic4sVersion = "7.6.1"
libraryDependencies ++= Seq(
  "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % elastic4sVersion
)
