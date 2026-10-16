package

import org.threeten.bp.LocalTime
import org.threeten.bp.LocalDateTime
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset
import org.threeten.bp.DayOfWeek
import org.threeten.bp.Month
import org.threeten.bp.DateTimeException

import scala.scalajs.LinkingInfo
import scala.scalajs.js
import scala.scalajs.reflect.annotation.EnableReflectiveInstantiation

@EnableReflectiveInstantiation
final class TzdbZoneRulesProvider extends ZoneRulesProvider {

  // Two copies of the tzdb data exist. The JS one (TzdbZoneRulesJsData) is compact, but it is
  // built on `js.Dynamic`, so it needs a JavaScript host. The portable one
  // (TzdbZoneRulesPortableData) uses only Scala types. On a WebAssembly target without a
  // JavaScript host (the scala-wasm fork's `ModuleKind.MinimalWasmModule` = 4 and
  // `ModuleKind.WasmComponent` = 5) any reference to JS fails at link time, so it gets the
  // portable data. All other targets (JS, Wasm with a JS host) get the JS data, as before.
  // `linkTimeIf` removes the unused branch at link time, so only one copy is linked.
  // The values are literals because mainline Scala.js does not define these two
  // `LinkingInfo.ModuleKind` constants. Mainline uses only 1 to 3.
  private val data: TzdbZoneRulesData =
    LinkingInfo.linkTimeIf[TzdbZoneRulesData](
      LinkingInfo.isWebAssembly && (LinkingInfo.moduleKind == 4 || LinkingInfo.moduleKind == 5)
    ) {
      new TzdbZoneRulesPortableData
    } {
      new TzdbZoneRulesJsData
    }

  override protected def provideZoneIds: java.util.Set[String] = data.provideZoneIds

  override protected def provideRules(regionId: String, forCaching: Boolean): ZoneRules =
    data.provideRules(regionId)

  override protected def provideVersions(
      zoneId: String): java.util.NavigableMap[String, ZoneRules] =
    data.provideVersions(zoneId)
}

private sealed abstract class TzdbZoneRulesData {
  def provideZoneIds: java.util.Set[String]
  def provideRules(regionId: String): ZoneRules
  def provideVersions(zoneId: String): java.util.NavigableMap[String, ZoneRules]
}

private final class TzdbZoneRulesJsData extends TzdbZoneRulesData {
  import zonedb.threeten.tzdb.*

  private val stdZonesMap =
    stdZones.asInstanceOf[js.Dictionary[js.Dynamic]].toMap
  private val fixedZonesMap = fixedZones.asInstanceOf[js.Dictionary[Int]].toMap

  def provideZoneIds: java.util.Set[String] = {
    val zones = new java.util.HashSet[String]()
    val zonesSet = (stdZonesMap.keySet ++ fixedZonesMap.keySet ++ zoneLinks.keySet)
    zonesSet.foreach(zones.add(_))
    // I'm not totallly sure the reason why but TTB removes these ZoneIds
    // zones.remove("UTC")
    // zones.remove("GMT")
    zones.remove("GMT0")
    zones.remove("GMT+0")
    zones.remove("GMT-0")
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

  def provideRules(regionId: String): ZoneRules = {
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

  def provideVersions(zoneId: String): java.util.NavigableMap[String, ZoneRules] = {
    val actualRegion = zoneLinks.getOrElse(zoneId, zoneId)
    stdZonesMap
      .get(actualRegion)
      .map(toZoneRules)
      .orElse(
        fixedZonesMap
          .get(actualRegion)
          .map(i => ZoneRules.of(ZoneOffset.ofTotalSeconds(i))))
      .map { z =>
        val r = new java.util.TreeMap[String, ZoneRules]
        r.put(version, z)
        r
      }
      .getOrElse(
        throw new DateTimeException(s"TimeZone Region $actualRegion unknown"))
  }
}

private final class TzdbZoneRulesPortableData extends TzdbZoneRulesData {
  import zonedb.threeten.portable.tzdb.*

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

  def provideZoneIds: java.util.Set[String] = {
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

  def provideRules(regionId: String): ZoneRules = {
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

  def provideVersions(zoneId: String): java.util.NavigableMap[String, ZoneRules] = {
    val actualRegion = zoneLinks.getOrElse(zoneId, zoneId)
    stdZones
      .get(actualRegion)
      .map(toZoneRules)
      .orElse(
        fixedZones
          .get(actualRegion)
          .map(i => ZoneRules.of(ZoneOffset.ofTotalSeconds(i))))
      .map { z =>
        val r = new java.util.TreeMap[String, ZoneRules]
        r.put(version, z)
        r
      }
      .getOrElse(
        throw new DateTimeException(s"TimeZone Region $actualRegion unknown"))
  }
}
