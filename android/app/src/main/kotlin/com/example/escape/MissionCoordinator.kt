package com.example.escape

import android.content.Context
import kotlin.math.max

class MissionCoordinator(
    context: Context,
    private val preferences: EscapePreferences
) {
    private val modelManager = GemmaModelManager(context, preferences)
    private val engineManager = GemmaEngineManager(context, modelManager)
    private val generator = GemmaMissionGenerator(preferences, engineManager)
    private val pool = MissionPoolManager(preferences, generator, modelManager)

    fun prepareAsync() {
        if (!modelManager.isInstalled()) return

        preferences.putBoolean(EscapeKeys.AI_ENGINE_PREPARING, true)
        engineManager.prepareAsync { ready, error ->
            preferences.putBoolean(EscapeKeys.AI_ENGINE_PREPARING, false)
            preferences.putBoolean(EscapeKeys.AI_ENGINE_READY, ready)
            if (error != null) {
                preferences.putString(EscapeKeys.AI_LAST_ERROR, error)
            }
        }
    }

    /**
     * Selects one current mission immediately. If no cached Gemma mission exists,
     * ESCAPE uses a safe fallback and replaces it with a locally generated mission
     * only while the mission is still waiting to be started.
     */
    fun prepareMission(
        lockMode: String,
        onMissionUpdated: (() -> Unit)? = null
    ) {
        val missionMinutes = missionMinutes()
        val minimumSteps = if (lockMode == EscapeKeys.LOCK_MODE_EVENING) {
            0
        } else {
            preferences.getInt(EscapeKeys.EFFECTIVE_MIN_STEPS, 600)
        }

        val cached = pool.takeCached(lockMode)
        val fallback = generator
            .fallbackMissions(lockMode, missionMinutes, minimumSteps)
            .first()

        saveMission(cached ?: fallback)
        preferences.putBoolean(EscapeKeys.MISSION_GENERATING, false)

        if (!modelManager.isInstalled()) {
            onMissionUpdated?.invoke()
            return
        }

        // Keep a small local mission pool so notifications do not invoke the LLM.
        if (pool.cachedCount(lockMode) >= 3) {
            onMissionUpdated?.invoke()
            return
        }

        preferences.putBoolean(EscapeKeys.MISSION_GENERATING, true)
        preferences.putString(EscapeKeys.AI_LAST_ERROR, "")

        pool.refillAsync(
            lockMode = lockMode,
            missionMinutes = missionMinutes,
            minimumSteps = minimumSteps
        ) { generated ->
            try {
                preferences.putBoolean(EscapeKeys.AI_ENGINE_READY, generated.isNotEmpty())

                // If we had no cached AI mission and the user has not started the
                // fallback yet, replace it with the first new local-Gemma mission.
                if (
                    cached == null &&
                    generated.isNotEmpty() &&
                    !preferences.getBoolean(EscapeKeys.MISSION_ACTIVE, false) &&
                    preferences.getBoolean(EscapeKeys.LOCKED, false)
                ) {
                    val fresh = pool.takeCached(lockMode)
                    if (fresh != null) {
                        saveMission(fresh)
                    }
                }
            } catch (t: Throwable) {
                preferences.putString(
                    EscapeKeys.AI_LAST_ERROR,
                    t.message ?: t.javaClass.simpleName
                )
                preferences.putBoolean(EscapeKeys.AI_ENGINE_READY, false)
            } finally {
                preferences.putBoolean(EscapeKeys.MISSION_GENERATING, false)
                onMissionUpdated?.invoke()
            }
        }
    }

    fun currentMission(): EscapeMission = EscapeMission(
        title = preferences.getString(
            EscapeKeys.MISSION_TITLE,
            "Earn Your Scroll"
        ),
        instruction = preferences.getString(
            EscapeKeys.MISSION_INSTRUCTION,
            "Complete a screen-free mission to unlock social apps."
        ),
        source = preferences.getString(
            EscapeKeys.MISSION_SOURCE,
            "fallback"
        )
    )

    fun close() {
        engineManager.close()
        preferences.putBoolean(EscapeKeys.AI_ENGINE_READY, false)
        preferences.putBoolean(EscapeKeys.AI_ENGINE_PREPARING, false)
    }

    private fun missionMinutes(): Int {
        val seconds = preferences.getInt(EscapeKeys.WALK_SECONDS_TARGET, 600)
        return max(1, (seconds + 59) / 60)
    }

    private fun saveMission(mission: EscapeMission) {
        preferences.putString(EscapeKeys.MISSION_TITLE, mission.title)
        preferences.putString(EscapeKeys.MISSION_INSTRUCTION, mission.instruction)
        preferences.putString(EscapeKeys.MISSION_SOURCE, mission.source)
        preferences.putString(EscapeKeys.LAST_MISSION_TITLE, mission.title)
    }
}
