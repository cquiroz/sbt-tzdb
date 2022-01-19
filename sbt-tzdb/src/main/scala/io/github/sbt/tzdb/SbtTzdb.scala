package io.github.sbt.tzdb

import java.io.{ File => JFile }
import sbt._
import sbt.util.Logger
import sbt.util.CacheImplicits._
import Keys._
import cats.effect

object TzdbPlugin extends AutoPlugin {
  sealed trait TZDBVersion {
    val id: String
    val path: String
  }
  case object LatestVersion extends TZDBVersion {
    val id: String   = "latest"
    val path: String = "tzdata-latest"
  }
  final case class Version(version: String) extends TZDBVersion {
    val id: String   = version
    val path: String = s"releases/tzdata$version"
  }

  sealed abstract class Platform(val name: String) extends Product with Serializable
  final object Platform {
    final case object Jvm    extends Platform("jvm")
    final case object Js     extends Platform("js")
    final case object Native extends Platform("native")
  }

  object autoImport {

    /*
     * Settings
     */
    val zonesFilter                        = settingKey[String => Boolean]("Filter for zones")
    val dbVersion                          = settingKey[TZDBVersion]("Version of the tzdb")
    val tzdbCodeGen                        =
      taskKey[Seq[JFile]]("Generate scala.js compatible database of tzdb data")
    val includeTTBP: SettingKey[Boolean]   =
      settingKey[Boolean]("Include also a provider for threeten bp")
    val tzdbPlatform: SettingKey[Platform] = settingKey[Platform](
      "The generated code is platform specific. Specify what is the target platform."
    )
  }

  import autoImport._
  override def trigger            = noTrigger
  override lazy val buildSettings = Seq(
    zonesFilter := { case _ => true },
    dbVersion := LatestVersion,
    includeTTBP := false,
    tzdbPlatform := Platform.Js
  )
  override val projectSettings    =
    Seq(
      Compile / sourceGenerators += Def.task {
        tzdbCodeGen.value
      },
      tzdbCodeGen := {
        val cacheLocation                                  = streams.value.cacheDirectory / "sbt-tzdb"
        val log                                            = streams.value.log
        val cachedActionFunction: Set[JFile] => Set[JFile] = FileFunction.cached(
          cacheLocation,
          inStyle = FilesInfo.hash
        ) { _ =>
          tzdbCodeGenImpl(
            sourceManaged = (Compile / sourceManaged).value,
            resourcesManaged = (Compile / resourceManaged).value,
            zonesFilter = zonesFilter.value,
            dbVersion = dbVersion.value,
            includeTTBP = includeTTBP.value,
            tzdbPlatform = tzdbPlatform.value,
            log = log
          )
        }
        cachedActionFunction(Set((Compile / resourceManaged).value / "tzdb.tar.gz")).toSeq
      }
    )

  def tzdbCodeGenImpl(
    sourceManaged:    JFile,
    resourcesManaged: JFile,
    zonesFilter:      String => Boolean,
    dbVersion:        TZDBVersion,
    includeTTBP:      Boolean,
    tzdbPlatform:     Platform,
    log:              Logger
  ): Set[JFile] = {

    import cats._
    import cats.syntax.all._

    val tzdbData: JFile = resourcesManaged / "tzdb"
    val ttbp            = IOTasks.copyProvider(sourceManaged,
                                    tzdbPlatform.name,
                                    "TzdbZoneRulesProvider.scala",
                                    "org.threeten.bp.zone",
                                    false
    )
    val jt              =
      IOTasks.copyProvider(sourceManaged,
                           tzdbPlatform.name,
                           "TzdbZoneRulesProvider.scala",
                           "java.time.zone",
                           true
      )
    val providerCopy    = if (includeTTBP) List(ttbp, jt) else List(jt)
    (for {
      _ <- IOTasks.downloadTZDB(log, resourcesManaged, dbVersion)
      // Use it to detect if files have been already generated
      p <- IOTasks.providerFile(sourceManaged / tzdbPlatform.name,
                                "TzdbZoneRulesProvider.scala",
                                "java.time.zone"
           )
      e <- effect.IO(p.exists)
      j <- if (e) effect.IO(List(p)) else providerCopy.sequence
      f <- if (e) IOTasks.tzDataSources(sourceManaged, includeTTBP).map(_.map(_._3))
           else
             IOTasks.generateTZDataSources(sourceManaged,
                                           tzdbData,
                                           log,
                                           includeTTBP,
                                           tzdbPlatform,
                                           zonesFilter
             )
    } yield (j ::: f).toSet).unsafeRunSync
  }
}
