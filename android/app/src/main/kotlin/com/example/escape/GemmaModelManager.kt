package com.example.escape

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import java.io.File

class GemmaModelManager(
    private val context: Context,
    private val preferences: EscapePreferences = EscapePreferences(context)
) {
    companion object {
        const val MODEL_PAGE_URL =
            "https://huggingface.co/litert-community/Gemma3-1B-IT/blob/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"

        const val RECOMMENDED_MODEL_FILE =
            "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm"

        // Exact size published for this LiteRT-LM model.
        const val EXPECTED_MODEL_SIZE_BYTES = 584_417_280L

        private const val PRIVATE_MODEL_FILE = "escape-gemma.litertlm"
        private const val MODEL_DIR = "models"

        // Allow a small amount of tolerance for future repackaging while still
        // rejecting HTML error pages / tiny broken downloads.
        private const val MIN_VALID_MODEL_BYTES = 550_000_000L
    }

    private val modelDirectory: File
        get() = File(context.filesDir, MODEL_DIR).apply { mkdirs() }

    private val privateModelFile: File
        get() = File(modelDirectory, PRIVATE_MODEL_FILE)

    /**
     * DownloadManager writes here. It is app-specific external storage, so no
     * broad storage permission is required.
     */
    val autoDownloadedModelFile: File
        get() {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.filesDir
            return File(base, RECOMMENDED_MODEL_FILE)
        }

    val modelFile: File
        get() = when {
            isValidModel(privateModelFile) -> privateModelFile
            isValidModel(autoDownloadedModelFile) -> autoDownloadedModelFile
            else -> privateModelFile
        }

    fun isInstalled(): Boolean = isValidModel(modelFile)

    fun statusMap(): Map<String, Any> = mapOf(
        "installed" to isInstalled(),
        "modelFileName" to preferences.getString(
            EscapeKeys.AI_MODEL_ORIGINAL_NAME,
            if (isInstalled()) RECOMMENDED_MODEL_FILE else ""
        ),
        "sizeBytes" to if (isInstalled()) modelFile.length() else 0L,
        "expectedSizeBytes" to EXPECTED_MODEL_SIZE_BYTES,
        "recommendedFile" to RECOMMENDED_MODEL_FILE,
        "modelPageUrl" to MODEL_PAGE_URL,
        "runtime" to "LiteRT-LM",
        "modelFamily" to "Gemma 3 1B IT",
        "backend" to "CPU",
        "lastError" to preferences.getString(EscapeKeys.AI_LAST_ERROR, "")
    )

    fun importFromUri(uri: Uri): Map<String, Any> {
        val displayName = queryDisplayName(uri) ?: RECOMMENDED_MODEL_FILE

        if (!displayName.lowercase().endsWith(".litertlm")) {
            throw IllegalArgumentException(
                "Please choose a .litertlm model file."
            )
        }

        val temp = File(modelDirectory, "$PRIVATE_MODEL_FILE.tmp")
        if (temp.exists()) temp.delete()

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Android could not open the selected file." }
            temp.outputStream().use { output ->
                input.copyTo(output, bufferSize = 1024 * 1024)
            }
        }

        if (!isValidModel(temp)) {
            temp.delete()
            throw IllegalArgumentException(
                "The selected file does not look like the expected ~584 MB Gemma LiteRT-LM model."
            )
        }

        if (privateModelFile.exists()) privateModelFile.delete()
        if (!temp.renameTo(privateModelFile)) {
            temp.copyTo(privateModelFile, overwrite = true)
            temp.delete()
        }

        preferences.putString(EscapeKeys.AI_MODEL_ORIGINAL_NAME, displayName)
        preferences.putString(EscapeKeys.AI_LAST_ERROR, "")
        return statusMap()
    }

    fun verifyAutoDownloadedModel(): Boolean {
        val valid = isValidModel(autoDownloadedModelFile)

        if (valid) {
            preferences.putString(
                EscapeKeys.AI_MODEL_ORIGINAL_NAME,
                RECOMMENDED_MODEL_FILE
            )
            preferences.putString(EscapeKeys.AI_LAST_ERROR, "")
        } else if (autoDownloadedModelFile.exists()) {
            preferences.putString(
                EscapeKeys.AI_LAST_ERROR,
                "Downloaded model is incomplete or invalid " +
                    "(${autoDownloadedModelFile.length()} bytes)."
            )
        }

        return valid
    }

    fun removeModel(): Map<String, Any> {
        if (privateModelFile.exists()) privateModelFile.delete()
        if (autoDownloadedModelFile.exists()) autoDownloadedModelFile.delete()

        preferences.putString(EscapeKeys.AI_MODEL_ORIGINAL_NAME, "")
        preferences.putString(EscapeKeys.AI_LAST_ERROR, "")
        return statusMap()
    }

    private fun isValidModel(file: File): Boolean =
        file.exists() &&
            file.isFile &&
            file.length() >= MIN_VALID_MODEL_BYTES

    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null

        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = cursor.getString(index)
                }
            }
        }

        return name
    }
}
