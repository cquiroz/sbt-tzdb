package io.gitub.sbt.tzdb

import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io._
import cats._
import cats.implicits._
import cats.effect._
import sbt.Logger
import kuyfi.TZDBCodeGenerator
import kuyfi.TZDBCodeGenerator.OptimizedTreeGenerator._

object IOTasks {
  def generateTZDataSources(base: File, data: File, log: Logger): IO[List[better.files.File]] = {
    val dataPath = base.toPath.resolve("tzdb")
    val paths = List(("zonedb.threeten", "org.threeten.bp", dataPath.resolve(s"tzdb_threeten.scala")), ("zonedb.java", "java.time", dataPath.resolve(s"tzdb_java.scala")))
    // val paths = List(("zonedb.threeten", "org.threeten.bp", dataPath.resolve(s"tzdb_threeten.scala"))), ("zonedb.java", "java.time", dataPath.resolve(s"tzdb_java.scala"))))
    for {
      _ <- IO(log.info(s"Generating tzdb from db at $data to $base"))
      _ <- IO(paths.foreach(_._3.getParent.toFile.mkdirs()))
      f <- paths.map(p => TZDBCodeGenerator.exportAll(data, p._3.toFile, p._1, p._2)).sequence
    } yield f
  }

  def download(url: String, to: File) = IO {
    import gigahorse._, support.okhttp.Gigahorse
    import scala.concurrent._, duration._
    Gigahorse.withHttp(Gigahorse.config) { http =>
       val r = Gigahorse.url(url)
       val f = http.download(r, to)
       Await.result(f, 120.seconds)
     }
  }

  def gunzipTar(tarFile: File, dest: File): IO[String] = IO {
    dest.mkdir()

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
