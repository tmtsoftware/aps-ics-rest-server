import sbt.Keys._

scalaVersion in ThisBuild := "2.13.0"

libraryDependencies += guice
libraryDependencies += "org.joda" % "joda-convert" % "1.9.2"
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % "4.11"

libraryDependencies += "io.lemonlabs" %% "scala-uri" % "1.5.1"
libraryDependencies += "net.codingwell" %% "scala-guice" % "4.2.6"

val `galil-prototype-version` = "0.1-SNAPSHOT"
libraryDependencies += "com.github.tmtsoftware.galil-prototype" %% "galil-commands" % `galil-prototype-version`
libraryDependencies += "com.github.tmtsoftware.galil-prototype" %% "galil-client" % `galil-prototype-version`

libraryDependencies += "com.github.tmtsoftware.csw" %% "csw-framework" % "1.0.0-RC2"

libraryDependencies += "com.github.tmtsoftware.csw" %% "csw-aas-installed" % "1.0.0"

//libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test


// The Play project itself
lazy val root = (project in file("."))
  .enablePlugins(Common, PlayScala)
  .settings(
    name := """aps-ics-rest-api"""
  )

// Documentation for this project:
//    sbt "project docs" "~ paradox"
//    open docs/target/paradox/site/index.html
lazy val docs = (project in file("docs")).enablePlugins(ParadoxPlugin).
  settings(
    paradoxProperties += ("download_url" -> "https://example.lightbend.com/v1/download/play-rest-api")
  )
