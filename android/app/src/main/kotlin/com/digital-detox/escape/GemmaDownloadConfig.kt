package com.example.escape

object GemmaDownloadConfig {
    /**
     * Configure this in android/local.properties:
     *
     * GEMMA_MODEL_URL=https://github.com/<USER>/<REPO>/releases/download/gemma-v1/Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm
     *
     * The URL must point directly to the .litertlm bytes.
     */
    val MODEL_URL: String
        get() = BuildConfig.GEMMA_MODEL_URL.trim()

    // The model is ~584 MB, so keep Wi-Fi-only for the normal first download.
    const val WIFI_ONLY = true

    fun isConfigured(): Boolean {
        val url = MODEL_URL
        return url.startsWith("https://") &&
            url.lowercase().endsWith(".litertlm") &&
            !url.contains("<USER>") &&
            !url.contains("<REPO>") &&
            !url.contains("REPLACE_WITH_")
    }
}
