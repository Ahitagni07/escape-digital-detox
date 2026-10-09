package com.example.escape

import android.app.Service
import android.os.Build
import android.content.pm.ServiceInfo
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import android.os.SystemClock
import android.os.Vibrator
import android.os.VibrationEffect
import kotlin.math.max
import java.util.Calendar
import java.util.TimeZone

class MonitorService : Service() {
    companion object {
        private const val REMINDER_INTERVAL_MS = 60 * 60 * 1000L
        private const val NORMAL_EMERGENCY_ACCESS_SECONDS = 10 * 60
        private const val DEMO_EMERGENCY_ACCESS_SECONDS = 60
        @Volatile var activeInstance: MonitorService? = null
            private set
    }

    private lateinit var preferences: EscapePreferences
    private lateinit var foregroundDetector: ForegroundAppDetector
    private lateinit var stepTracker: StepTracker
    private lateinit var recoveryTracker: RecoveryProgressTracker
    private lateinit var rideTracker: RideProgressTracker
    private lateinit var voiceGuide: OfflineVoiceGuide
    private lateinit var overlay: LockOverlayController
    private lateinit var notifications: NotificationHelper
    private lateinit var stats: StatsManager
    private lateinit var missions: MissionCoordinator
    private lateinit var daypart: DaypartClock

    private val handler = Handler(Looper.getMainLooper())
    private var tickerStarted = false
    private var lastBlockedPackage: String? = null
    private var lastAccessNotificationText: String? = null

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
        activeInstance = this

        preferences = EscapePreferences(this)
        val permissions = PermissionHelper(this)
        foregroundDetector = ForegroundAppDetector(this)
        stepTracker = StepTracker(this, permissions)
        recoveryTracker = RecoveryProgressTracker(preferences, stepTracker)
        rideTracker = RideProgressTracker(this, preferences)
        voiceGuide = OfflineVoiceGuide(this)
        overlay = LockOverlayController(this)
        notifications = NotificationHelper(this)
        stats = StatsManager(preferences)
        missions = MissionCoordinator(this, preferences)
        daypart = DaypartClock(this, preferences)
        daypart.nowMillis() // anchor the clock before showing any missions

