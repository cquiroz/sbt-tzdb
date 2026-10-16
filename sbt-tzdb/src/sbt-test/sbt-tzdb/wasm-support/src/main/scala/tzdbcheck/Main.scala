package tzdbcheck

import java.time.{ Instant, ZoneId }

// Runs the generated provider on Scala.js: the zone must resolve and give the right offsets.
object Main {
  def main(args: Array[String]): Unit = {
    val rules  = ZoneId.of("Europe/Warsaw").getRules
    val winter = rules.getOffset(Instant.parse("2026-01-15T12:00:00Z")).getTotalSeconds
    val summer = rules.getOffset(Instant.parse("2026-07-15T12:00:00Z")).getTotalSeconds
    if (winter != 3600 || summer != 7200)
      throw new IllegalStateException(s"Europe/Warsaw: winter $winter, summer $summer")
    println(s"Europe/Warsaw: winter $winter, summer $summer")
  }
}
