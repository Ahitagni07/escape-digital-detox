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
            Make each mission imaginative and practical, for instance: invent a two-line story
            with your family; sketch an imaginary island; build a paper airplane; organise a
            tiny book club; make a recipe from ingredients already at home; invent a card
            game; try origami; turn a household item into a short story; write a thank-you
            note; do a five-item memory game. No photos of family are required.
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
            Make them playful and varied: find tree bark with an unusual texture;
            compare two shades of leaves; find a safe place to see the sky;
            look for a reflection in water; spot flowers or grass; invent a
            micro-story inspired by a tree. Every challenge ends with a nature
            photo submitted as lightweight offline proof. No faces, strangers or addresses.
            Previous mission title: $previousTitle

            Do not require internet, shopping, strangers, entering buildings,
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
        val categories = listOf("tree", "water", "nature", "flower", "sky", "nature")
        return parsed.take(safeCount).mapIndexed { index, mission ->
            if (lockMode == EscapeKeys.LOCK_MODE_EVENING) mission.copy(proofTag = "none")
            else mission.copy(
                proofTag = categories[index % categories.size],
                instruction = mission.instruction + " Upload a photo showing ${categories[index % categories.size]}."
            )
        }
    }

    fun fallbackMissions(
        lockMode: String,
        missionMinutes: Int,
        minimumSteps: Int
    ): List<EscapeMission> {
        val minutes = max(1, missionMinutes)

        return if (lockMode == EscapeKeys.LOCK_MODE_EVENING) {
            listOf(
                EscapeMission(
                    "One-Page Adventure",
                    "Read a physical book, then invent a different ending on paper for $minutes minutes.",
                    "fallback",
                    "none"
                ),
                EscapeMission(
                    "Memory Museum",
                    "Trade three happy memories with a family member or write them down for $minutes minutes.",
                    "fallback",
                    "none"
                ),
                EscapeMission(
                    "Invent a Tiny Game",
                    "Invent a new card or word game, or sketch an imaginary island, for $minutes minutes.",
                    "fallback", "none"
                )
            )
        } else {
            listOf(
                EscapeMission(
                    "Notice Something New",
                    "Find an interesting tree or natural texture. Upload a nature photo to earn social access.",
                    "fallback",
                    "nature"
                ),
                EscapeMission(
                    "Colour Hunt",
                    "Find contrasting natural colours. Upload a photo of leaves, grass or a tree.",
                    "fallback"
                ),
                EscapeMission(
                    "Sound Safari",
                    "Listen for three sounds outdoors. Upload a photo of sky, trees or grass.",
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
