package

import org.threeten.bp.DateTimeException
import java.util.TreeMap

import org.portablescala.reflect.annotation.EnableReflectiveInstantiation

@EnableReflectiveInstantiation
final class TzdbZoneRulesProvider extends ZoneRulesProvider {
  import zonedb.threeten.tzdb._

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
      .orElse(
        fixedZones
          .get(actualRegion))
      .getOrElse(
        throw new DateTimeException(s"TimeZone Region $actualRegion unknown"))
  }

  override protected def provideVersions(
      zoneId: String): java.util.NavigableMap[String, ZoneRules] = {
    val actualRegion = zoneLinks.getOrElse(zoneId, zoneId)
    stdZones
      .get(actualRegion)
      .orElse(
        fixedZones
          .get(actualRegion))
      .map { z =>
        val r = new TreeMap[String, ZoneRules]
        r.put(version, z)
        r
      }
      .getOrElse(
        throw new DateTimeException(s"TimeZone Region $actualRegion unknown"))
  }
}
