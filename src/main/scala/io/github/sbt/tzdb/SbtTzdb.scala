package io.gitub.sbt.tzdb

import java.io.{File => JFile}
import better.files._
import sbt._
import sbt.util.Logger
import Keys._
import cats._
import cats.implicits._
import cats.effect

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
    val includeTTBP: TaskKey[Boolean] =
      taskKey[Boolean]("Include also a provider for threeten bp")
  }

  import autoImport._
  override def trigger = noTrigger
  override lazy val buildSettings = Seq(
    zonesFilter := { case _ => true },
    dbVersion := LatestVersion,
    includeTTBP := false
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
          log.debug("tzdb files already available")
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
          includeTTBP = includeTTBP.value,
          log = streams.value.log
        )
      },
    )

  def tzdbCodeGenImpl(tzdbDir: JFile,
                      tzdbData: JFile,
                      srcDir: JFile,
                      zonesFilter: String => Boolean,
                      dbVersion: TZDBVersion,
                      includeTTBP: Boolean,
                      log: Logger): Seq[JFile] = {
    val ttbp = IOTasks.copyProvider(tzdbDir, "TzdbZoneRulesProvider.scala", "org.threeten.bp.zone", false)
    val jt = IOTasks.copyProvider(tzdbDir, "TzdbZoneRulesProvider.scala", "java.time.zone", true)
    val providerCopy = if (includeTTBP) List(ttbp, jt) else List(jt)
    (for {
      r <- IOTasks.providerPresent(tzdbDir, "TzdbZoneRulesProvider.scala", "java.time.zone")
      j <- if (r) effect.IO(Nil) else providerCopy.sequence
      f <- if (r) effect.IO(Nil) else IOTasks.generateTZDataSources(tzdbDir, tzdbData, log, includeTTBP, zonesFilter)
    } yield (j ::: f).map(_.toJava).toSeq).unsafeRunSync
  }
}
