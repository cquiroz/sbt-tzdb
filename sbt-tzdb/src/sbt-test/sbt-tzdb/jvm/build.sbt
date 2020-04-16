name := "tzdb"

enablePlugins(TzdbPlugin)
enablePlugins(ScalaJSPlugin)

scalaVersion := "2.13.1"

crossScalaVersions := Seq("2.13.1", "2.12.10")

jsOptimized := false

dbVersion := TzdbPlugin.Version("2019c")

// doesn't work to do this `inThisBuild`
lazy val commonSettings = Seq(
  Compile / doc / scalacOptions --= Seq(
    "-Xfatal-warnings",
    "-deprecation"
  )
)

libraryDependencies ++= Seq(
  "org.portable-scala" %%% "portable-scala-reflect" % "1.0.0",
  "io.github.cquiroz" %%% "scala-java-time" % "2.0.0-RC3"
)
