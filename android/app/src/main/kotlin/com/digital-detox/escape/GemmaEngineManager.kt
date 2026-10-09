package com.example.escape

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

class GemmaEngineManager(
    private val context: Context,
    private val modelManager: GemmaModelManager
) {
    private val lifecycleLock = Any()
    private val inferenceLock = Any()

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var preparing = false

    fun prepareAsync(onFinished: ((Boolean, String?) -> Unit)? = null) {
        if (!modelManager.isInstalled()) {
            onFinished?.invoke(false, "Gemma model is not installed.")
            return
        }

        synchronized(lifecycleLock) {
            if (engine != null) {
                onFinished?.invoke(true, null)
                return
            }
            if (preparing) return
            preparing = true
        }

        Thread({
            try {
                ensureEngine()
                onFinished?.invoke(true, null)
            } catch (t: Throwable) {
                onFinished?.invoke(false, t.message ?: t.javaClass.simpleName)
            } finally {
                preparing = false
            }
        }, "escape-gemma-prepare").start()
    }

    /**
     * Synchronous inference. Always call this from a background thread.
     */
    fun generate(prompt: String): String = synchronized(inferenceLock) {
        val activeEngine = ensureEngine()

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(
                "You are ESCAPE, an offline screen-time coach. " +
                    "Create short, safe, practical screen-free missions. " +
                    "Before 6 PM prefer outdoor walking, observing nature, simple movement, " +
                    "or playful activities. After 6 PM prefer reading a physical book, " +
                    "family time, journaling, drawing, board games, light stretching, or " +
                    "preparing for tomorrow. Never require internet, shopping, driving, " +
                    "talking to strangers, trespassing, unsafe road crossing, or looking at " +
                    "the phone during the activity."
            ),
            samplerConfig = SamplerConfig(
                topK = 40,
                topP = 0.90,
                temperature = 0.80,
                seed = 7
            ),
            maxOutputToken = 384
        )

        activeEngine.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessage(prompt, maxOutputToken = 384).toString().trim()
        }
    }

    fun close() {
        synchronized(lifecycleLock) {
            val active = engine
            engine = null
            preparing = false
            if (active != null) {
                try {
                    active.close()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun ensureEngine(): Engine {
        engine?.let { return it }

        return synchronized(lifecycleLock) {
            engine?.let { return@synchronized it }

            check(modelManager.isInstalled()) {
                "Gemma model is not installed."
            }

            val cacheDirectory = File(
                context.cacheDir,
                "litertlm"
            ).apply { mkdirs() }

            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

            val created = Engine(
                EngineConfig(
                    modelPath = modelManager.modelFile.absolutePath,
                    backend = Backend.CPU(),
                    maxNumTokens = 1536,
                    cacheDir = cacheDirectory.absolutePath
                )
            )

            try {
                created.initialize()
                engine = created
                created
            } catch (t: Throwable) {
                try {
                    created.close()
                } catch (_: Throwable) {
                }
                throw t
            }
        }
    }
}
