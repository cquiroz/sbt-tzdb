package io.gitub.sbt.tzdb

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import better.files._
import better.files.Dsl._
import java.io.{InputStream, BufferedInputStream, BufferedOutputStream, FileOutputStream, FileInputStream, File => JFile}
import java.nio.file.{Files, StandardCopyOption}
import cats._
import cats.implicits._
import cats.effect._
import sbt.Logger
import kuyfi.TZDBCodeGenerator
import kuyfi.TZDBCodeGenerator.OptimizedTreeGenerator._

object IOTasks {
  def generateTZDataSources(base: JFile, data: JFile, log: Logger, zonesFilter: String => Boolean): IO[List[better.files.File]] = {
    val dataPath = base.toPath.resolve("tzdb")
    val paths = List(("zonedb.threeten", "org.threeten.bp", dataPath.resolve(s"tzdb_threeten.scala")), ("zonedb.java", "java.time", dataPath.resolve(s"tzdb_java.scala")))
    for {
      _ <- IO(log.info(s"Generating tzdb from db at $data to $base"))
      _ <- IO(paths.foreach(_._3.getParent.toFile.mkdirs()))
      f <- paths.map(p => TZDBCodeGenerator.exportAll(data, p._3.toFile, p._1, p._2, zonesFilter)).sequence
    } yield f
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

    val pathSeparator = JFile.separator
    val packagePath = packageDir.replaceAll("\\.", pathSeparator)
    val stream: InputStream = getClass.getResourceAsStream("/" + name)
    val destinationPath = base.toScala/packagePath
    mkdirs(destinationPath)
    val destinationFile = destinationPath/name
    rm(destinationFile)
    File.usingTemporaryFile() {tempFile =>
      //do something
      Files.copy(stream, tempFile.path, StandardCopyOption.REPLACE_EXISTING)
      val replaced = tempFile.lines.map(if (isJava) replacementsJava else replacements)
      println("replace")
      replaced.foreach(println)
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
        var btoRead = new Array[Byte](1 * 1024)
        var bout = new BufferedOutputStream(new FileOutputStream(file))
        var len = 0
        len = tarIn.read(btoRead)
        while(len != -1) {
          bout.write(btoRead,0,len)
          len = tarIn.read(btoRead)
        }
        bout.close()
        btoRead = null
      }
      tarEntry = tarIn.getNextTarEntry()
    }
    tarIn.close()

    topDir
  }

}
