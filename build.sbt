import sbt._
import sbt.io.Using

val scalaVer = "2.12.10"

Global / onChangedBuildSource := ReloadOnSourceChanges

resolvers in Global += Resolver.sonatypeRepo("public")

lazy val commonSettings = Seq(
  name         := "sbt-tzdb",
  description  := "Sbt plugin to build custom timezone databases",
  version      := "0.4.0",
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
)

lazy val sbt_tzdb = project
  .in(file("sbt-tzdb"))
  .enablePlugins(SbtPlugin)
  .settings(commonSettings: _*)
  .settings(
    name := "sbt-tzdb",
    libraryDependencies ++= Seq(
      "io.github.cquiroz"    %%  "kuyfi" % "0.10.0",
      "org.apache.commons"   %  "commons-compress" % "1.20",
      "com.eed3si9n"         %% "gigahorse-okhttp" % "0.5.0",
      "com.github.pathikrit" %% "better-files"     % "3.8.0",
      "org.typelevel"        %% "cats-core"        % "2.1.0",
      "org.typelevel"        %% "cats-effect"      % "2.0.0",
    ),
    addSbtPlugin("org.scala-js"      % "sbt-scalajs"  % "0.6.32"),
    scriptedLaunchOpts := { scriptedLaunchOpts.value ++
      Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
  // .dependsOn(kuyfi)

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
