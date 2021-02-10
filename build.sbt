import sbt._
import sbt.io.Using

val scalaVer = "2.12.12"

Global / onChangedBuildSource := ReloadOnSourceChanges

resolvers in Global += Resolver.sonatypeRepo("public")

pluginCrossBuild / sbtVersion := "1.2.8"

inThisBuild(
  List(
    organization := "io.github.cquiroz",
    homepage := Some(url("https://github.com/cquiroz/sbt-zdb")),
    licenses := Seq("BSD 3-Clause License" -> url("https://opensource.org/licenses/BSD-3-Clause")),
    developers := List(
      Developer("cquiroz",
                "Carlos Quiroz",
                "carlos.m.quiroz@gmail.com",
                url("https://github.com/cquiroz"))
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/cquiroz/sbt-zdb"),
        "scm:git:git@github.com:cquiroz/sbt-zdb.git"
      )
    )
  )
)

lazy val commonSettings = Seq(
  name := "sbt-tzdb",
  description := "Sbt plugin to build custom timezone databases",
  organization := "io.github.cquiroz",
  scalaVersion := scalaVer,
  javaOptions ++= Seq("-Dfile.encoding=UTF8")
)

lazy val sbt_tzdb = project
  .in(file("sbt-tzdb"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings: _*)
  .settings(
    name := "sbt-tzdb",
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %% "kuyfi" % "1.3.0",
      "org.apache.commons" % "commons-compress" % "1.20",
      "com.eed3si9n" %% "gigahorse-okhttp" % "0.5.0",
      "org.typelevel" %% "cats-core" % "2.4.1",
      "org.typelevel" %% "cats-effect" % "2.3.1"
    ),
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
