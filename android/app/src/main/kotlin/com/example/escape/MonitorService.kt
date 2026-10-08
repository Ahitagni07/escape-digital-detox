package com.example.escape

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import java.time.LocalTime
import kotlin.math.max

class MonitorService : Service() {
    companion object {
        private const val EVENING_START_HOUR = 18
        private const val NORMAL_REMINDER_INTERVAL_MS = 2 * 60 * 60 * 1000L
        private const val DEMO_REMINDER_INTERVAL_MS = 60 * 1000L
        private const val NORMAL_EMERGENCY_ACCESS_SECONDS = 10 * 60
        private const val DEMO_EMERGENCY_ACCESS_SECONDS = 60
    }

    private lateinit var preferences: EscapePreferences
    private lateinit var foregroundDetector: ForegroundAppDetector
    private lateinit var stepTracker: StepTracker
    private lateinit var recoveryTracker: RecoveryProgressTracker
    private lateinit var overlay: LockOverlayController
    private lateinit var notifications: NotificationHelper
    private lateinit var stats: StatsManager
    private lateinit var missions: MissionCoordinator

    private val handler = Handler(Looper.getMainLooper())
    private var tickerStarted = false
    private var lastBlockedPackage: String? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            try {
                tick()
            } catch (_: Throwable) {
            }
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()

        preferences = EscapePreferences(this)
        val permissions = PermissionHelper(this)
        foregroundDetector = ForegroundAppDetector(this)
        stepTracker = StepTracker(this, permissions)
        recoveryTracker = RecoveryProgressTracker(preferences, stepTracker)
        overlay = LockOverlayController(this)
        notifications = NotificationHelper(this)
        stats = StatsManager(preferences)
        missions = MissionCoordinator(this, preferences)

