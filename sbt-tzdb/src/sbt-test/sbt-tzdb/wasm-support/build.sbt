name := "tzdb"

enablePlugins(TzdbPlugin)
enablePlugins(ScalaJSPlugin)

scalaVersion := "2.13.18"

// `LinkingInfo.linkTimeIf` needs Scala 2.12/2.13 or Scala 3.8+ (on 3.3 it is a run-time stub).
crossScalaVersions := Seq("2.13.18", "2.12.21", "3.9.0")

zonesFilter := { (z: String) => z == "Europe/Warsaw" }

// The point of the test: generate from data already on disk, with no download.
tzdbLocalDir := Some((ThisBuild / baseDirectory).value / "tzdb-data")

// A version that does not exist. If tzdbLocalDir were ignored — or merely used to seed
// a download — resolving this would 404 and the build would fail, so a green run is
// evidence that nothing was fetched.
dbVersion := TzdbPlugin.Version("0000z")

scalaJSUseMainModuleInitializer := true

tzdbWasmSupport := true

// The portable copy of the data must not use JS interop (`scala.scalajs.js`), so that it links
// for a Scala.js WebAssembly target without a JavaScript host.
@transient lazy val checkPortableData = taskKey[Unit]("Fails if the portable data uses scala.scalajs.js")
checkPortableData := {
  val _        = (Compile / managedSources).value
  val portable = ((Compile / sourceManaged).value ** "*_portable.scala").get()
  if (portable.isEmpty) sys.error("no portable data generated")
  val offenders = portable.filter(f => IO.read(f).contains("scala.scalajs.js"))
  if (offenders.nonEmpty) sys.error(s"JS interop in portable data: ${offenders.mkString(", ")}")
}

// On a JS target `linkTimeIf` must keep the JS data and drop the portable copy.
@transient lazy val checkJsLink = taskKey[Unit]("Fails if a JS link contains the portable data")
checkJsLink := {
  val _      = (Compile / fastLinkJS).value
  val outDir = (Compile / fastLinkJS / scalaJSLinkerOutputDirectory).value
  val js     = (outDir ** "*.js").get().map(f => IO.read(f)).mkString
  if (!js.contains("TzdbZoneRulesJsData")) sys.error("the JS data is not linked")
  if (js.contains("TzdbZoneRulesPortableData") || js.contains("zonedb_java_portable"))
    sys.error("the portable data is linked on a JS target")
}

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
