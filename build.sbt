name := """oh-my-cards"""
organization := "com.example"

version := "0.2.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.1"

resolvers ++= Seq(
  "Atlassian Releases" at "https://maven.atlassian.com/public/",
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  Resolver.sonatypeRepo("snapshots")
 )

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
libraryDependencies ++= Seq(evolutions, jdbc)
libraryDependencies ++= Seq(
  "org.xerial" % "sqlite-jdbc" % "3.30.1",
  "org.playframework.anorm" %% "anorm" % "2.6.5",
  "com.mohiva" %% "play-silhouette" % "6.1.0",
  "net.codingwell" %% "scala-guice" % "4.2.6",
  "joda-time" % "joda-time" % "2.10.5",
  // "org.mockito" % "mockito-core" % "3.2.4"
  "org.mockito" %% "mockito-scala" % "1.11.2",
  "com.lihaoyi" %% "requests" % "0.5.1"
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"
