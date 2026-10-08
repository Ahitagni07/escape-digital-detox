package com.example.escape

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import kotlin.math.max

class EscapeMethodChannelHandler(
    private val activity: FlutterActivity
) {
    companion object {
        private const val CHANNEL_NAME = "escape/native"
        private const val REQUEST_CODE = 4107
        private const val MODEL_PICK_REQUEST_CODE = 4108
        private const val PHOTO_PICK_REQUEST_CODE = 4109
    }

    private val permissions = PermissionHelper(activity)
    private val appQuery = AppQueryHelper(activity)
    private val preferences = EscapePreferences(activity)
    private val stats = StatsManager(preferences)
    private val modelManager = GemmaModelManager(activity, preferences)
    private val autoDownloader = GemmaAutoDownloader(activity, preferences, modelManager)

    private var pendingModelImportResult: MethodChannel.Result? = null
    private var pendingPhotoResult: MethodChannel.Result? = null

    fun register(binaryMessenger: BinaryMessenger) {
        MethodChannel(binaryMessenger, CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getPermissionStatus" -> result.success(
                        mapOf(
                            "usage" to permissions.hasUsageAccess(),
                            "overlay" to permissions.hasOverlayPermission(),
                            "activity" to permissions.hasActivityRecognitionPermission()
                        )
                    )

                    "openUsageSettings" -> {
                        activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        result.success(true)
                    }

                    "openOverlaySettings" -> {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${activity.packageName}")
                            )
                        )
                        result.success(true)
                    }

                    "requestRuntimePermissions" -> {
                        requestRuntimePermissions()
                        result.success(true)
                    }

                    "getInstalledSupportedApps" ->
                        result.success(appQuery.installedSupportedApps())

                    "getSettings" -> result.success(preferences.settingsMap())

                    "saveSettings" -> {
                        val packages =
                            call.argument<List<String>>("packages")?.toSet() ?: emptySet()

                        preferences.saveSettings(
                            packages = packages,
                            walkMinutes = call.argument<Int>("walkMinutes") ?: 10,
                            minSteps = call.argument<Int>("minSteps") ?: 600,
                            accessMinutes = call.argument<Int>("accessMinutes") ?: 45,
                            demoMode = call.argument<Boolean>("demoMode") ?: false,
                            walkSecondsTarget =
                                call.argument<Int>("walkSecondsTarget") ?: 600,
                            effectiveMinSteps =
                                call.argument<Int>("effectiveMinSteps") ?: 600,
                            accessSecondsTarget =
                                call.argument<Int>("accessSecondsTarget") ?: 2700
                        )
                        result.success(true)
                    }

                    "startMonitor" -> {
                        if (
                            !permissions.hasUsageAccess() ||
                            !permissions.hasOverlayPermission() ||
                            !permissions.hasActivityRecognitionPermission()
                        ) {
                            result.success(false)
                        } else {
                            startMonitorService()
                            result.success(true)
                        }
                    }

                    "stopMonitor" -> {
                        if (preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false)) {
                            result.error(
                                "mission_active",
                                "Finish or emergency-unlock the active mission before pausing ESCAPE.",
                                null
                            )
                        } else {
                            activity.stopService(Intent(activity, MonitorService::class.java))
                            preferences.clearRuntimeState()
                            result.success(true)
                        }
                    }

                    "triggerTestLock" -> {
                        sendMonitorAction(EscapeKeys.ACTION_TEST_LOCK)
                        result.success(true)
                    }

                    "startMission" -> {
                        sendMonitorAction(EscapeKeys.ACTION_START_MISSION)
                        result.success(true)
                    }

                    "submitMissionPhoto" -> {
                        if (!preferences.getBoolean(EscapeKeys.RUNNING, false) ||
                            !preferences.getBoolean(EscapeKeys.LOCKED, false) ||
                            preferences.getString(EscapeKeys.LOCK_MODE, "walk") != EscapeKeys.LOCK_MODE_WALK) {
                            result.success(mapOf("approved" to false,
                                "message" to "No outdoor mission is waiting."))
                        } else if (pendingPhotoResult != null) {
                            result.error("photo_busy", "Finish choosing the previous photo first.", null)
                        } else {
                            pendingPhotoResult = result
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            activity.startActivityForResult(
                                Intent.createChooser(intent, "Select a nature photo"),
                                PHOTO_PICK_REQUEST_CODE
                            )
                        }
                    }

                    "emergencyUnlock" -> {
                        sendMonitorAction(EscapeKeys.ACTION_EMERGENCY_UNLOCK)
                        result.success(true)
                    }

                    "getStatus" -> result.success(statusMap())

                    // --- Local Gemma / LiteRT-LM ---
                    "getAiStatus" -> result.success(aiStatusMap())
                    "ensureGemmaModel" -> result.success(autoDownloader.ensureStarted())
                    "retryGemmaModelDownload" -> result.success(autoDownloader.retry())

                    "openGemmaModelPage" -> {
                        activity.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(GemmaModelManager.MODEL_PAGE_URL))
                        )
                        result.success(true)
                    }

                    "importGemmaModel" -> {
                        if (pendingModelImportResult != null) {
                            result.error("picker_busy", "A model picker is already open.", null)
                        } else {
                            pendingModelImportResult = result
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

                                // Android scoped storage does not let ESCAPE silently
                                // read a browser-created file from public Downloads.
                                // Open the system picker directly at Downloads instead.
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    putExtra(
                                        DocumentsContract.EXTRA_INITIAL_URI,
                                        Uri.parse(
                                            "content://com.android.providers.downloads.documents/root/downloads"
                                        )
                                    )
                                }
                            }
                            activity.startActivityForResult(intent, MODEL_PICK_REQUEST_CODE)
                        }
                    }

                    "removeGemmaModel" -> {
                        if (preferences.getBoolean(EscapeKeys.RUNNING, false)) {
                            result.error(
                                "escape_running",
                                "Pause ESCAPE before removing the AI model.",
                                null
                            )
                        } else {
                            autoDownloader.cancelAndDelete()
                            result.success(modelManager.removeModel())
                        }
                    }

                    "testGemmaMission" -> {
                        if (preferences.getBoolean(EscapeKeys.RUNNING, false)) {
                            result.error(
                                "escape_running",
                                "Pause ESCAPE before running the AI test.",
                                null
                            )
                        } else if (!modelManager.isInstalled()) {
                            result.error(
                                "model_missing",
                                "Install the Gemma .litertlm model first.",
                                null
                            )
                        } else {
                            testGemmaMission(result)
                        }
                    }

                    else -> result.notImplemented()
                }
            }
    }

    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (requestCode == PHOTO_PICK_REQUEST_CODE) {
            val pending = pendingPhotoResult ?: return true
            pendingPhotoResult = null
            val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
            if (uri == null) {
                pending.success(mapOf("approved" to false, "message" to "Photo selection cancelled."))
                return true
            }
            val tag = preferences.getString(EscapeKeys.MISSION_PROOF_TAG, "nature")
            PhotoProofVerifier(activity).verify(uri, tag) { evaluation ->
                activity.runOnUiThread {
                    val stillOutdoor = preferences.getBoolean(EscapeKeys.LOCKED, false) &&
                        preferences.getBoolean(EscapeKeys.RUNNING, false) &&
                        preferences.getString(EscapeKeys.LOCK_MODE, "walk") == EscapeKeys.LOCK_MODE_WALK &&
                        preferences.getString(EscapeKeys.MISSION_PROOF_TAG, "nature") == tag
                    if (evaluation["approved"] == true && stillOutdoor) {
                        sendMonitorAction(EscapeKeys.ACTION_PHOTO_APPROVED)
                        pending.success(evaluation)
                    } else {
                        pending.success(if (stillOutdoor) evaluation else mapOf(
                            "approved" to false,
                            "message" to "Mission changed while checking photo. Please try the new mission."
                        ))
                    }
                }
            }
            return true
        }
        if (requestCode != MODEL_PICK_REQUEST_CODE) return false

        val pending = pendingModelImportResult ?: return true
        pendingModelImportResult = null

        if (resultCode != Activity.RESULT_OK) {
            pending.success(
                mapOf(
                    "installed" to modelManager.isInstalled(),
                    "cancelled" to true
                )
            )
            return true
        }

        val uri = data?.data
        if (uri == null) {
            pending.error("no_file", "No model file was selected.", null)
            return true
        }

        try {
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {
        }

        Thread({
            try {
                modelManager.importFromUri(uri)
                // If an older build left a failed/pending DownloadManager job,
                // remove it now so only the installed state remains visible.
                autoDownloader.cancelAndDelete()
                val status = aiStatusMap()
                activity.runOnUiThread { pending.success(status) }
            } catch (t: Throwable) {
                activity.runOnUiThread {
                    pending.error(
                        "model_import_failed",
                        t.message ?: "Could not import the model.",
                        null
                    )
                }
            }
        }, "escape-model-import").start()

        return true
    }

    private fun testGemmaMission(result: MethodChannel.Result) {
        Thread({
            val engineManager = GemmaEngineManager(activity, modelManager)
            try {
                val generator = GemmaMissionGenerator(preferences, engineManager)
                val mission = generator.generateMissionBatch(
                    lockMode = EscapeKeys.LOCK_MODE_WALK,
                    count = 1,
                    missionMinutes = 10,
                    minimumSteps = 600
                ).first()

                preferences.putString(EscapeKeys.AI_LAST_ERROR, "")
                activity.runOnUiThread {
                    result.success(
                        mapOf(
                            "title" to mission.title,
                            "instruction" to mission.instruction,
                            "source" to mission.source
                        )
                    )
                }
            } catch (t: Throwable) {
                val message = t.message ?: t.javaClass.simpleName
                preferences.putString(EscapeKeys.AI_LAST_ERROR, message)
                activity.runOnUiThread {
                    result.error("gemma_test_failed", message, null)
                }
            } finally {
                engineManager.close()
            }
        }, "escape-gemma-test").start()
    }

    private fun requestRuntimePermissions() {
        val requested = mutableListOf<String>()

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            activity.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requested.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requested.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (requested.isNotEmpty()) {
            activity.requestPermissions(requested.toTypedArray(), REQUEST_CODE)
        }
    }

    private fun startMonitorService() {
        val intent = Intent(activity, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intent)
        } else {
            activity.startService(intent)
        }
    }

    private fun sendMonitorAction(action: String) {
        val intent = Intent(activity, MonitorService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intent)
        } else {
            activity.startService(intent)
        }
    }

    private fun aiStatusMap(): Map<String, Any> {
        val result = modelManager.statusMap().toMutableMap()
        result.putAll(autoDownloader.refreshStatus(startIfMissing = false))
        result["engineReady"] = preferences.getBoolean(EscapeKeys.AI_ENGINE_READY, false)
        result["enginePreparing"] = preferences.getBoolean(
            EscapeKeys.AI_ENGINE_PREPARING,
            false
        )
        result["escapeRunning"] = preferences.getBoolean(EscapeKeys.RUNNING, false)
        return result
    }

    private fun statusMap(): Map<String, Any> {
        val downloadStatus = autoDownloader.refreshStatus(startIfMissing = false)

        val accessRemaining = accessRemainingSeconds()
        val result = mutableMapOf<String, Any>(
            "running" to preferences.getBoolean(EscapeKeys.RUNNING, false),
            "locked" to preferences.getBoolean(EscapeKeys.LOCKED, false),
            "missionActive" to preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false),
            "lockMode" to preferences.getString(
                EscapeKeys.LOCK_MODE,
                EscapeKeys.LOCK_MODE_WALK
            ),
            "accessActive" to (accessRemaining > 0),
            "accessRemainingSeconds" to accessRemaining,
            "socialSeconds" to 0,
            "walkSeconds" to preferences.getInt(EscapeKeys.WALK_SECONDS, 0),
            "walkSteps" to preferences.getInt(EscapeKeys.WALK_STEPS, 0),
            "stepSensorAvailable" to preferences.getBoolean(
                EscapeKeys.STEP_SENSOR_AVAILABLE,
                true
            ),
            "foregroundPackage" to preferences.getString(
                EscapeKeys.FOREGROUND_PACKAGE,
                ""
            ),
            "missionTitle" to preferences.getString(
                EscapeKeys.MISSION_TITLE,
                "Earn Your Scroll"
            ),
            "missionInstruction" to preferences.getString(
                EscapeKeys.MISSION_INSTRUCTION,
                "Complete a screen-free mission to unlock social apps."
            ),
            "missionSource" to preferences.getString(
                EscapeKeys.MISSION_SOURCE,
                "fallback"
            ),
            "missionProofTag" to preferences.getString(EscapeKeys.MISSION_PROOF_TAG, "nature"),
            "missionGenerating" to preferences.getBoolean(
                EscapeKeys.MISSION_GENERATING,
                false
            ),
            "aiModelInstalled" to modelManager.isInstalled(),
            "aiEngineReady" to preferences.getBoolean(EscapeKeys.AI_ENGINE_READY, false)
        )

        result.putAll(downloadStatus)
        result["aiLastError"] = preferences.getString(EscapeKeys.AI_LAST_ERROR, "")
        result.putAll(stats.statusMap())
        return result
    }

    private fun accessRemainingSeconds(): Int {
        val until = preferences.getLong(EscapeKeys.ACCESS_UNTIL_ELAPSED, 0L)
        val now = android.os.SystemClock.elapsedRealtime()
        val issued = preferences.getLong(EscapeKeys.ACCESS_ISSUED_ELAPSED, -1L)
        val clock = DaypartClock(activity, preferences)
        val issuedBoot = preferences.getInt(EscapeKeys.ACCESS_ISSUED_BOOT_COUNT, -1)
        if (issued >= 0L && now >= issued &&
            (issuedBoot < 0 || issuedBoot == clock.bootCount())) {
            return max(0L, (until - now + 999L) / 1000L).toInt()
        }
        val fallback = preferences.getLong(EscapeKeys.ACCESS_UNTIL_MS, 0L)
        if (fallback <= 0L) return 0
        return max(0L, (fallback - DaypartClock(activity, preferences).nowMillis() + 999L) / 1000L).toInt()
    }
}
