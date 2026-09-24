import sbt._
import sbt.io.Using

// The sbt 1.x leg builds on 2.12, the sbt 2.x leg on Scala 3, from a single source tree.
val scala212 = "2.12.21"
val scala3   = "3.9.0"

val sbt1 = "1.10.2"
val sbt2 = "2.0.8"

Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(
  List(
    scalaVersion := scala212,
    crossScalaVersions := Seq(scala212, scala3),
    organization := "io.github.cquiroz",
    versionScheme := Some("early-semver"),
    homepage := Some(url("https://github.com/cquiroz/sbt-tzdb")),
    licenses := Seq("BSD 3-Clause License" -> url("https://opensource.org/licenses/BSD-3-Clause")),
    developers := List(
      Developer("cquiroz",
                "Carlos Quiroz",
                "carlos.m.quiroz@gmail.com",
                url("https://github.com/cquiroz")
      )
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/cquiroz/sbt-tzdb"),
        "scm:git:git@github.com:cquiroz/sbt-tzdb.git"
      )
    )
  )
)

lazy val commonSettings = Seq(
  name := "sbt-tzdb",
  description := "Sbt plugin to build custom timezone databases",
  organization := "io.github.cquiroz",
  javaOptions ++= Seq("-Dfile.encoding=UTF8")
)

lazy val sbt_tzdb = project
  .in(file("sbt-tzdb"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings)
  .settings(
    name := "sbt-tzdb",
    pluginCrossBuild / sbtVersion := (scalaBinaryVersion.value match {
      case "2.12" => sbt1
      case _      => sbt2
    }),
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %% "kuyfi"            % "1.7.0",
      "org.apache.commons" % "commons-compress" % "1.28.0"
    ),
    // Built on a modern JDK, but consumers may still be on older ones.
    scalacOptions ++= (scalaBinaryVersion.value match {
      case "2.12" => Seq("-release:8")
      case _      => Seq("-release:17")
    }),
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
