package com.example.escape

import android.content.Context
import android.content.SharedPreferences

class EscapePreferences(
    context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(
            EscapeKeys.PREFS_NAME,
            Context.MODE_PRIVATE
        )

    fun selectedPackages(): Set<String> =
        prefs.getStringSet(
            EscapeKeys.SELECTED_PACKAGES,
            emptySet()
        ) ?: emptySet()

    fun saveSettings(
        packages: Set<String>,
        walkMinutes: Int,
        minSteps: Int,
        accessMinutes: Int,
        demoMode: Boolean,
        walkSecondsTarget: Int,
        effectiveMinSteps: Int,
        accessSecondsTarget: Int
    ) {
        prefs.edit()
            .putStringSet(EscapeKeys.SELECTED_PACKAGES, packages)
            .putInt(EscapeKeys.WALK_MINUTES, walkMinutes)
            .putInt(EscapeKeys.MIN_STEPS, minSteps)
            .putInt(EscapeKeys.ACCESS_MINUTES, accessMinutes)
            .putBoolean(EscapeKeys.DEMO_MODE, demoMode)
            .putInt(EscapeKeys.WALK_SECONDS_TARGET, walkSecondsTarget)
            .putInt(EscapeKeys.EFFECTIVE_MIN_STEPS, effectiveMinSteps)
            .putInt(EscapeKeys.ACCESS_SECONDS_TARGET, accessSecondsTarget)
            .apply()
    }

    fun settingsMap(): Map<String, Any> = mapOf(
        "packages" to selectedPackages().toList(),
        "walkMinutes" to getInt(EscapeKeys.WALK_MINUTES, 10),
        "minSteps" to getInt(EscapeKeys.MIN_STEPS, 600),
        "accessMinutes" to getInt(EscapeKeys.ACCESS_MINUTES, 30),
        "demoMode" to getBoolean(EscapeKeys.DEMO_MODE, false)
    )

    fun getInt(key: String, defaultValue: Int = 0): Int =
        prefs.getInt(key, defaultValue)

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long =
        prefs.getLong(key, defaultValue)

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getBoolean(
        key: String,
        defaultValue: Boolean = false
    ): Boolean = prefs.getBoolean(key, defaultValue)

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    fun getString(
        key: String,
        defaultValue: String = ""
    ): String = prefs.getString(key, defaultValue) ?: defaultValue

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun clearRuntimeState() {
        prefs.edit()
            .putBoolean(EscapeKeys.RUNNING, false)
            .putBoolean(EscapeKeys.LOCKED, false)
            .putBoolean(EscapeKeys.MISSION_ACTIVE, false)
            .putString(EscapeKeys.MISSION_ACTIVITY, "")
            .putInt(EscapeKeys.RIDE_METERS, 0)
            .putLong(EscapeKeys.LOCK_STARTED_MS, 0L)
            .putLong(EscapeKeys.ACCESS_UNTIL_MS, 0L)
            .putLong(EscapeKeys.NEXT_MISSION_REMINDER_MS, 0L)
            .putString(EscapeKeys.LOCK_MODE, EscapeKeys.LOCK_MODE_WALK)
            .putLong(EscapeKeys.EVENING_UNLOCK_AT_MS, 0L)
            .putInt(EscapeKeys.SOCIAL_SECONDS, 0)
            .putInt(EscapeKeys.WALK_SECONDS, 0)
            .putInt(EscapeKeys.WALK_STEPS, 0)
            .apply()
    }
}
