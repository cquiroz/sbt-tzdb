name := "tzdb"

enablePlugins(TzdbPlugin)
enablePlugins(ScalaJSPlugin)

scalaVersion := "2.13.14"

crossScalaVersions := Seq("2.13.14", "2.12.10", "3.4.1")

tzdbPlatform := TzdbPlugin.Platform.Jvm

dbVersion := TzdbPlugin.Version("2019c")

// doesn't work to do this `inThisBuild`
lazy val commonSettings = Seq(
  Compile / doc / scalacOptions --= Seq(
    "-Xfatal-warnings",
    "-deprecation"
  )
)

libraryDependencies ++= Seq(
  ("org.portable-scala" %%% "portable-scala-reflect" % "1.1.2").cross(CrossVersion.for3Use2_13),
  "io.github.cquiroz"  %%% "scala-java-time"        % "2.5.0"
)
