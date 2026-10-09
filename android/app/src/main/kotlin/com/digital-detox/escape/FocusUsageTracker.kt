package com.example.escape

import kotlin.math.max

class FocusUsageTracker(
    private val preferences: EscapePreferences
) {
    private var socialStartMs = 0L

    fun update(
        foreground: String?,
        selectedPackages: Set<String>,
        targetSeconds: Int
    ): Boolean {
        val now = System.currentTimeMillis()

        if (
            foreground != null &&
            selectedPackages.contains(foreground)
        ) {
            if (socialStartMs == 0L) {
                socialStartMs = now
            }

            val elapsed =
                max(0L, now - socialStartMs)

            val seconds =
                (elapsed / 1000L).toInt()

            preferences.putInt(
                EscapeKeys.SOCIAL_SECONDS,
                seconds
            )

            return seconds >= targetSeconds
        }

        reset()
        return false
    }

    fun reset() {
        socialStartMs = 0L
        preferences.putInt(
            EscapeKeys.SOCIAL_SECONDS,
            0
        )
    }
}
