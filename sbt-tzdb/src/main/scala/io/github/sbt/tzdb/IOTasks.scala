package io.github.sbt.tzdb

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io._
import java.nio.file.{ Files, StandardCopyOption }
import cats.syntax.all._
import scala.collection.JavaConverters._
import sbt._
import kuyfi.TZDBCodeGenerator
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.nio.charset.StandardCharsets

object IOTasks {
  def downloadTZDB(
    log:          Logger,
    resourcesDir: File,
    tzdbVersion:  TzdbPlugin.TZDBVersion
  ): cats.effect.IO[Unit] = {
    val tzdbDir     = resourcesDir / "tzdb"
    val tzdbTarball = resourcesDir / "tzdb.tar.gz"
    if (!tzdbDir.exists) {
      val url =
        s"http://www.iana.org/time-zones/repository/${tzdbVersion.path}.tar.gz"
      for {
        _ <- cats.effect.IO(
               log.info(s"tzdb data missing. downloading ${tzdbVersion.id} version to $tzdbDir...")
             )
        _ <- cats.effect.IO(log.info(s"downloading from $url"))
        _ <- cats.effect.IO(log.info(s"to file $tzdbTarball"))
        _ <- cats.effect.IO(tzdbDir.mkdirs())
        _ <- IOTasks.download(url, tzdbTarball)
        _ <- IOTasks.gunzipTar(tzdbTarball, tzdbDir)
        _ <- cats.effect.IO(tzdbTarball.delete())
      } yield ()
    } else
      cats.effect.IO(log.debug("tzdb files already available"))
  }

  def tzDataSources(
    base:        File,
    includeTTBP: Boolean
  ): cats.effect.IO[List[(String, String, File)]] =
    cats.effect.IO {
      val dataPath = base / "tzdb"
      val pathsJT  =
        ("zonedb.java", "java.time", dataPath / "tzdb_java.scala")
      val pathsTTB = ("zonedb.threeten", "org.threeten.bp", dataPath / "tzdb_threeten.scala")
      if (includeTTBP) List(pathsTTB, pathsJT) else List(pathsJT)
    }

  def generateTZDataSources(
    base:         File,
    data:         File,
    log:          Logger,
    includeTTBP:  Boolean,
    tzdbPlatform: TzdbPlugin.Platform,
    zonesFilter:  String => Boolean
  ): cats.effect.IO[List[File]] =
    for {
      paths <- tzDataSources(base, includeTTBP)
      _     <- cats.effect.IO(log.info(s"Generating tzdb from db at $data to $base"))
      _     <- cats.effect.IO(paths.foreach(t => t._3.getParentFile().mkdirs()))
      f     <- paths.map { p =>
             tzdbPlatform match {
               case TzdbPlugin.Platform.Js =>
                 import kuyfi.TZDBCodeGenerator.OptimizedTreeGenerator.*
                 TZDBCodeGenerator
                   .exportAll(data, p._3, p._1, p._2, zonesFilter)
               case _                      =>
                 import kuyfi.TZDBCodeGenerator.PureTreeGenerator.*
                 TZDBCodeGenerator
                   .exportAll(data, p._3, p._1, p._2, zonesFilter)
             }
           }.sequence
    } yield f

  def providerFile(base: File, name: String, packageDir: String): cats.effect.IO[File] =
    cats.effect.IO {
      val packagePath     = packageDir.replaceAll("\\.", "/")
      val destinationPath = base / packagePath
      val destinationFile = destinationPath / name
      destinationFile
    }

  def copyProvider(
    base:       File,
    sub:        String,
    name:       String,
    packageDir: String,
    isJava:     Boolean
  ): cats.effect.IO[File] =
    cats.effect.IO {
      def replacements(line: String): String =
        line
          .replaceAll("package", s"package $packageDir")

      def replacementsJava(line: String): String =
        line
          .replaceAll("package", s"package $packageDir")
          .replaceAll("package org.threeten$", "package java")
          .replaceAll("package object bp", "package object time")
          .replaceAll("""import org.threeten.bp(\..*)?(\.[A-Z_{][^\.]*)""", "import java.time$1$2")
          .replaceAll("import zonedb.threeten", "import zonedb.java")
          .replaceAll("private\\s*\\[bp\\]", "private[time]")

      val packagePath         = packageDir.replaceAll("\\.", "/")
      val stream: InputStream = getClass.getResourceAsStream(s"/$sub/$name")
      val destinationPath     = base / packagePath
      destinationPath.mkdirs()
      val destinationFile     = destinationPath / name
      destinationFile.delete()
      val tempFile            = File.createTempFile("tzdb", "tzdb")
      //do something
      Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
      val replaced            =
        Files
          .readAllLines(tempFile.toPath(), StandardCharsets.UTF_8)
          .asScala
          .map(if (isJava) replacementsJava else replacements)
      Files.write(destinationFile.toPath(), replaced.asJava, StandardCharsets.UTF_8)
      tempFile.delete()
      destinationFile
    }

  def download(url: String, to: File) =
    cats.effect.IO {
      import gigahorse.*
      import support.okhttp.Gigahorse

      import scala.concurrent.*
      import duration.*
      Gigahorse.withHttp(gigahorse.Config()) { http =>
        val r = Gigahorse.url(url)
        val f = http.download(r, to)
        Await.result(f, 120.seconds)
      }
    }

  def gunzipTar(tarFile: File, dest: File): cats.effect.IO[String] =
    cats.effect.IO {
      dest.mkdirs

      val tarIn = new TarArchiveInputStream(
        new GzipCompressorInputStream(
          new BufferedInputStream(
            new FileInputStream(
              tarFile
            )
          )
        )
      )

      var tarEntry = tarIn.getNextTarEntry()

      val topDir = tarEntry.getName().split("[/\\\\]")(0)

      while (tarEntry != null) {
        val file = new File(dest, tarEntry.getName())
        if (tarEntry.isDirectory())
          file.mkdirs()
        else {
          file.getParentFile.mkdirs()
          file.createNewFile
          val btoRead = new Array[Byte](1 * 1024)
          val bout    = new BufferedOutputStream(new FileOutputStream(file))
          var len     = 0
          len = tarIn.read(btoRead)
          while (len != -1) {
            bout.write(btoRead, 0, len)
            len = tarIn.read(btoRead)
          }
          bout.close()
        }
        tarEntry = tarIn.getNextTarEntry()
      }
      tarIn.close()

      topDir
    }

}
