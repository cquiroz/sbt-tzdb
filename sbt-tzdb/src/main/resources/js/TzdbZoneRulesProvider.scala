package

import org.threeten.bp.LocalTime
import org.threeten.bp.LocalDateTime
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset
import org.threeten.bp.DayOfWeek
import org.threeten.bp.Month
import org.threeten.bp.DateTimeException

import scala.scalajs.js
import scala.scalajs.reflect.annotation.EnableReflectiveInstantiation

@EnableReflectiveInstantiation
final class TzdbZoneRulesProvider extends ZoneRulesProvider {
  import zonedb.threeten.tzdb._

  private val stdZonesMap =
    stdZones.asInstanceOf[js.Dictionary[js.Dynamic]].toMap
  private val fixedZonesMap = fixedZones.asInstanceOf[js.Dictionary[Int]].toMap

  override protected def provideZoneIds: java.util.Set[String] = {
    val zones = new java.util.HashSet[String]()
    val zonesSet = (stdZonesMap.keySet ++ fixedZonesMap.keySet ++ zoneLinks.keySet)
    // I'm not totallly sure the reason why but TTB removes these ZoneIds
    val zonesSetRemovals = Set("GMT0", "GMT+0", "GMT-0"/*, "UTC", "GMT"*/)
    (zonesSet -- zonesSetRemovals).foreach(zones.add(_))
    zones
  }

  private def toLocalTime(lt: Int): LocalTime =
    LocalTime.ofSecondOfDay(lt.toLong)

  private def toZoneOffsetTransition(
      zr: js.Array[Int]): ZoneOffsetTransition = {
    val jointDate = zr(0)
    // year is the first 4 digits
    val year = jointDate.toString.substring(0, 4).toInt
    val dayOfYear = jointDate.toString.substring(4, 7).toInt
    val transition =
      LocalDateTime.of(LocalDate.ofYearDay(year, dayOfYear), toLocalTime(zr(1)))
    ZoneOffsetTransition.of(transition,
                            ZoneOffset.ofTotalSeconds(zr(2)),
                            ZoneOffset.ofTotalSeconds(zr(3)))
  }

  private def toZoneOffsetTransitionRule(
      zor: js.Array[Int]): ZoneOffsetTransitionRule = {
    val time = toLocalTime(zor(3))
    val dayOfWeek = if (zor(2) >= 0) DayOfWeek.of(zor(2)) else null
    ZoneOffsetTransitionRule.of(
      Month.of(zor(0)),
      zor(1),
      dayOfWeek,
      time,
      zor(4) == 1,
      ZoneOffsetTransitionRule.TimeDefinition.values.apply(zor(5)),
      ZoneOffset.ofTotalSeconds(zor(6)),
      ZoneOffset.ofTotalSeconds(zor(7)),
      ZoneOffset.ofTotalSeconds(zor(8))
    )
  }

  private def toZoneRules(zr: scala.scalajs.js.Dynamic): ZoneRules = {
    // Get the values from a dynamic object to save code generated space
    val bso = ZoneOffset.ofTotalSeconds(zr.s.asInstanceOf[Int])
    val bwo = ZoneOffset.ofTotalSeconds(zr.w.asInstanceOf[Int])
    val standardTransitions =
      zr.t.asInstanceOf[js.Array[js.Array[Int]]].map(toZoneOffsetTransition)
    val transitionList =
      zr.l.asInstanceOf[js.Array[js.Array[Int]]].map(toZoneOffsetTransition)
    val lastRules =
      zr.r.asInstanceOf[js.Array[js.Array[Int]]].map(toZoneOffsetTransitionRule)
    val standardTransitionsJ = new java.util.ArrayList[ZoneOffsetTransition]
    standardTransitions.toList.foreach(standardTransitionsJ.add(_))
    val transitionListJ = new java.util.ArrayList[ZoneOffsetTransition]
    transitionList.toList.foreach(transitionListJ.add(_))
    val lastRulesJ = new java.util.ArrayList[ZoneOffsetTransitionRule]
    lastRules.toList.foreach(lastRulesJ.add(_))
    ZoneRules.of(bso,
                 bwo,
                 standardTransitionsJ,
                 transitionListJ,
                 lastRulesJ)
  }

  override protected def provideRules(regionId: String,
                                      forCaching: Boolean): ZoneRules = {
    val actualRegion = zoneLinks.getOrElse(regionId, regionId)
    stdZonesMap
      .get(actualRegion)
      .map(toZoneRules)
      .orElse(
        fixedZonesMap
          .get(actualRegion)
          .map(i => ZoneRules.of(ZoneOffset.ofTotalSeconds(i))))
      .getOrElse(
        throw new DateTimeException(s"TimeZone Region $actualRegion unknown"))
  }

  override protected def provideVersions(
      zoneId: String): java.util.NavigableMap[String, ZoneRules] = {
    val actualRegion = zoneLinks.getOrElse(zoneId, zoneId)
    stdZonesMap
      .get(actualRegion)
      .map(toZoneRules)
      .orElse(
        fixedZonesMap
          .get(actualRegion)
          .map(i => ZoneRules.of(ZoneOffset.ofTotalSeconds(i))))
      .map { z =>
        val r = new ZoneMap[String, ZoneRules]
        r.put(version, z)
        r
      }
      .getOrElse(
        throw new DateTimeException(s"TimeZone Region $actualRegion unknown"))
  }
}
