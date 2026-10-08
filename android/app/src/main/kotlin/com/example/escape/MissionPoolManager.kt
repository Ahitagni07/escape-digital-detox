package com.example.escape

import org.json.JSONArray
import org.json.JSONObject

class MissionPoolManager(
    private val preferences: EscapePreferences,
    private val generator: GemmaMissionGenerator,
    private val modelManager: GemmaModelManager
) {
    private val generationLock = Any()
    private val generatingModes = mutableSetOf<String>()

    fun takeCached(lockMode: String): EscapeMission? {
        val pool = readPool(lockMode).toMutableList()
        if (pool.isEmpty()) return null

        val mission = pool.removeAt(0)
        writePool(lockMode, pool)
        return mission
    }

    fun cachedCount(lockMode: String): Int = readPool(lockMode).size

    fun refillAsync(
        lockMode: String,
        missionMinutes: Int,
        minimumSteps: Int,
        onReady: ((List<EscapeMission>) -> Unit)? = null
    ) {
        if (!modelManager.isInstalled()) {
            onReady?.invoke(emptyList())
            return
        }

        synchronized(generationLock) {
            if (generatingModes.contains(lockMode)) {
                onReady?.invoke(emptyList())
                return
            }
            generatingModes.add(lockMode)
        }

        Thread({
            try {
                val generated = generator.generateMissionBatch(
                    lockMode = lockMode,
                    count = 6,
                    missionMinutes = missionMinutes,
                    minimumSteps = minimumSteps
                )

                if (generated.isNotEmpty()) {
                    val existing = readPool(lockMode)
                    val merged = (existing + generated)
                        .distinctBy { it.title.lowercase() }
                        .take(10)
                    writePool(lockMode, merged)
                }

                onReady?.invoke(generated)
            } finally {
                synchronized(generationLock) {
                    generatingModes.remove(lockMode)
                }
            }
        }, "escape-gemma-pool-$lockMode").start()
    }

    private fun readPool(lockMode: String): List<EscapeMission> {
        val json = preferences.getString(poolKey(lockMode), "")
        if (json.isBlank()) return emptyList()

        return try {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val obj = array.optJSONObject(index) ?: continue
                    val title = obj.optString("title").trim()
                    val instruction = obj.optString("instruction").trim()
                    val source = obj.optString("source", "gemma-local")
                    // Ignore v6 cache: those prompts did not include photo targets.
                    if (!obj.has("proofTag")) continue
                    if (title.isNotBlank() && instruction.isNotBlank()) {
                        add(EscapeMission(title, instruction, source, obj.optString("proofTag", "nature")))
                    }
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun writePool(lockMode: String, missions: List<EscapeMission>) {
        val array = JSONArray()
        missions.forEach { mission ->
            array.put(
                JSONObject().apply {
                    put("title", mission.title)
                    put("instruction", mission.instruction)
                    put("source", mission.source)
                    put("proofTag", mission.proofTag)
                }
            )
        }
        preferences.putString(poolKey(lockMode), array.toString())
    }

    private fun poolKey(lockMode: String): String =
        if (lockMode == EscapeKeys.LOCK_MODE_EVENING) {
            EscapeKeys.EVENING_MISSION_POOL_JSON
        } else {
            EscapeKeys.DAY_MISSION_POOL_JSON
        }
}
