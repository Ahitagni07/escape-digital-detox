package com.example.escape

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.util.Calendar
import java.util.TimeZone

/**
 * Home-zone clock anchored to elapsedRealtime for this boot.
 * User timezone changes or manually moving the wall clock backwards while
 * ESCAPE is running will not flip evening mode back to daytime.
 * No offline app can provide a tamper-proof trusted time after a reboot.
 */
class DaypartClock(private val context: Context, private val prefs: EscapePreferences) {
    private val homeZone = TimeZone.getTimeZone("Europe/Amsterdam")

    fun bootCount(): Int = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (_: Throwable) { -1 }

    fun nowMillis(): Long {
        val elapsed = SystemClock.elapsedRealtime()
        val boot = bootCount()
        val savedBoot = prefs.getInt(EscapeKeys.CLOCK_BOOT_COUNT, -2)
        val wall = prefs.getLong(EscapeKeys.CLOCK_ANCHOR_WALL, 0L)
        val previousElapsed = prefs.getLong(EscapeKeys.CLOCK_ANCHOR_ELAPSED, -1L)
        if (wall <= 0L || previousElapsed < 0L || elapsed < previousElapsed ||
            (boot >= 0 && boot != savedBoot)) {
            val current = System.currentTimeMillis()
            prefs.putLong(EscapeKeys.CLOCK_ANCHOR_WALL, current)
            prefs.putLong(EscapeKeys.CLOCK_ANCHOR_ELAPSED, elapsed)
            prefs.putInt(EscapeKeys.CLOCK_BOOT_COUNT, boot)
            return current
        }
        return wall + (elapsed - previousElapsed)
    }

    /**
     * The evening choice is a fixed 18:00–06:59 Amsterdam time policy.
     * It is independent from sunset and never changes a started quest.
     * DaypartClock.nowMillis() is anchored to elapsedRealtime on this boot.
     */
    fun indoorChoiceAvailable(): Boolean {
        val hour = Calendar.getInstance(homeZone).apply { timeInMillis = nowMillis() }
            .get(Calendar.HOUR_OF_DAY)
        return hour >= 18 || hour < 7
    }

    /** Uses previously cached coordinates; never fetches GPS without user permission. */
    fun isEvening(): Boolean {
        val now = nowMillis()
        val cached = context.getSharedPreferences("escape_weekend_places", Context.MODE_PRIVATE)
        val lat = cached.getString("lat", "")?.toDoubleOrNull()
        val lon = cached.getString("lon", "")?.toDoubleOrNull()
        if (lat != null && lon != null) {
            val solar = SunsetPlanner.forDate(now, lat, lon)
            if (solar != null) {
                // No unsafe missions at night / dawn; 30 min twilight buffer.
                return now < solar.sunriseUtcMillis + 30*60_000L ||
                    now >= solar.sunsetUtcMillis - 30*60_000L
            }
        }
        // Conservative fallback before user has looked up nearby places.
        val hour = Calendar.getInstance(homeZone).apply { timeInMillis = now }
            .get(Calendar.HOUR_OF_DAY)
        return hour >= 18 || hour < 7
    }
}
