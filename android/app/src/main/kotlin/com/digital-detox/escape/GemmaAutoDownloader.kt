package com.example.escape

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

class GemmaAutoDownloader(
    private val context: Context,
    private val preferences: EscapePreferences,
    private val modelManager: GemmaModelManager
) {
    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun ensureStarted(): Map<String, Any> {
        if (modelManager.isInstalled()) {
            clearDownloadId()
            return installedStatus()
        }

        // No network URL: this is a manual-import flow, not a failed download.
        // Clear any stale DownloadManager row left by an older build so the UI
        // has exactly one authoritative state.
        if (!isConfigured()) {
            clearStaleDownloadForManualImport()
            return manualImportStatus()
        }

        val existingId = preferences.getLong(EscapeKeys.AI_DOWNLOAD_ID, 0L)
        if (existingId > 0L) {
            val status = refreshStatus(startIfMissing = false)
            val state = status["downloadState"]?.toString()
            if (state != "not-started" && state != "unknown") {
                return status
            }
        }

        return enqueueDownload()
    }

    /**
     * IMPORTANT:
     * A PAUSED DownloadManager job already contains Android's resumable state.
     * Do not remove/re-enqueue it, otherwise we throw that progress away.
     *
     * For a terminal FAILED job the public DownloadManager API does not expose a
     * supported way for the app to restart the exact same row from its partial
     * bytes. In that case we create a fresh request.
     */
    fun retry(): Map<String, Any> {
        if (modelManager.isInstalled()) return installedStatus()
        if (!isConfigured()) {
            clearStaleDownloadForManualImport()
            return manualImportStatus()
        }

        val current = refreshStatus(startIfMissing = false)
        return when (current["downloadState"]?.toString()) {
            "pending", "downloading", "paused" -> current
            else -> {
                removeDownloadRecord(deleteDestination = true)
                preferences.putString(EscapeKeys.AI_LAST_ERROR, "")
                ensureStarted()
            }
        }
    }

    fun refreshStatus(startIfMissing: Boolean = false): Map<String, Any> {
        if (modelManager.isInstalled()) {
            clearDownloadId()
            return installedStatus()
        }

        if (!isConfigured()) {
            clearStaleDownloadForManualImport()
            return manualImportStatus()
        }

        val id = preferences.getLong(EscapeKeys.AI_DOWNLOAD_ID, 0L)
        if (id <= 0L) {
            return if (startIfMissing) {
                ensureStarted()
            } else {
                statusMap(
                    state = "not-started",
                    progress = 0,
                    downloadedBytes = 0L,
                    totalBytes = GemmaModelManager.EXPECTED_MODEL_SIZE_BYTES,
                    reason = 0,
                    message = "Gemma download has not started yet."
                )
            }
        }

        val query = DownloadManager.Query().setFilterById(id)
        downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                clearDownloadId()
                return if (startIfMissing) {
                    ensureStarted()
                } else {
                    statusMap(
                        state = "not-started",
                        progress = 0,
                        downloadedBytes = 0L,
                        totalBytes = GemmaModelManager.EXPECTED_MODEL_SIZE_BYTES,
                        reason = 0,
                        message = "Gemma download has not started yet."
                    )
                }
            }

            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            )
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            ).coerceAtLeast(0L)
            val reportedTotal = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            val total = if (reportedTotal > 0L) {
                reportedTotal
            } else {
                GemmaModelManager.EXPECTED_MODEL_SIZE_BYTES
            }
            val percent = if (total > 0L) {
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            } else {
                0
            }
            val reason = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            )

            return when (status) {
                DownloadManager.STATUS_PENDING -> statusMap(
                    "pending",
                    percent,
                    downloaded,
                    total,
                    reason,
                    "Gemma download is queued."
                )

                DownloadManager.STATUS_RUNNING -> statusMap(
                    "downloading",
                    percent,
                    downloaded,
                    total,
                    reason,
                    "Downloading Gemma for offline AI."
                )

                DownloadManager.STATUS_PAUSED -> statusMap(
                    "paused",
                    percent,
                    downloaded,
                    total,
                    reason,
                    pausedReasonText(reason)
                )

                DownloadManager.STATUS_SUCCESSFUL -> {
                    if (modelManager.verifyAutoDownloadedModel()) {
                        clearDownloadId()
                        installedStatus()
                    } else {
                        preferences.putString(
                            EscapeKeys.AI_LAST_ERROR,
                            "The downloaded file is not a valid Gemma model."
                        )
                        statusMap(
                            "failed",
                            percent,
                            downloaded,
                            total,
                            reason,
                            "Download finished, but the model file could not be validated."
                        )
                    }
                }

                DownloadManager.STATUS_FAILED -> {
                    val message = failedReasonText(reason)
                    preferences.putString(EscapeKeys.AI_LAST_ERROR, message)
                    statusMap(
                        "failed",
                        percent,
                        downloaded,
                        total,
                        reason,
                        message
                    )
                }

                else -> statusMap(
                    "unknown",
                    percent,
                    downloaded,
                    total,
                    reason,
                    "Waiting for Android download status."
                )
            }
        }

        return statusMap(
            "unknown",
            0,
            0L,
            GemmaModelManager.EXPECTED_MODEL_SIZE_BYTES,
            0,
            "Waiting for Android download status."
        )
    }

    fun cancelAndDelete() {
        removeDownloadRecord(deleteDestination = true)
    }

    private fun enqueueDownload(): Map<String, Any> {
        modelManager.autoDownloadedModelFile.parentFile?.mkdirs()

        // A final model file that passes validation would already have returned
        // from ensureStarted(). Remove only an invalid/stale destination file.
        if (modelManager.autoDownloadedModelFile.exists()) {
            modelManager.autoDownloadedModelFile.delete()
        }

        val request = DownloadManager.Request(Uri.parse(GemmaDownloadConfig.MODEL_URL))
            .setTitle("ESCAPE local AI")
            .setDescription("Downloading Gemma 3 1B for offline missions")
            .setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(!GemmaDownloadConfig.WIFI_ONLY)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                GemmaModelManager.RECOMMENDED_MODEL_FILE
            )

        val id = downloadManager.enqueue(request)
        preferences.putLong(EscapeKeys.AI_DOWNLOAD_ID, id)
        preferences.putString(EscapeKeys.AI_LAST_ERROR, "")

        return statusMap(
            state = "pending",
            progress = 0,
            downloadedBytes = 0L,
            totalBytes = GemmaModelManager.EXPECTED_MODEL_SIZE_BYTES,
            reason = 0,
            message = if (GemmaDownloadConfig.WIFI_ONLY) {
                "Queued. Android will download on Wi-Fi and resume interruptions automatically."
            } else {
                "Queued. Android will resume interruptions automatically."
            }
        )
    }

    private fun removeDownloadRecord(deleteDestination: Boolean) {
        val id = preferences.getLong(EscapeKeys.AI_DOWNLOAD_ID, 0L)
        if (id > 0L) {
            try {
                downloadManager.remove(id)
            } catch (_: Throwable) {
            }
        }
        clearDownloadId()

        if (deleteDestination && modelManager.autoDownloadedModelFile.exists()) {
            modelManager.autoDownloadedModelFile.delete()
        }
    }

    private fun clearStaleDownloadForManualImport() {
        val id = preferences.getLong(EscapeKeys.AI_DOWNLOAD_ID, 0L)
        if (id > 0L) {
            try {
                downloadManager.remove(id)
            } catch (_: Throwable) {
            }
        }
        clearDownloadId()
        preferences.putString(EscapeKeys.AI_LAST_ERROR, "")
    }

    private fun manualImportStatus(): Map<String, Any> = statusMap(
        state = "manual-import-required",
        progress = 0,
        downloadedBytes = 0L,
        totalBytes = GemmaModelManager.EXPECTED_MODEL_SIZE_BYTES,
        reason = 0,
        message = "Model download URL is not configured. Choose the existing .litertlm file from Downloads once; Android will then let ESCAPE copy it into private app storage."
    )

    private fun isConfigured(): Boolean = GemmaDownloadConfig.isConfigured()

    private fun clearDownloadId() {
        preferences.putLong(EscapeKeys.AI_DOWNLOAD_ID, 0L)
    }

    private fun installedStatus(): Map<String, Any> = statusMap(
        state = "installed",
        progress = 100,
        downloadedBytes = modelManager.modelFile.length(),
        totalBytes = modelManager.modelFile.length(),
        reason = 0,
        message = "Gemma is installed and ready for offline use."
    )

    private fun pausedReasonText(reason: Int): String = when (reason) {
        DownloadManager.PAUSED_WAITING_TO_RETRY ->
            "Connection interrupted. Android is waiting to retry and will resume the existing download."
        DownloadManager.PAUSED_WAITING_FOR_NETWORK ->
            "Waiting for a network connection. The existing download will resume automatically."
        DownloadManager.PAUSED_QUEUED_FOR_WIFI ->
            "Waiting for Wi-Fi. The existing download will resume automatically."
        DownloadManager.PAUSED_UNKNOWN ->
            "Download paused. Android will try to resume it automatically."
        else ->
            "Download paused. Android will try to resume it automatically."
    }

    private fun failedReasonText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME ->
            "Android cannot resume this download from the server. Tap Retry to start a new request."
        DownloadManager.ERROR_DEVICE_NOT_FOUND ->
            "Storage is unavailable. Free/check device storage and tap Retry."
        DownloadManager.ERROR_FILE_ERROR ->
            "Android could not save the model file. Check free storage and tap Retry."
        DownloadManager.ERROR_INSUFFICIENT_SPACE ->
            "Not enough free storage for the Gemma model. Free space and tap Retry."
        DownloadManager.ERROR_HTTP_DATA_ERROR ->
            "Network data error while downloading Gemma. Tap Retry."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS ->
            "The model URL redirected too many times. Check GEMMA_MODEL_URL in android/local.properties."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE ->
            "The model server returned an unsupported HTTP response. Check the direct model URL."
        DownloadManager.ERROR_UNKNOWN ->
            "Gemma download failed for an unknown Android DownloadManager reason. Tap Retry."
        else -> "Gemma download failed (reason $reason). Tap Retry."
    }

    private fun statusMap(
        state: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        reason: Int,
        message: String
    ): Map<String, Any> = mapOf(
        "downloadState" to state,
        "downloadProgress" to progress,
        "downloadedBytes" to downloadedBytes,
        "downloadTotalBytes" to totalBytes,
        "downloadReason" to reason,
        "downloadMessage" to message,
        "autoDownloadConfigured" to isConfigured(),
        "wifiOnly" to GemmaDownloadConfig.WIFI_ONLY
    )
}
