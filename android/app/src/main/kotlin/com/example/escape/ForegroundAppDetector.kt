package com.example.escape

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

class ForegroundAppDetector(
    context: Context
) {
    private val usageStats =
        context.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager

    private var lastForegroundPackage: String? = null

    fun currentPackage(): String? {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(now - 15_000L, now)
        val event = UsageEvents.Event()

        var newestTime = 0L
        var newestPackage: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            val foregroundEvent =
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            event.eventType ==
                            UsageEvents.Event.ACTIVITY_RESUMED
                    )

            if (foregroundEvent && event.timeStamp >= newestTime) {
                newestTime = event.timeStamp
                newestPackage = event.packageName
            }
        }

        if (newestPackage != null) {
            lastForegroundPackage = newestPackage
        }

        return lastForegroundPackage
    }
}
