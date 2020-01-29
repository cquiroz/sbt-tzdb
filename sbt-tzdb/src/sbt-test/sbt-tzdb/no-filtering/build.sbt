name := "tzdb"

enablePlugins(TzdbPlugin)
enablePlugins(ScalaJSPlugin)

scalaVersion := "2.13.1"

crossScalaVersions := Seq("2.13.1", "2.12.10")

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

libraryDependencies ++= Seq(
  "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3"
)
