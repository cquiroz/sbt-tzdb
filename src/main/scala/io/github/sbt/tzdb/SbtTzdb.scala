package io.gitub.sbt.tzdb

import java.io.{File => JFile}
import better.files._
import sbt._
import sbt.util.Logger
import Keys._

object TzdbPlugin extends AutoPlugin {
  sealed trait TZDBVersion {
    val id: String
    val path: String
  }
  case object LatestVersion extends TZDBVersion {
    val id: String = "latest"
    val path: String = "tzdata-latest"
  }
  case class Version(version: String) extends TZDBVersion {
    val id: String = version
    val path: String = s"releases/tzdata$version"
  }

  object autoImport {

    /*
     * Settings
     */
    val zonesFilter = settingKey[String => Boolean]("Filter for zones")
    val dbVersion = settingKey[TZDBVersion]("Version of the tzdb")
    val tzdbCodeGen =
      taskKey[Seq[JFile]]("Generate scala.js compatible database of tzdb data")
    val downloadFromZip: TaskKey[Unit] =
      taskKey[Unit]("Download the tzdb tarball and extract it")
  }

  import autoImport._
  override def trigger = noTrigger
  override lazy val buildSettings = Seq(
    zonesFilter := { case _ => true },
    dbVersion := LatestVersion
  )
  override val projectSettings =
    Seq(
      downloadFromZip := {
        val log = streams.value.log
        val tzdbDir = (resourceManaged in Compile).value / "tzdb"
        val tzdbTarball = (resourceManaged in Compile).value / "tzdb.tar.gz"
        val tzdbVersion = dbVersion.value
        if (java.nio.file.Files.notExists(tzdbDir.toPath)) {
          var url = s"http://www.iana.org/time-zones/repository/${tzdbVersion.path}.tar.gz"
          val p = for {
            _   <- cats.effect.IO(log.info(s"tzdb data missing. downloading ${tzdbVersion.id} version to $tzdbDir..."))
            _   <- cats.effect.IO(log.info(s"downloading from $url"))
            _   <- cats.effect.IO(log.info(s"to file $tzdbTarball"))
            _   <- cats.effect.IO(IO.createDirectory(tzdbDir))
            _   <- IOTasks.download(url, tzdbTarball.toScala)
            _   <- IOTasks.gunzipTar(tzdbTarball, tzdbDir)
            _   <- cats.effect.IO(tzdbTarball.delete())
          } yield ()
          p.unsafeRunSync()
        } else {
          log.info("tzdb files already available")
        }
      },
      compile in Compile := (compile in Compile).dependsOn(downloadFromZip).value,
      sourceGenerators in Compile += Def.task {
        tzdbCodeGen.value
      },
      tzdbCodeGen := {
        tzdbCodeGenImpl(
          tzdbData = (resourceManaged in Compile).value / "tzdb",
          tzdbDir = (sourceManaged in Compile).value,
          srcDir = (resourceDirectory in Compile).value,
          zonesFilter = zonesFilter.value,
          dbVersion = dbVersion.value,
          log = streams.value.log
        )
      },
    )

  def tzdbCodeGenImpl(tzdbDir: JFile,
                      tzdbData: JFile,
                      srcDir: JFile,
                      zonesFilter: String => Boolean,
                      dbVersion: TZDBVersion,
                      log: Logger): Seq[JFile] =
  (for {
    t <- IOTasks.copyProvider(tzdbDir, "TzdbZoneRulesProvider.scala", "org.threeten.bp.zone", false)
    j <- IOTasks.copyProvider(tzdbDir, "TzdbZoneRulesProvider.scala", "java.time.zone", true)
    f <- IOTasks.generateTZDataSources(tzdbDir, tzdbData, log, zonesFilter)
  } yield ((t :: j :: Nil)  ::: f).map(_.toJava).toSeq).unsafeRunSync
}
