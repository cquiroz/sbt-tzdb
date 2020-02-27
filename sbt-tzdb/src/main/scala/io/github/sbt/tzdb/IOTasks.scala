package io.gitub.sbt.tzdb

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import better.files._
import better.files.Dsl._
import java.io.{
  InputStream,
  BufferedInputStream,
  BufferedOutputStream,
  FileOutputStream,
  FileInputStream,
  File => JFile
}
import java.nio.file.{ Files, StandardCopyOption }
import cats.implicits._
import cats.effect._
import sbt.Logger
import kuyfi.TZDBCodeGenerator
import kuyfi.TZDBCodeGenerator.OptimizedTreeGenerator._
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

object IOTasks {
  def downloadTZDB(
    log:          Logger,
    resourcesDir: JFile,
    tzdbVersion:  TzdbPlugin.TZDBVersion
  ): IO[Unit] = {
    val tzdbDir     = resourcesDir.toScala / "tzdb"
    val tzdbTarball = resourcesDir.toScala / "tzdb.tar.gz"
    if (!tzdbDir.exists) {
      val url =
        s"http://www.iana.org/time-zones/repository/${tzdbVersion.path}.tar.gz"
      for {
        _ <- cats.effect.IO(
          log.info(s"tzdb data missing. downloading ${tzdbVersion.id} version to $tzdbDir...")
        )
        _ <- cats.effect.IO(log.info(s"downloading from $url"))
        _ <- cats.effect.IO(log.info(s"to file $tzdbTarball"))
        _ <- cats.effect.IO(mkdirs(tzdbDir))
        _ <- IOTasks.download(url, tzdbTarball)
        _ <- IOTasks.gunzipTar(tzdbTarball.toJava, tzdbDir.toJava)
        _ <- cats.effect.IO(tzdbTarball.delete())
      } yield ()
    } else {
      cats.effect.IO(log.debug("tzdb files already available"))
    }
  }

  def tzDataSources(
    base:        JFile,
    includeTTBP: Boolean
  ): IO[List[(String, String, better.files.File)]] = IO {
    val dataPath = base.toScala / "tzdb"
    val pathsJT =
      ("zonedb.java", "java.time", dataPath / "tzdb_java.scala")
    val pathsTTB = ("zonedb.threeten", "org.threeten.bp", dataPath / "tzdb_threeten.scala")
    if (includeTTBP) List(pathsTTB, pathsJT) else List(pathsJT)
  }

  def generateTZDataSources(
    base:        JFile,
    data:        JFile,
    log:         Logger,
    includeTTBP: Boolean,
    zonesFilter: String => Boolean
  ): IO[List[better.files.File]] =
    for {
      paths <- tzDataSources(base, includeTTBP)
      _     <- IO(log.info(s"Generating tzdb from db at $data to $base"))
      _     <- IO(paths.foreach(t => mkdirs(t._3.parent)))
      f <- paths
        .map(p =>
          TZDBCodeGenerator
            .exportAll(data, p._3.toJava, p._1, zonesFilter)
        )
        .sequence
    } yield f

  def providerFile(base: JFile, name: String, packageDir: String): IO[File] = IO {
    val packagePath     = packageDir.replaceAll("\\.", "/")
    val destinationPath = base.toScala / packagePath
    val destinationFile = destinationPath / name
    destinationFile
  }

  def copyProvider(base: JFile, name: String, packageDir: String, isJava: Boolean): IO[File] = IO {
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
    val stream: InputStream = getClass.getResourceAsStream("/" + name)
    val destinationPath     = base.toScala / packagePath
    mkdirs(destinationPath)
    val destinationFile = destinationPath / name
    rm(destinationFile)
    File.usingTemporaryFile() { tempFile =>
      //do something
      Files.copy(stream, tempFile.path, StandardCopyOption.REPLACE_EXISTING)
      val replaced =
        tempFile.lines.map(if (isJava) replacementsJava else replacements)
      destinationFile.printLines(replaced)
    }
    destinationFile
  }

  def download(url: String, to: File) = IO {
    import gigahorse._, support.okhttp.Gigahorse
    import scala.concurrent._, duration._
    Gigahorse.withHttp(Gigahorse.config) { http =>
      val r = Gigahorse.url(url)
      val f = http.download(r, to.toJava)
      Await.result(f, 120.seconds)
    }
  }

  def gunzipTar(tarFile: JFile, dest: JFile): IO[String] = IO {
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
      val file = new JFile(dest, tarEntry.getName())
      if (tarEntry.isDirectory()) {
        file.mkdirs()
      } else {
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