        notifications.createChannel()
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notifications.build("Earn your scroll with a screen-free mission")
        )

        stats.resetEmergencyCounterIfNeeded()
        preferences.putBoolean(EscapeKeys.RUNNING, true)
        preferences.putBoolean(EscapeKeys.STEP_SENSOR_AVAILABLE, stepTracker.available)
        preferences.putInt(EscapeKeys.SOCIAL_SECONDS, 0)

        stepTracker.start()
        missions.prepareAsync()

        if (isAccessActive()) {
            preferences.putBoolean(EscapeKeys.LOCKED, false)
            preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, false)
            notifications.cancelMissionReminder()
        } else {
            ensureMissionLock(sendReminder = true)
        }

        if (preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)) {
            recoveryTracker.resumeIfNeeded()
        }

        startTicker()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            EscapeKeys.ACTION_TEST_LOCK -> {
                preferences.putLong(EscapeKeys.ACCESS_UNTIL_MS, 0L)
                ensureMissionLock(sendReminder = true, forceNewMission = true)
            }

            EscapeKeys.ACTION_START_MISSION -> startCurrentMission()
            EscapeKeys.ACTION_EMERGENCY_UNLOCK -> emergencyUnlock()
        }

        startTicker()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        stepTracker.stop()
        missions.close()
        overlay.hide()
        notifications.cancelMissionReminder()
        preferences.clearRuntimeState()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startTicker() {
        if (tickerStarted) return
        tickerStarted = true
        handler.post(tickRunnable)
    }

    private fun tick() {
        val selected = preferences.selectedPackages()
        val foreground = foregroundDetector.currentPackage()

        preferences.putString(EscapeKeys.FOREGROUND_PACKAGE, foreground ?: "")
        preferences.putInt(EscapeKeys.SOCIAL_SECONDS, 0)

        if (isAccessActive()) {
            if (preferences.getBoolean(EscapeKeys.LOCKED, false)) {
                preferences.putBoolean(EscapeKeys.LOCKED, false)
            }
            overlay.hide()
            lastBlockedPackage = null
            notifications.update(
                "Social access earned — ${formatMinutes(accessRemainingSeconds())} remaining"
            )
            return
        }

        // The reward window has expired. Social apps now require a new mission.
        ensureMissionLock(sendReminder = false)

        var missionActive = preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)
        var lockMode = currentLockMode()

        // If a mission was waiting before 18:00 but has not started yet, switch it
        // automatically to an evening screen-free mission. The reverse also happens
        // the next morning for a mission that was never started overnight.
        val desiredMode = modeForCurrentTime()
        if (!missionActive && lockMode != desiredMode) {
            preferences.putString(EscapeKeys.LOCK_MODE, desiredMode)
            missions.prepareMission(desiredMode) {
                if (preferences.getBoolean(EscapeKeys.LOCKED, false) &&
                    !preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)) {
                    sendCurrentMissionReminder()
                }
            }
            scheduleNextReminder()
            lockMode = desiredMode
        }

        maybeSendMissionReminder()
        missionActive = preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)

        if (missionActive) {
            val missionTargetSeconds = preferences.getInt(
                EscapeKeys.WALK_SECONDS_TARGET,
                600
            )
            val minimumSteps = if (lockMode == EscapeKeys.LOCK_MODE_EVENING) {
                0
            } else {
                preferences.getInt(EscapeKeys.EFFECTIVE_MIN_STEPS, 600)
            }

            val progress = recoveryTracker.current(
                walkTargetSeconds = missionTargetSeconds,
                minimumSteps = minimumSteps,
                requireSteps = lockMode != EscapeKeys.LOCK_MODE_EVENING
            )

            if (progress.timeDone && progress.stepsDone) {
                completeMission(progress)
                return
            }
        }

        val protectedForeground = foreground != null && selected.contains(foreground)

        if (protectedForeground) {
            if (lastBlockedPackage != foreground) {
                stats.recordBlockedAttempt()
                lastBlockedPackage = foreground
            }
            showOverlay()
        } else {
            overlay.hide()
            lastBlockedPackage = null
        }
    }

    private fun ensureMissionLock(
        sendReminder: Boolean,
        forceNewMission: Boolean = false
    ) {
        if (
            preferences.getBoolean(EscapeKeys.LOCKED, false) &&
            !forceNewMission
        ) {
            return
        }

        preferences.putBoolean(EscapeKeys.LOCKED, true)
        preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, false)
        preferences.putLong(EscapeKeys.ACCESS_UNTIL_MS, 0L)
        preferences.putLong(EscapeKeys.LOCK_STARTED_MS, 0L)
        preferences.putInt(EscapeKeys.WALK_SECONDS, 0)
        preferences.putInt(EscapeKeys.WALK_STEPS, 0)
        recoveryTracker.finish()

        val mode = modeForCurrentTime()
        preferences.putString(EscapeKeys.LOCK_MODE, mode)

        missions.prepareMission(mode) {
            if (
                preferences.getBoolean(EscapeKeys.LOCKED, false) &&
                !preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)
            ) {
                sendCurrentMissionReminder()
            }
        }

        scheduleNextReminder()
        notifications.update("Mission ready — complete it to earn social access")

        if (sendReminder) {
            sendCurrentMissionReminder()
        }
    }

    private fun startCurrentMission() {
        if (!preferences.getBoolean(EscapeKeys.LOCKED, false)) return
        if (preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)) return

        preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, true)
        preferences.putLong(EscapeKeys.LOCK_STARTED_MS, System.currentTimeMillis())
        recoveryTracker.start()
        notifications.cancelMissionReminder()
        notifications.update("Mission in progress — put the phone away")

        Toast.makeText(
            this,
            if (currentLockMode() == EscapeKeys.LOCK_MODE_EVENING) {
                "🌙 Screen-free mission started."
            } else {
                "🌱 Mission started. Go earn your scroll!"
            },
            Toast.LENGTH_LONG
        ).show()
    }

    private fun completeMission(progress: RecoveryProgress) {
        val outdoor = currentLockMode() != EscapeKeys.LOCK_MODE_EVENING
        stats.recordCompletedMission(
            steps = progress.steps,
            missionSeconds = progress.walkSeconds,
            outdoor = outdoor
        )

        val accessSeconds = preferences.getInt(
            EscapeKeys.ACCESS_SECONDS_TARGET,
            45 * 60
        )

        grantAccess(accessSeconds, emergency = false)
    }

    private fun grantAccess(seconds: Int, emergency: Boolean) {
        val safeSeconds = max(60, seconds)
        val now = System.currentTimeMillis()

        preferences.putLong(
            EscapeKeys.ACCESS_UNTIL_MS,
            now + safeSeconds * 1000L
        )
        preferences.putBoolean(EscapeKeys.LOCKED, false)
        preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, false)
        preferences.putBoolean(EscapeKeys.MISSION_GENERATING, false)
        preferences.putLong(EscapeKeys.NEXT_MISSION_REMINDER_MS, 0L)

        recoveryTracker.finish()
        overlay.hide()
        notifications.cancelMissionReminder()

        val minutes = max(1, (safeSeconds + 59) / 60)
        notifications.showAccessGranted(minutes)
        notifications.update("Social access earned for $minutes minutes")

        Toast.makeText(
            this,
            if (emergency) {
                "Emergency access granted for $minutes minutes."
            } else {
                "✅ Mission complete — $minutes minutes of social access earned!"
            },
            Toast.LENGTH_LONG
        ).show()
    }

    private fun emergencyUnlock() {
        if (!preferences.getBoolean(EscapeKeys.LOCKED, false)) return

        stats.recordEmergencyUnlock()
        val demo = preferences.getBoolean(EscapeKeys.DEMO_MODE, false)
        grantAccess(
            if (demo) DEMO_EMERGENCY_ACCESS_SECONDS
            else NORMAL_EMERGENCY_ACCESS_SECONDS,
            emergency = true
        )
    }

    private fun showOverlay() {
        val lockMode = currentLockMode()
        val missionActive = preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)
        val targetSeconds = preferences.getInt(EscapeKeys.WALK_SECONDS_TARGET, 600)
        val minimumSteps = if (lockMode == EscapeKeys.LOCK_MODE_EVENING) {
            0
        } else {
            preferences.getInt(EscapeKeys.EFFECTIVE_MIN_STEPS, 600)
        }

        overlay.show(
            lockMode = lockMode,
            missionActive = missionActive,
            missionSeconds = preferences.getInt(EscapeKeys.WALK_SECONDS, 0),
            missionTargetSeconds = targetSeconds,
            steps = preferences.getInt(EscapeKeys.WALK_STEPS, 0),
            minSteps = minimumSteps,
            stepSensorAvailable = stepTracker.available,
            missionTitle = preferences.getString(
                EscapeKeys.MISSION_TITLE,
                "Earn Your Scroll"
            ),
            missionInstruction = preferences.getString(
                EscapeKeys.MISSION_INSTRUCTION,
                "Complete a screen-free mission to unlock social apps."
            ),
            missionSource = preferences.getString(EscapeKeys.MISSION_SOURCE, "fallback"),
            missionGenerating = preferences.getBoolean(
                EscapeKeys.MISSION_GENERATING,
                false
            )
        )
    }

    private fun maybeSendMissionReminder() {
        if (preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)) return

        val now = System.currentTimeMillis()
        val next = preferences.getLong(EscapeKeys.NEXT_MISSION_REMINDER_MS, 0L)
        if (next <= 0L || now >= next) {
            sendCurrentMissionReminder()
            scheduleNextReminder()
        }
    }

    private fun sendCurrentMissionReminder() {
        notifications.showMissionReminder(
            mission = missions.currentMission(),
            evening = currentLockMode() == EscapeKeys.LOCK_MODE_EVENING
        )
    }

    private fun scheduleNextReminder() {
        val demo = preferences.getBoolean(EscapeKeys.DEMO_MODE, false)
        val interval = if (demo) DEMO_REMINDER_INTERVAL_MS else NORMAL_REMINDER_INTERVAL_MS
        preferences.putLong(
            EscapeKeys.NEXT_MISSION_REMINDER_MS,
            System.currentTimeMillis() + interval
        )
    }

    private fun accessRemainingSeconds(): Int {
        val until = preferences.getLong(EscapeKeys.ACCESS_UNTIL_MS, 0L)
        if (until <= 0L) return 0
        return max(0L, (until - System.currentTimeMillis() + 999L) / 1000L).toInt()
    }

    private fun isAccessActive(): Boolean = accessRemainingSeconds() > 0

    private fun currentLockMode(): String =
        preferences.getString(EscapeKeys.LOCK_MODE, EscapeKeys.LOCK_MODE_WALK)

    private fun modeForCurrentTime(): String =
        if (LocalTime.now().hour >= EVENING_START_HOUR) {
            EscapeKeys.LOCK_MODE_EVENING
        } else {
            EscapeKeys.LOCK_MODE_WALK
        }

    private fun formatMinutes(seconds: Int): String {
        val minutes = max(1, (seconds + 59) / 60)
        return "$minutes min"
    }
}
