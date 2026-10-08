package com.example.escape

import kotlin.math.max

class GemmaMissionGenerator(
    private val preferences: EscapePreferences,
    private val engineManager: GemmaEngineManager
) {
    fun generateMissionBatch(
        lockMode: String,
        count: Int,
        missionMinutes: Int,
        minimumSteps: Int
    ): List<EscapeMission> {
        val safeCount = count.coerceIn(1, 8)
        val previousTitle = preferences.getString(
            EscapeKeys.LAST_MISSION_TITLE,
            "none"
        )

        val prompt = if (lockMode == EscapeKeys.LOCK_MODE_EVENING) {
            """
            Generate $safeCount DIFFERENT screen-free evening missions for ESCAPE.
            It is after 6 PM. Do NOT ask the user to go outside or walk.
            Each mission should occupy about $missionMinutes minutes.
            Prefer: reading a physical book, family conversation, a board/card game,
            journaling on paper, drawing, light stretching, tidying one small area,
            or preparing for tomorrow.
            Previous mission title: $previousTitle

            Return exactly $safeCount lines and nothing else.
            Each line must use this format:
            TITLE || MISSION

            TITLE: max 6 words.
            MISSION: max 35 words, no phone/internet needed.
            """.trimIndent()
        } else {
            """
            Generate $safeCount DIFFERENT outdoor/fresh-air missions for ESCAPE.
            The user must earn social-media access first.
            Each mission lasts about $missionMinutes minutes and should naturally include
            at least $minimumSteps steps when walking is possible.
            Make them playful and varied: short walk, colour hunt, tree/sky observation,
            gentle movement, noticing sounds, exploring a familiar safe area differently,
            or a simple outdoor family activity.
            Previous mission title: $previousTitle

            Do not require a phone, internet, photos, shopping, strangers, entering buildings,
            collecting objects, trespassing, or unsafe road crossing.

            Return exactly $safeCount lines and nothing else.
            Each line must use this format:
            TITLE || MISSION

            TITLE: max 6 words.
            MISSION: max 35 words.
            """.trimIndent()
        }

        val response = engineManager.generate(prompt)
        val parsed = parseBatch(response)
        if (parsed.isEmpty()) {
            return fallbackMissions(lockMode, missionMinutes, minimumSteps)
                .take(safeCount)
        }
        return parsed.take(safeCount)
    }

    fun fallbackMissions(
        lockMode: String,
        missionMinutes: Int,
        minimumSteps: Int
    ): List<EscapeMission> {
        val minutes = max(1, missionMinutes)
        val steps = max(0, minimumSteps)

        return if (lockMode == EscapeKeys.LOCK_MODE_EVENING) {
            listOf(
                EscapeMission(
                    "Read Something Real",
                    "Put the phone away and read a physical book for $minutes minutes.",
                    "fallback"
                ),
                EscapeMission(
                    "Family Time",
                    "Spend $minutes screen-free minutes talking, playing, or relaxing with family.",
                    "fallback"
                ),
                EscapeMission(
                    "Paper Reset",
                    "Use paper for $minutes minutes: journal, sketch, make tomorrow's plan, or write a short list.",
                    "fallback"
                )
            )
        } else {
            listOf(
                EscapeMission(
                    "Notice Something New",
                    "Walk for $minutes minutes and at least $steps steps. Notice three details you normally pass without seeing.",
                    "fallback"
                ),
                EscapeMission(
                    "Colour Hunt",
                    "Walk for $minutes minutes and at least $steps steps. Notice five different colours around you without using your phone.",
                    "fallback"
                ),
                EscapeMission(
                    "Sound Safari",
                    "Walk for $minutes minutes and at least $steps steps. Pay attention to three different outdoor sounds along the way.",
                    "fallback"
                )
            )
        }
    }

    private fun parseBatch(response: String): List<EscapeMission> {
        return response
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains("||") }
            .mapNotNull { line ->
                val cleaned = line.replace(Regex("^\\s*\\d+[.)-]?\\s*"), "")
                val parts = cleaned.split("||", limit = 2)
                if (parts.size != 2) return@mapNotNull null

                val title = parts[0].cleanText().take(80)
                val instruction = parts[1].cleanText().take(320)
                if (title.isBlank() || instruction.isBlank()) return@mapNotNull null

                EscapeMission(
                    title = title,
                    instruction = instruction,
                    source = "gemma-local"
                )
            }
            .distinctBy { it.title.lowercase() }
            .toList()
    }

    private fun String.cleanText(): String =
        trim()
            .trim('*', '-', '"', '\'')
            .replace(Regex("\\s+"), " ")
}
