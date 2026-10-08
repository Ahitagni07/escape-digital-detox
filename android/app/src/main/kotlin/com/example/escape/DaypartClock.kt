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

    fun isEvening(): Boolean = Calendar.getInstance(homeZone).apply {
        timeInMillis = nowMillis()
    }.get(Calendar.HOUR_OF_DAY) >= 18
}
