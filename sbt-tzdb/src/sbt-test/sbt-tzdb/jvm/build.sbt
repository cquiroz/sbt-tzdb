name := "tzdb"

enablePlugins(TzdbPlugin)
enablePlugins(ScalaJSPlugin)

scalaVersion := "2.13.18"

crossScalaVersions := Seq("2.13.18", "2.12.21", "3.3.6")

tzdbPlatform := TzdbPlugin.Platform.Jvm

dbVersion := TzdbPlugin.Version("2019c")

// doesn't work to do this `inThisBuild`
lazy val commonSettings = Seq(
  Compile / doc / scalacOptions --= Seq(
    "-Xfatal-warnings",
    "-deprecation"
  )
)

libraryDependencies ++= {
  val sbt2    = sbtVersion.value.startsWith("2.")
  val reflect = if (sbt2) "portable-scala-reflect" else "portable-scala-reflect_sjs1"
  val sjt     = if (sbt2) "scala-java-time" else "scala-java-time_sjs1"
  Seq(
    ("org.portable-scala" %% reflect % "1.1.2").cross(CrossVersion.for3Use2_13),
    "io.github.cquiroz"   %% sjt     % "2.5.0"
  )
}
