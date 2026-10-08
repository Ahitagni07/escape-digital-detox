package com.example.escape

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

class StatsManager(
    private val preferences: EscapePreferences
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun resetEmergencyCounterIfNeeded() {
        val today = dateFormat.format(Calendar.getInstance().time)
        val saved = preferences.getString(EscapeKeys.EMERGENCY_DATE, "")

        if (saved != today) {
            preferences.putString(EscapeKeys.EMERGENCY_DATE, today)
            preferences.putInt(EscapeKeys.EMERGENCY_UNLOCKS_TODAY, 0)
        }
    }

    fun recordBlockedAttempt() {
        increment(EscapeKeys.INTERRUPTIONS)
    }

    fun recordCompletedMission(
        steps: Int,
        missionSeconds: Int,
        outdoor: Boolean
    ) {
        increment(EscapeKeys.MISSIONS_COMPLETED)
        if (outdoor) increment(EscapeKeys.WALKS_COMPLETED)

        preferences.putInt(
            EscapeKeys.RECLAIMED_MINUTES,
            preferences.getInt(EscapeKeys.RECLAIMED_MINUTES, 0) +
                max(1, missionSeconds / 60)
        )

        preferences.putInt(
            EscapeKeys.STEPS_EARNED,
            preferences.getInt(EscapeKeys.STEPS_EARNED, 0) + steps
        )

        updateStreak()
    }

    fun recordEmergencyUnlock() {
        resetEmergencyCounterIfNeeded()
        increment(EscapeKeys.EMERGENCY_UNLOCKS_TODAY)
    }

    fun statusMap(): Map<String, Int> {
        resetEmergencyCounterIfNeeded()

        return mapOf(
            "interruptions" to preferences.getInt(EscapeKeys.INTERRUPTIONS, 0),
            "missionsCompleted" to preferences.getInt(EscapeKeys.MISSIONS_COMPLETED, 0),
            "walksCompleted" to preferences.getInt(EscapeKeys.WALKS_COMPLETED, 0),
            "reclaimedMinutes" to preferences.getInt(EscapeKeys.RECLAIMED_MINUTES, 0),
            "stepsEarned" to preferences.getInt(EscapeKeys.STEPS_EARNED, 0),
            "streakDays" to preferences.getInt(EscapeKeys.STREAK_DAYS, 0),
            "emergencyUnlocksToday" to preferences.getInt(
                EscapeKeys.EMERGENCY_UNLOCKS_TODAY,
                0
            )
        )
    }

    private fun increment(key: String) {
        preferences.putInt(key, preferences.getInt(key, 0) + 1)
    }

    private fun updateStreak() {
        val today = dateFormat.format(Calendar.getInstance().time)
        val saved = preferences.getString(EscapeKeys.LAST_COMPLETION_DATE, "")
        if (saved == today) return

        val current = preferences.getInt(EscapeKeys.STREAK_DAYS, 0)
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }

        val newStreak = if (saved.isNotBlank() && dateFormat.format(yesterday.time) == saved) {
            current + 1
        } else {
            1
        }

        preferences.putString(EscapeKeys.LAST_COMPLETION_DATE, today)
        preferences.putInt(EscapeKeys.STREAK_DAYS, newStreak)
    }
}
