package com.example.escape

import kotlin.math.max
import android.os.SystemClock

data class RecoveryProgress(
    val walkSeconds: Int,
    val steps: Int,
    val timeDone: Boolean,
    val stepsDone: Boolean
)

class RecoveryProgressTracker(
    private val preferences: EscapePreferences,
    private val stepTracker: StepTracker
) {
    fun start() {
        preferences.putLong(EscapeKeys.LOCK_STARTED_MS, System.currentTimeMillis())
        preferences.putInt(EscapeKeys.WALK_SECONDS, 0)
        preferences.putInt(EscapeKeys.WALK_STEPS, 0)
        preferences.putLong(EscapeKeys.MISSION_START_ELAPSED, SystemClock.elapsedRealtime())
        stepTracker.startMission()
    }

    fun resumeIfNeeded() {
        var started = preferences.getLong(EscapeKeys.LOCK_STARTED_MS, 0L)
        if (started <= 0L) {
            started = System.currentTimeMillis()
            preferences.putLong(EscapeKeys.LOCK_STARTED_MS, started)
        }
        val old = preferences.getLong(EscapeKeys.MISSION_START_ELAPSED, 0L)
        if (old <= 0 || old > SystemClock.elapsedRealtime()) {
            preferences.putLong(EscapeKeys.MISSION_START_ELAPSED, SystemClock.elapsedRealtime())
        }
        stepTracker.startMission()
    }

    fun current(
        walkTargetSeconds: Int,
        minimumSteps: Int,
        requireSteps: Boolean = true
    ): RecoveryProgress {
        val now = System.currentTimeMillis()
        var started = preferences.getLong(EscapeKeys.LOCK_STARTED_MS, 0L)

        if (started <= 0L) {
            started = now
            preferences.putLong(EscapeKeys.LOCK_STARTED_MS, started)
        }

        // Elapsed realtime is unaffected by edits to date/time or timezone.
        val monotonicNow = SystemClock.elapsedRealtime()
        val elapsedStart = preferences.getLong(EscapeKeys.MISSION_START_ELAPSED, 0L)
        val safeStart = if (elapsedStart <= 0 || elapsedStart > monotonicNow) {
            preferences.putLong(EscapeKeys.MISSION_START_ELAPSED, monotonicNow)
            monotonicNow
        } else elapsedStart
        val seconds = (max(0L, monotonicNow - safeStart) / 1000L).toInt()
        val steps = stepTracker.stepsSinceMissionStart()

        preferences.putInt(EscapeKeys.WALK_SECONDS, seconds)
        preferences.putInt(EscapeKeys.WALK_STEPS, steps)

        val timeDone = seconds >= walkTargetSeconds
        val stepsDone = !requireSteps || !stepTracker.available ||
            (stepTracker.hasBaseline() && steps >= minimumSteps)

        return RecoveryProgress(
            walkSeconds = seconds,
            steps = steps,
            timeDone = timeDone,
            stepsDone = stepsDone
        )
    }

    fun finish() {
        preferences.putLong(EscapeKeys.LOCK_STARTED_MS, 0L)
        preferences.putLong(EscapeKeys.MISSION_START_ELAPSED, 0L)
        preferences.putInt(EscapeKeys.WALK_SECONDS, 0)
        preferences.putInt(EscapeKeys.WALK_STEPS, 0)
        stepTracker.finishMission()
    }
}
