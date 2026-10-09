package com.example.escape

import android.Manifest
import android.content.ActivityNotFoundException
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.content.ClipData
import androidx.core.content.FileProvider
import java.io.File
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
        private const val WEEKEND_LOCATION_REQUEST = 4110
        private const val RIDE_LOCATION_REQUEST = 4111
    }

    private val permissions = PermissionHelper(activity)
    private val appQuery = AppQueryHelper(activity)
    private val preferences = EscapePreferences(activity)
    private val stats = StatsManager(preferences)
    private val modelManager = GemmaModelManager(activity, preferences)
    private val autoDownloader = GemmaAutoDownloader(activity, preferences, modelManager)

    private var pendingModelImportResult: MethodChannel.Result? = null
    private var pendingPhotoResult: MethodChannel.Result? = null
    private var pendingPhotoUri: Uri? = null
    private var pendingPhotoFile: File? = null
    private var capturedProofFile: File? = null
    private var capturedProofSignature: String? = null
    private var pendingRideResult: MethodChannel.Result? = null
    private var pendingWeekendResult: MethodChannel.Result? = null
    private val weekendExplorer = WeekendExplorer(activity)

    fun register(binaryMessenger: BinaryMessenger) {
        MethodChannel(binaryMessenger, CHANNEL_NAME)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getSavedWeekendPlaces" -> result.success(weekendExplorer.savedPlaces())

                    "getWeekendPlaces" -> {
                        if (pendingWeekendResult != null) {
                            result.error("busy", "Another weekend search is running", null)
                        } else {
                            pendingWeekendResult = result
                            val refresh = call.argument<Boolean>("refresh") == true
                            if (weekendExplorer.hasLocationPermission()) {
                                loadWeekendPlaces(refresh)
                            } else {
                                pendingWeekendRefresh = refresh
                                activity.requestPermissions(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ), WEEKEND_LOCATION_REQUEST)
                            }
                        }
                    }

                    "openWeekendCyclingDirections" -> {
                        val lat = call.argument<Double>("latitude")
                        val lon = call.argument<Double>("longitude")
                        if (lat == null || lon == null) {
                            result.error("bad_coordinates", "No mapped coordinates", null)
                        } else try {
                            weekendExplorer.openBicycleDirections(lat, lon)
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("map_failed", e.message, null)
                        }
                    }

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
                        if (MonitorService.activeInstance == null) {
                            result.error("monitor_not_running", "Start ESCAPE first", null)
                        } else {
                            sendMonitorAction(EscapeKeys.ACTION_START_MISSION)
                            result.success(true)
                        }
                    }
                    "startCycleQuest" -> {
                        if (preferences.getString(EscapeKeys.LOCK_MODE, "") != EscapeKeys.LOCK_MODE_WEEKEND) {
                            result.error("wrong_mode", "Cycling quest is available during weekend daylight only.", null)
                        } else if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED) {
                            sendMonitorAction(EscapeKeys.ACTION_START_RIDE)
                            result.success(true)
                        } else if (pendingRideResult != null) {
                            result.error("busy", "Another permission request is open", null)
                        } else {
                            pendingRideResult = result
                            activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION), RIDE_LOCATION_REQUEST)
                        }
                    }
                    "speakMission" -> {
                        result.success(MonitorService.activeInstance?.readMissionAloud() == true)
                    }

                    // Capture and verification are intentionally separate operations.
                    // Flutter can preview the private cached image before review.
                    "captureMissionPhoto" -> {
                        if (!preferences.getBoolean(EscapeKeys.RUNNING, false) ||
                            !preferences.getBoolean(EscapeKeys.LOCKED, false)) {
                            result.success(mapOf("captured" to false, "message" to "No mission is waiting."))
                        } else if (pendingPhotoResult != null) {
                            result.error("photo_busy", "Camera already open.", null)
                        } else if (MonitorService.activeInstance?.isProofReady() != true) {
                            result.success(mapOf("captured" to false,
                                "message" to "Complete your walk/ride or evening activity before taking a photo."))
                        } else {
                            try {
                                deleteCapturedProof()
                                val folder = File(activity.cacheDir, "proofs").apply { mkdirs() }
                                val file = File.createTempFile("quest_", ".jpg", folder)
                                val uri = FileProvider.getUriForFile(activity,
                                    "${activity.packageName}.fileprovider", file)
                                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                                    putExtra(MediaStore.EXTRA_OUTPUT, uri)
                                    clipData = ClipData.newUri(activity.contentResolver, "quest", uri)
                                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                pendingPhotoFile = file
                                pendingPhotoUri = uri
                                pendingPhotoResult = result
                                activity.startActivityForResult(intent, PHOTO_PICK_REQUEST_CODE)
                            } catch (e: Exception) {
                                pendingPhotoResult = null
                                pendingPhotoUri = null
                                pendingPhotoFile?.delete()
                                pendingPhotoFile = null
                                result.error("camera_unavailable", "Could not open camera: ${e.message}", null)
                            }
                        }
                    }

                    "discardMissionPhoto" -> {
                        deleteCapturedProof()
                        result.success(true)
                    }

                    "analyzeMissionPhoto" -> {
                        val file = capturedProofFile
                        if (file == null || !file.exists() || file.length() < 1024L) {
                            result.success(mapOf("approved" to false,
                                "message" to "No photo to analyze. Capture a fresh photo first."))
                        } else if (MonitorService.activeInstance?.isProofReady() != true ||
                            capturedProofSignature != proofSignature()) {
                            deleteCapturedProof()
                            result.success(mapOf("approved" to false,
                                "message" to "The mission changed or is no longer ready. Take a new photo."))
                        } else {
                            val photoUri = FileProvider.getUriForFile(activity,
                                "${activity.packageName}.fileprovider", file)
                            val snapshotTitle = preferences.getString(EscapeKeys.MISSION_TITLE, "")
                            val tag = preferences.getString(EscapeKeys.MISSION_PROOF_TAG, "nature")
                            val code = preferences.getString(EscapeKeys.MISSION_PROOF_CODE, "")
                            val instruction = preferences.getString(EscapeKeys.MISSION_INSTRUCTION, "")
                            val source = preferences.getString(EscapeKeys.MISSION_SOURCE, "fallback")
                            PhotoProofVerifier(activity).verify(photoUri, tag, code) { evidence ->
                                if (!evidence.passed) {
                                    activity.runOnUiThread {
                                        result.success(mapOf(
                                            "approved" to false, "message" to evidence.message,
                                            "reviewer" to "offline-mlkit"))
                                    }
                                } else {
                                    Thread({
                                        // Gemma is text-only. ML Kit evaluates the pixels.
                                        // Reuse the loaded model for optional review of labels/OCR.
                                        val gemmaDecision = try {
                                            MonitorService.activeInstance?.reviewProof(
                                                EscapeMission(snapshotTitle, instruction, source, tag), evidence)
                                        } catch (_: Throwable) { null }
                                        activity.runOnUiThread {
                                            val sameMission = MonitorService.activeInstance?.isProofReady() == true &&
                                                preferences.getBoolean(EscapeKeys.LOCKED, false) &&
                                                preferences.getBoolean(EscapeKeys.RUNNING, false) &&
                                                capturedProofSignature == proofSignature() &&
                                                capturedProofFile == file
                                            val approved = sameMission && gemmaDecision != false
                                            if (approved) {
                                                // Send native unlock before returning approval.
                                                sendMonitorAction(EscapeKeys.ACTION_PHOTO_APPROVED)
                                                deleteCapturedProof()
                                            }
                                            result.success(mapOf(
                                                "approved" to approved,
                                                "reviewer" to if (gemmaDecision == null)
                                                    "offline-mlkit" else "gemma-text-plus-mlkit",
                                                "message" to when {
                                                    !sameMission -> "Quest changed. Capture proof for the current quest."
                                                    gemmaDecision == false -> "Photo was readable, but did not clearly match the quest. Retake it with the requested subject visible."
                                                    gemmaDecision == true -> "Approved! Gemma reviewed the extracted clues. Social access earned."
                                                    else -> "Approved by offline photo recognition! Social access earned."
                                                }
                                            ))
                                        }
                                    }, "escape-photo-analysis").start()
                                }
                            }
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

    private var pendingWeekendRefresh = false

    private fun proofSignature(): String = listOf(
        preferences.getString(EscapeKeys.MISSION_TITLE, ""),
        preferences.getString(EscapeKeys.MISSION_PROOF_TAG, ""),
        preferences.getString(EscapeKeys.MISSION_PROOF_CODE, ""),
        preferences.getString(EscapeKeys.MISSION_ACTIVITY, "")
    ).joinToString("::")

    private fun deleteCapturedProof() {
        capturedProofFile?.delete()
        capturedProofFile = null
        capturedProofSignature = null
    }

    private fun loadWeekendPlaces(refresh: Boolean) {
        weekendExplorer.discover(refresh) { data ->
            val pending = pendingWeekendResult ?: return@discover
            pendingWeekendResult = null
            pending.success(data)
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ): Boolean {
        if (requestCode == RIDE_LOCATION_REQUEST) {
            val pending = pendingRideResult
            pendingRideResult = null
            if (pending != null) {
                if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                    sendMonitorAction(EscapeKeys.ACTION_START_RIDE)
                    pending.success(true)
                } else pending.error("gps_denied", "Precise location is required for cycling distance. Choose the walking quest instead.", null)
            }
            return true
        }
        if (requestCode != WEEKEND_LOCATION_REQUEST) return false
        if (weekendExplorer.hasLocationPermission()) {
            loadWeekendPlaces(pendingWeekendRefresh)
        } else {
            pendingWeekendResult?.success(mapOf(
                "places" to emptyList<Any>(),
                "message" to "Location permission denied. You can still do the offline weekend goals."
            ))
            pendingWeekendResult = null
        }
        return true
    }

    fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (requestCode == PHOTO_PICK_REQUEST_CODE) {
            val pending = pendingPhotoResult ?: return true
            pendingPhotoResult = null
            val file = pendingPhotoFile
            pendingPhotoFile = null
            pendingPhotoUri = null
            if (resultCode != Activity.RESULT_OK || file == null || file.length() < 1024L) {
                file?.delete()
                pending.success(mapOf("captured" to false,
                    "message" to "No photo was saved. Try again."))
                return true
            }
            if (MonitorService.activeInstance?.isProofReady() != true) {
                file.delete()
                pending.success(mapOf("captured" to false,
                    "message" to "Mission progress changed. Complete the goal again."))
                return true
            }
            capturedProofFile = file
            capturedProofSignature = proofSignature()
            pending.success(mapOf(
                "captured" to true,
                "path" to file.absolutePath,
                "message" to "Photo ready. Check the preview, then tap Analyze photo."
            ))
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
            "missionActivity" to preferences.getString(EscapeKeys.MISSION_ACTIVITY, ""),
            "rideMeters" to preferences.getInt(EscapeKeys.RIDE_METERS, 0),
            "rideTargetMeters" to preferences.getInt(EscapeKeys.RIDE_TARGET_METERS, 1500),
            "walkTargetSeconds" to preferences.getInt(EscapeKeys.WALK_SECONDS_TARGET, 600),
            "minRequiredSteps" to preferences.getInt(EscapeKeys.EFFECTIVE_MIN_STEPS, 600),
            "proofReady" to (MonitorService.activeInstance?.isProofReady() == true),
            "sunsetAware" to true,
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
            "missionProofCode" to preferences.getString(EscapeKeys.MISSION_PROOF_CODE, ""),
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
