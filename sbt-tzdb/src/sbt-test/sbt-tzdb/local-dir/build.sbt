name := "tzdb"

enablePlugins(TzdbPlugin)
enablePlugins(ScalaJSPlugin)

scalaVersion := "2.13.18"

crossScalaVersions := Seq("2.13.18", "2.12.21", "3.3.6")

zonesFilter := { (z: String) => z == "Europe/Warsaw" }

// The point of the test: generate from data already on disk, with no download.
tzdbLocalDir := Some((ThisBuild / baseDirectory).value / "tzdb-data")

// A version that does not exist. If tzdbLocalDir were ignored — or merely used to seed
// a download — resolving this would 404 and the build would fail, so a green run is
// evidence that nothing was fetched.
dbVersion := TzdbPlugin.Version("0000z")

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
