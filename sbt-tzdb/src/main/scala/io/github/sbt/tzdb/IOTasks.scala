package io.github.sbt.tzdb

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io._
import java.net.{ HttpURLConnection, URL }
import java.nio.file.{ Files, StandardCopyOption }
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
  ): Unit = {
    val tzdbDir     = resourcesDir / "tzdb"
    val tzdbTarball = resourcesDir / "tzdb.tar.gz"
    if (!tzdbDir.exists) {
      val url =
        s"https://www.iana.org/time-zones/repository/${tzdbVersion.path}.tar.gz"
      log.info(s"tzdb data missing. downloading ${tzdbVersion.id} version to $tzdbDir...")
      log.info(s"downloading from $url")
      log.info(s"to file $tzdbTarball")
      tzdbDir.mkdirs()
      download(url, tzdbTarball)
      gunzipTar(tzdbTarball, tzdbDir)
      tzdbTarball.delete()
      ()
    } else
      log.debug("tzdb files already available")
  }

  def tzDataSources(
    base:        File,
    includeTTBP: Boolean
  ): List[(String, String, File)] = {
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
  ): List[File] = {
    val paths = tzDataSources(base, includeTTBP)
    log.info(s"Generating tzdb from db at $data to $base")
    paths.foreach(t => t._3.getParentFile().mkdirs())
    paths.map { p =>
      tzdbPlatform match {
        case TzdbPlugin.Platform.Js =>
          import kuyfi.TZDBCodeGenerator.OptimizedTreeGenerator.*
          TZDBCodeGenerator.exportAll(data, p._3, p._1, p._2, zonesFilter)
        case _                      =>
          import kuyfi.TZDBCodeGenerator.PureTreeGenerator.*
          TZDBCodeGenerator.exportAll(data, p._3, p._1, p._2, zonesFilter)
      }
    }
  }

  def providerFile(base: File, name: String, packageDir: String): File = {
    val packagePath     = packageDir.replaceAll("\\.", "/")
    val destinationPath = base / packagePath
    destinationPath / name
  }

  def copyProvider(
    base:       File,
    sub:        String,
    name:       String,
    packageDir: String,
    isJava:     Boolean
  ): File = {
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

  def download(url: String, to: File): File = {
    val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setConnectTimeout(30000)
    conn.setReadTimeout(120000)
    conn.setInstanceFollowRedirects(true)
    val in   = conn.getInputStream
    try Files.copy(in, to.toPath, StandardCopyOption.REPLACE_EXISTING)
    finally {
      in.close()
      conn.disconnect()
    }
    to
  }

  def gunzipTar(tarFile: File, dest: File): String = {
    dest.mkdirs

    val tarIn = new TarArchiveInputStream(
      new GzipCompressorInputStream(
        new BufferedInputStream(
          new FileInputStream(tarFile)
        )
      )
    )

    var tarEntry = tarIn.getNextEntry()

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
      tarEntry = tarIn.getNextEntry()
    }
    tarIn.close()

    topDir
  }

}
