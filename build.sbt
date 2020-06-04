name := """oh-my-cards"""
organization := "com.example"

version := "0.6.0"

//Defines a custom config for functional tests
lazy val functionalTests = taskKey[Unit]("Run functional tests")
lazy val unitTests = taskKey[Unit]("Run unit tests")

//Project
lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    unitTests := {
      (testOnly in Test).toTask(" -- -l tags.FunctionalTests").value
    },
    functionalTests := {
      (testOnly in Test).toTask(" -- -n tags.FunctionalTests").value
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
