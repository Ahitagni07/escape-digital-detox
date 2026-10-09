package com.example.escape

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Voice mission readout; intentionally refuses network-dependent voices. */
class OfflineVoiceGuide(context: Context) {
    @Volatile private var ready = false
    private val engine = TextToSpeech(context.applicationContext) { code ->
        ready = code == TextToSpeech.SUCCESS
    }
    fun speak(message: String): Boolean {
        if (!ready) return false
        return try {
            val candidates = engine.voices?.filter {
                it.locale.language == "en" && !it.isNetworkConnectionRequired
            } ?: emptyList()
            val chosen = candidates.firstOrNull() ?: return false
            engine.voice = chosen
            engine.speak(message.take(600), TextToSpeech.QUEUE_FLUSH, null, "escape-mission") >= 0
        } catch (_: Exception) { false }
    }
    fun close() { engine.stop(); engine.shutdown() }
}
