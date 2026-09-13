name := "tzdb"

enablePlugins(TzdbPlugin)
enablePlugins(ScalaJSPlugin)

scalaVersion := "2.13.18"

crossScalaVersions := Seq("2.13.18", "2.12.21", "3.3.6")

val zonesFilterFn = {(z: String) => z == "America/Santiago" || z == "Pacific/Honolulu"}

zonesFilter := zonesFilterFn

dbVersion := TzdbPlugin.Version("2019c")

// doesn't work to do this `inThisBuild`
lazy val commonSettings = Seq(
  Compile / doc / scalacOptions --= Seq(
    "-Xfatal-warnings",
    "-deprecation"
  )
)

libraryDependencies += {
  val sjs = if (sbtVersion.value.startsWith("2.")) "scala-java-time" else "scala-java-time_sjs1"
  "io.github.cquiroz" %% sjs % "2.5.0"
}
