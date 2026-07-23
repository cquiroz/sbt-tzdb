package

import org.threeten.bp.LocalTime
import org.threeten.bp.LocalDateTime
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset
import org.threeten.bp.DayOfWeek
import org.threeten.bp.Month
import org.threeten.bp.DateTimeException
import java.util.TreeMap

import org.portablescala.reflect.annotation.EnableReflectiveInstantiation

@EnableReflectiveInstantiation
final class TzdbZoneRulesProvider extends ZoneRulesProvider {
  import zonedb.threeten.tzdb._

  private def toLocalTime(secondOfDay: Int): LocalTime =
    LocalTime.ofSecondOfDay(secondOfDay.toLong)

  // Decodes a flat Int array of (year, dayOfYear, secondOfDay, offsetBefore, offsetAfter)
  // quintuples, the encoding produced by kuyfi's TZDBCodeGenerator.
  private def decodeTransitions(data: Array[Int]): java.util.List[ZoneOffsetTransition] = {
    val out = new java.util.ArrayList[ZoneOffsetTransition](data.length / 5)
    var i   = 0
    while (i < data.length) {
      val transition =
        LocalDateTime.of(LocalDate.ofYearDay(data(i), data(i + 1)), toLocalTime(data(i + 2)))
      out.add(
        ZoneOffsetTransition.of(transition,
                                ZoneOffset.ofTotalSeconds(data(i + 3)),
                                ZoneOffset.ofTotalSeconds(data(i + 4))
        )
      )
      i += 5
    }
    out
  }

  // Decodes a flat Int array of 9-int-wide transition rule tuples.
  private def decodeRules(data: Array[Int]): java.util.List[ZoneOffsetTransitionRule] = {
    val out = new java.util.ArrayList[ZoneOffsetTransitionRule](data.length / 9)
    var i   = 0
    while (i < data.length) {
      val dayOfWeek = if (data(i + 2) >= 0) DayOfWeek.of(data(i + 2)) else null
      out.add(
        ZoneOffsetTransitionRule.of(
          Month.of(data(i)),
          data(i + 1),
          dayOfWeek,
          toLocalTime(data(i + 3)),
          data(i + 4) == 1,
          ZoneOffsetTransitionRule.TimeDefinition.values.apply(data(i + 5)),
          ZoneOffset.ofTotalSeconds(data(i + 6)),
          ZoneOffset.ofTotalSeconds(data(i + 7)),
          ZoneOffset.ofTotalSeconds(data(i + 8))
        )
      )
      i += 9
    }
    out
  }

  private def toZoneRules(czr: (Int, Int, Array[Int], Array[Int], Array[Int])): ZoneRules = {
    val (bso, bwo, standardTransitions, transitionList, lastRules) = czr
    ZoneRules.of(
      ZoneOffset.ofTotalSeconds(bso),
      ZoneOffset.ofTotalSeconds(bwo),
      decodeTransitions(standardTransitions),
      decodeTransitions(transitionList),
      decodeRules(lastRules)
    )
  }

  override protected def provideZoneIds: java.util.Set[String] = {
    val zones = new java.util.HashSet[String]()
    val zonesSet = (stdZones.keySet ++ fixedZones.keySet ++ zoneLinks.keySet)
    zonesSet.foreach(zones.add(_))
    // I'm not totallly sure the reason why but TTB removes these ZoneIds
    // zones.remove("UTC")
    // zones.remove("GMT")
    zones.remove("GMT0")
    zones.remove("GMT+0")
    zones.remove("GMT-0")
    zones
  }

  override protected def provideRules(regionId: String,
                                      forCaching: Boolean): ZoneRules = {
    val actualRegion = zoneLinks.getOrElse(regionId, regionId)
    stdZones
      .get(actualRegion)
      .map(toZoneRules)
      .orElse(
        fixedZones
          .get(actualRegion)
          .map(i => ZoneRules.of(ZoneOffset.ofTotalSeconds(i))))
      .getOrElse(
        throw new DateTimeException(s"TimeZone Region $actualRegion unknown"))
  }

  override protected def provideVersions(
      zoneId: String): java.util.NavigableMap[String, ZoneRules] = {
    val actualRegion = zoneLinks.getOrElse(zoneId, zoneId)
    stdZones
      .get(actualRegion)
      .map(toZoneRules)
      .orElse(
        fixedZones
          .get(actualRegion)
          .map(i => ZoneRules.of(ZoneOffset.ofTotalSeconds(i))))
      .map { z =>
        val r = new TreeMap[String, ZoneRules]
        r.put(version, z)
        r
      }
      .getOrElse(
        throw new DateTimeException(s"TimeZone Region $actualRegion unknown"))
  }
}
