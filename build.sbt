import sbt._
import sbt.io.Using

val scalaVer = "2.12.4"

lazy val commonSettings = Seq(
  name         := "sbt-tzdb",
  description  := "Sbt plugin to build custom timezone databases",
  version      := "0.1.1-SNAPSHOT",
  organization := "io.github.cquiroz",
  homepage     := Some(url("https://github.com/cquiroz/sbt-tzdb")),
  licenses     := Seq("BSD 3-Clause License" -> url("https://opensource.org/licenses/BSD-3-Clause")),

  scalaVersion       := scalaVer,

  javaOptions ++= Seq("-Dfile.encoding=UTF8"),
  autoAPIMappings := true,
  useGpg := true,

  publishArtifact in Test := false,
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := pomData,
  pomIncludeRepository := { _ => false },
) ++ scalafixSettings

lazy val sbt_tzdb = project
  .in(file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "sbt-tzdb",
    sbtPlugin := true,
    libraryDependencies ++= Seq(
      "io.github.cquiroz"    %% "kuyfi"            % "0.8.2",
      "org.apache.commons"   %  "commons-compress" % "1.12",
      "com.eed3si9n"         %% "gigahorse-okhttp" % "0.3.1",
      "com.github.pathikrit" %% "better-files"     % "3.4.0"
    ),
    addSbtPlugin("org.scala-js"      % "sbt-scalajs"  % "0.6.22")
  )

lazy val pomData =
  <scm>
    <url>git@github.com:cquiroz/sbt-tzdb.git</url>
    <connection>scm:git:git@github.com:cquiroz/sbt-tzdb.git</connection>
  </scm>
  <developers>
    <developer>
      <id>cquiroz</id>
      <name>Carlos Quiroz</name>
      <url>https://github.com/cquiroz</url>
      <roles>
        <role>Project Lead</role>
      </roles>
    </developer>
  </developers>
