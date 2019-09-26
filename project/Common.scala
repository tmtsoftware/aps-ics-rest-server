import sbt.Keys._
import sbt.{Resolver, url, _}
import sbt.plugins.JvmPlugin

/**
  * Settings that are comment to all the SBT projects
  */
object Common extends AutoPlugin {
  override def trigger = allRequirements
  override def requires: sbt.Plugins = JvmPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    organization := "org.tmt",
    organizationName := "TMT",
    scalaVersion := "2.13.0",
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-Xlint",
//      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Xfuture"
    ),
    javacOptions in (Compile, doc) ++= Seq("-Xdoclint:none"),
    resolvers += "jitpack" at "https://jitpack.io",
    version := "0.0.1",
    fork := true,
    parallelExecution in Test := false,
    autoCompilerPlugins := true
  )
}