        notifications.createChannel()
        val ongoing = notifications.build("Earn your scroll with a real-world quest")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NotificationHelper.NOTIFICATION_ID, ongoing,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, ongoing)
        }

        stats.resetEmergencyCounterIfNeeded()
        preferences.putBoolean(EscapeKeys.RUNNING, true)
        preferences.putBoolean(EscapeKeys.STEP_SENSOR_AVAILABLE, stepTracker.available)
        preferences.putInt(EscapeKeys.RIDE_TARGET_METERS,
            if (preferences.getBoolean(EscapeKeys.DEMO_MODE, false)) 150 else 1500)
        preferences.putInt(EscapeKeys.SOCIAL_SECONDS, 0)

        stepTracker.start()
        missions.prepareAsync()

        if (isAccessActive()) {
            preferences.putBoolean(EscapeKeys.LOCKED, false)
            preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, false)
            notifications.cancelMissionReminder()
        } else {
            // v7 saved evening timer missions had no proof tag/code.
            val staleEvening = preferences.getString(EscapeKeys.LOCK_MODE, "") ==
                EscapeKeys.LOCK_MODE_EVENING &&
                (preferences.getString(EscapeKeys.MISSION_PROOF_TAG, "") != "writing" ||
                 preferences.getString(EscapeKeys.MISSION_PROOF_CODE, "").isBlank())
            ensureMissionLock(sendReminder = true, forceNewMission = staleEvening)
        }

        // Migrate v9 cached missions to quests that require actual movement.
        if (preferences.getInt(EscapeKeys.QUEST_VERSION, 0) != 10) {
            for (key in listOf(EscapeKeys.DAY_MISSION_POOL_JSON,
                EscapeKeys.WEEKEND_MISSION_POOL_JSON, EscapeKeys.EVENING_MISSION_POOL_JSON)) {
                preferences.putString(key, "")
            }
            preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, false)
            preferences.putInt(EscapeKeys.QUEST_VERSION, 10)
            if (!isAccessActive()) ensureMissionLock(sendReminder = false, forceNewMission = true)
        } else if (preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)) {
            recoveryTracker.resumeIfNeeded()
            if (preferences.getString(EscapeKeys.MISSION_ACTIVITY, "") == "cycle") {
                // Ride GPS tracking needs a fresh user action after a service restart.
                preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, false)
            }
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
                preferences.putLong(EscapeKeys.ACCESS_UNTIL_ELAPSED, 0L)
                ensureMissionLock(sendReminder = true, forceNewMission = true)
            }

            EscapeKeys.ACTION_START_MISSION -> startCurrentMission("walk")
            EscapeKeys.ACTION_START_RIDE -> startCurrentMission("cycle")
            EscapeKeys.ACTION_PHOTO_APPROVED -> {
                if (preferences.getBoolean(EscapeKeys.RUNNING, false) &&
                    preferences.getBoolean(EscapeKeys.LOCKED, false) && isProofReady()) {
                    grantAccess(preferences.getInt(EscapeKeys.ACCESS_SECONDS_TARGET, 1800), false)
                }
            }
            EscapeKeys.ACTION_EMERGENCY_UNLOCK -> emergencyUnlock()
        }

        startTicker()
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickRunnable)
        if (activeInstance === this) activeInstance = null
        stepTracker.stop()
        rideTracker.stop()
        voiceGuide.close()
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
        maybeSendWeekendReminder()
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
            val text = "Social access earned — ${formatMinutes(accessRemainingSeconds())} remaining"
            if (text != lastAccessNotificationText) {
                notifications.update(text)
                lastAccessNotificationText = text
            }
            return
        }

        // The reward window has expired. Social apps now require a new mission.
        lastAccessNotificationText = null
        val oldEvening = preferences.getString(EscapeKeys.LOCK_MODE, "") ==
            EscapeKeys.LOCK_MODE_EVENING &&
            (preferences.getString(EscapeKeys.MISSION_PROOF_TAG, "") != "writing" ||
             preferences.getString(EscapeKeys.MISSION_PROOF_CODE, "").isBlank())
        ensureMissionLock(sendReminder = false, forceNewMission = oldEvening)

        var missionActive = preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)
        var lockMode = currentLockMode()

        // If a mission was waiting before 18:00 but has not started yet, switch it
        // automatically to an evening screen-free mission. The reverse also happens
        // the next morning for a mission that was never started overnight.
        val desiredMode = modeForCurrentTime()
        // A sunset during an unfinished outdoor quest moves the user to a safe indoor
        // alternative instead of making them continue cycling or walking in darkness.
        if (missionActive && desiredMode == EscapeKeys.LOCK_MODE_EVENING &&
            lockMode != EscapeKeys.LOCK_MODE_EVENING) {
            ensureMissionLock(sendReminder = true, forceNewMission = true)
            missionActive = false
            lockMode = EscapeKeys.LOCK_MODE_EVENING
        }
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
            val progress = recoveryTracker.current(
                preferences.getInt(EscapeKeys.WALK_SECONDS_TARGET, 600),
                preferences.getInt(EscapeKeys.EFFECTIVE_MIN_STEPS, 600),
                requireSteps = desiredMode != EscapeKeys.LOCK_MODE_EVENING
            )
            // Once per quest, nudge gently when movement is complete.
            if (isProofReady() && !preferences.getBoolean(EscapeKeys.MOVEMENT_NOTIFIED, false)) {
                preferences.putBoolean(EscapeKeys.MOVEMENT_NOTIFIED, true)
                notifications.update("Clue 1 complete — stop safely and take your mission photo")
                try {
                    val motor = getSystemService(VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= 26) motor.vibrate(
                        VibrationEffect.createOneShot(160L, VibrationEffect.DEFAULT_AMPLITUDE))
                    else {
                        @Suppress("DEPRECATION")
                        motor.vibrate(160L)
                    }
                } catch (_: Exception) {}
            }
        }

        // No automatic timer-based approval. A fresh camera photo must pass local proof.

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
        preferences.putString(EscapeKeys.MISSION_ACTIVITY, "")
        preferences.putInt(EscapeKeys.RIDE_METERS, 0)
        rideTracker.stop()
        restoreHealthForeground()
        preferences.putLong(EscapeKeys.ACCESS_UNTIL_MS, 0L)
        preferences.putLong(EscapeKeys.ACCESS_UNTIL_ELAPSED, 0L)
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

    /** Called only from an explicit user action in the visible Flutter app. */
    private fun startCurrentMission(requestedActivity: String) {
        if (!preferences.getBoolean(EscapeKeys.LOCKED, false)) return
        if (preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)) return
        val mode = currentLockMode()
        val activity = if (mode == EscapeKeys.LOCK_MODE_EVENING) "indoor" else requestedActivity
        if (activity == "cycle") {
            if (mode != EscapeKeys.LOCK_MODE_WEEKEND) return
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(NotificationHelper.NOTIFICATION_ID,
                        notifications.build("ESCAPE bicycle GPS quest in progress"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NotificationHelper.NOTIFICATION_ID,
                        notifications.build("ESCAPE bicycle GPS quest in progress"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                }
                preferences.putInt(EscapeKeys.RIDE_METERS, 0)
                if (!rideTracker.start()) {
                    restoreHealthForeground()
                    Toast.makeText(this, "Enable precise GPS to start cycling.", Toast.LENGTH_LONG).show()
                    return
                }
            } catch (_: SecurityException) {
                Toast.makeText(this, "Open ESCAPE and allow precise location before cycling.", Toast.LENGTH_LONG).show()
                return
            } catch (_: IllegalStateException) { return }
        }
        preferences.putString(EscapeKeys.MISSION_ACTIVITY, activity)
        preferences.putBoolean(EscapeKeys.MOVEMENT_NOTIFIED, false)
        recoveryTracker.start()
        preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, true)
        notifications.update("Quest running — return for a fresh nature photo")
    }

    private fun restoreHealthForeground() {
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                startForeground(NotificationHelper.NOTIFICATION_ID,
                    notifications.build("ESCAPE is protecting your focus"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } catch (_: Exception) { /* The health service remains available. */ }
        }
    }

    fun isProofReady(): Boolean {
        if (!preferences.getBoolean(EscapeKeys.RUNNING, false) ||
            !preferences.getBoolean(EscapeKeys.LOCKED, false) ||
            !preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)) return false
        val mode = currentLockMode()
        val activity = preferences.getString(EscapeKeys.MISSION_ACTIVITY, "")
        val progress = recoveryTracker.current(
            preferences.getInt(EscapeKeys.WALK_SECONDS_TARGET, 600),
            preferences.getInt(EscapeKeys.EFFECTIVE_MIN_STEPS, 600),
            requireSteps = mode != EscapeKeys.LOCK_MODE_EVENING)
        if (!progress.timeDone) return false
        return when (activity) {
            "indoor" -> mode == EscapeKeys.LOCK_MODE_EVENING
            "cycle" -> mode == EscapeKeys.LOCK_MODE_WEEKEND &&
                rideTracker.meters >= preferences.getInt(EscapeKeys.RIDE_TARGET_METERS, 1500)
            "walk" -> (mode != EscapeKeys.LOCK_MODE_EVENING) && progress.stepsDone
            else -> false
        }
    }

    fun readMissionAloud(): Boolean {
        val title = preferences.getString(EscapeKeys.MISSION_TITLE, "Nature quest")
        val instruction = preferences.getString(EscapeKeys.MISSION_INSTRUCTION, "Take a safe walk")
        return voiceGuide.speak("Escape quest. $title. $instruction. " +
            "Put the phone in your pocket and watch where you're going.")
    }

    fun reviewProof(mission: EscapeMission, evidence: ProofEvidence): Boolean? =
        missions.reviewExtractedEvidence(mission, evidence)

    private fun grantAccess(seconds: Int, emergency: Boolean) {
        val safeSeconds = max(60, seconds)
        val now = daypart.nowMillis()
        preferences.putLong(EscapeKeys.ACCESS_ISSUED_ELAPSED, SystemClock.elapsedRealtime())
        preferences.putInt(EscapeKeys.ACCESS_ISSUED_BOOT_COUNT, daypart.bootCount())
        preferences.putLong(EscapeKeys.ACCESS_UNTIL_ELAPSED,
            SystemClock.elapsedRealtime() + safeSeconds * 1000L)

        preferences.putLong(
            EscapeKeys.ACCESS_UNTIL_MS,
            now + safeSeconds * 1000L
        )
        preferences.putBoolean(EscapeKeys.LOCKED, false)
        preferences.putBoolean(EscapeKeys.MISSION_ACTIVE, false)
        preferences.putString(EscapeKeys.MISSION_ACTIVITY, "")
        preferences.putBoolean(EscapeKeys.MISSION_GENERATING, false)
        preferences.putLong(EscapeKeys.NEXT_MISSION_REMINDER_MS, 0L)

        recoveryTracker.finish()
        rideTracker.stop()
        restoreHealthForeground()
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
            ),
            missionProofCode = preferences.getString(EscapeKeys.MISSION_PROOF_CODE, "")
        )
    }

    private fun maybeSendMissionReminder() {
        if (preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)) return

        val now = SystemClock.elapsedRealtime()
        val next = preferences.getLong(EscapeKeys.NEXT_MISSION_REMINDER_MS, 0L)
        if (next > now + REMINDER_INTERVAL_MS) {
            scheduleNextReminder() // elapsedRealtime counter reset by reboot
            return
        }
        if (next <= 0L || now >= next) {
            // Different creative idea every hour, even if the previous was not done.
            missions.prepareMission(currentLockMode())
            sendCurrentMissionReminder()
            scheduleNextReminder()
        }
    }

    private fun sendCurrentMissionReminder() {
        val now = SystemClock.elapsedRealtime()
        val last = preferences.getLong(EscapeKeys.LAST_REMINDER_ELAPSED, -1L)
        // Prevent duplicate notifications when model generation finishes.
        if (last >= 0 && now >= last && now - last < 55 * 60 * 1000L) return
        preferences.putLong(EscapeKeys.LAST_REMINDER_ELAPSED, now)
        notifications.showMissionReminder(
            mission = missions.currentMission(),
            evening = currentLockMode() == EscapeKeys.LOCK_MODE_EVENING
        )
    }

    private fun scheduleNextReminder() {
        val interval = REMINDER_INTERVAL_MS
        preferences.putLong(
            EscapeKeys.NEXT_MISSION_REMINDER_MS,
            SystemClock.elapsedRealtime() + interval
        )
    }

    private fun accessRemainingSeconds(): Int {
        val monotonicUntil = preferences.getLong(EscapeKeys.ACCESS_UNTIL_ELAPSED, 0L)
        val now = SystemClock.elapsedRealtime()
        val issued = preferences.getLong(EscapeKeys.ACCESS_ISSUED_ELAPSED, -1L)
        val issuedBoot = preferences.getInt(EscapeKeys.ACCESS_ISSUED_BOOT_COUNT, -1)
        if (issued >= 0L && now >= issued &&
            (issuedBoot < 0 || issuedBoot == daypart.bootCount())) {
            return max(0L, (monotonicUntil - now + 999L) / 1000L).toInt()
        }
        // After reboot, use the anchored home clock as a best-effort fallback.
        val until = preferences.getLong(EscapeKeys.ACCESS_UNTIL_MS, 0L)
        if (until <= 0L) return 0
        return max(0L, (until - daypart.nowMillis() + 999L) / 1000L).toInt()
    }

    private fun isAccessActive(): Boolean = accessRemainingSeconds() > 0

    private fun currentLockMode(): String =
        preferences.getString(EscapeKeys.LOCK_MODE, EscapeKeys.LOCK_MODE_WALK)

    private fun modeForCurrentTime(): String {
        if (daypart.isEvening()) return EscapeKeys.LOCK_MODE_EVENING
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Amsterdam"))
        calendar.timeInMillis = daypart.nowMillis()
        return if (calendar.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY))
            EscapeKeys.LOCK_MODE_WEEKEND else EscapeKeys.LOCK_MODE_WALK
    }

    private fun maybeSendWeekendReminder() {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Amsterdam"))
        calendar.timeInMillis = daypart.nowMillis()
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY) return
        if (hour !in 9..18 || daypart.isEvening()) return
        val dateKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
        if (preferences.getString("weekend_reminder_last_day", "") == dateKey) return
        preferences.putString("weekend_reminder_last_day", dateKey)
        notifications.showWeekendReminder()
    }

    private fun formatMinutes(seconds: Int): String {
        val minutes = max(1, (seconds + 59) / 60)
        return "$minutes min"
    }
}
