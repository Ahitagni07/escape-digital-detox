package com.example.escape

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.*

/** Approximate NOAA solar geometry. No internet needed; coordinates are locally cached. */
object SunsetPlanner {
    private const val ZENITH = 90.833 // refraction + solar disk
    data class SolarTimes(val sunriseUtcMillis: Long, val sunsetUtcMillis: Long)

    fun forDate(now: Long, lat: Double, lon: Double): SolarTimes? {
        if (!lat.isFinite() || !lon.isFinite() || abs(lat) > 66.0 || abs(lon) > 180.0) return null
        val local = Calendar.getInstance(TimeZone.getTimeZone("Europe/Amsterdam")).apply { timeInMillis = now }
        val n = local.get(Calendar.DAY_OF_YEAR)
        val gamma = 2 * PI / 365.0 * (n - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868*cos(gamma) - 0.032077*sin(gamma) -
            0.014615*cos(2*gamma) - 0.040849*sin(2*gamma))
        val decl = 0.006918 - 0.399912*cos(gamma) + 0.070257*sin(gamma) -
            0.006758*cos(2*gamma) + 0.000907*sin(2*gamma) -
            0.002697*cos(3*gamma) + 0.00148*sin(3*gamma)
        val phi = Math.toRadians(lat)
        val cosHA = (cos(Math.toRadians(ZENITH)) / (cos(phi)*cos(decl))) - tan(phi)*tan(decl)
        if (cosHA !in -1.0..1.0) return null // polar region fallback
        val offset = 4.0 * Math.toDegrees(acos(cosHA))
        val solarNoonUtcMinutes = 720 - 4*lon - eqTime
        val midnightUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        return SolarTimes(
            midnightUtc + ((solarNoonUtcMinutes - offset)*60_000).toLong(),
            midnightUtc + ((solarNoonUtcMinutes + offset)*60_000).toLong()
        )
    }
}
