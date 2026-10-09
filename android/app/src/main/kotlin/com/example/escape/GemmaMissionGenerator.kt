package com.example.escape


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
            Generate $safeCount DISTINCT achievable indoor, nature-connected, phone-free MINI-WRITING missions.
            Each must produce a tangible sheet of paper with AT LEAST 20 handwritten or
            clearly printed words, which the user photographs as proof.
            Examples: write a five-sentence nature memory; make a paper plan
            for planting herbs; list five things to notice on tomorrow's walk;
            write rules for a family nature bingo; draft a five-sentence
            thank-you note about a shared outdoor memory; design a three-stop
            family nature adventure on paper.
            Keep playful, specific, safe, under $missionMinutes minutes.
            NO abstract prompts, no physical models, no pirate maps, no
            sketches-only, no photos of faces/family/private information.
            IMPORTANT: make the instructions demand visible writing on paper.
            Previous title: $previousTitle.
            Return exactly $safeCount lines, one per mission:
            TITLE || INSTRUCTION
            TITLE max 6 words; INSTRUCTION 12-28 words, concrete and testable.
            """.trimIndent()
        } else if (lockMode == EscapeKeys.LOCK_MODE_WEEKEND) {
            """
            Create $safeCount DISTINCT safe WEEKEND outdoor quests for digital detox.
            Activities must involve a real stroll, a nature hunt, sitting on grass,
            or an optional bicycle ride on marked cycleways. Give WALK alternatives
            to cycling, and DO NOT require owning a bike.
            Each mission MUST include a safe outdoor activity AND a concrete nature subject (grass, tree,
            canal/water, flower or sky) to photograph at the destination.
            Avoid invented paths, addresses, distances, maps or road safety claims.
            No risky night travel, trespass, disturbing wildlife or entering traffic.
            Each instruction 15-26 words, practical, with a visible proof subject.
            Prior title: $previousTitle.
            Return exactly $safeCount lines as TITLE || INSTRUCTION.
            """.trimIndent()
        } else {
            """
            Generate $safeCount DISTINCT outdoor nature-photo mini-adventures.
            Each must include a walk or gentle stroll of at least $minimumSteps steps.
            Each must name ONE visible thing to photograph (tree, water,
            flower, sky, or general nature) without showing faces or addresses.
            Examples: a reflection on canal water, unusual tree bark,
            cloud shapes, a bright flower, green grass.
            Previous title: $previousTitle.
            No trespassing, roads, traffic, strangers or unsafe challenges.
            Return exactly $safeCount lines, one per mission:
            TITLE || INSTRUCTION
            TITLE max 6 words; INSTRUCTION 12-26 words, concrete and testable.
            """.trimIndent()
        }

        val response = engineManager.generate(prompt)
        val generated = parseBatch(response)
        val eligible = if (lockMode == EscapeKeys.LOCK_MODE_EVENING) {
            // Gemma 1B sometimes invents grand-sounding but unverifiable tasks.
            // Reject those instead of showing "The Shrine of Silent Flowers" again.
            val writingVerbs = Regex("\\b(write|describe|list|invent|compose|draft|create)\\b")
            val paperWords = Regex("\\b(paper|sentence|word|letter|story|dialogue|rule|recipe|note|lines?)\\b")
            generated.filter { mission ->
                val text = mission.instruction.lowercase()
                writingVerbs.containsMatchIn(text) &&
                    paperWords.containsMatchIn(text) &&
                    !text.contains("draw only") && !text.contains("build a model")
            }
        } else generated.filter { mission ->
            val text = mission.instruction.lowercase()
            listOf("walk", "stroll", "step", "cycle", "ride", "outside", "outdoor")
                .any { text.contains(it) } &&
                listOf("tree", "flower", "grass", "nature", "water", "sky", "leaf", "canal", "plant", "cloud")
                    .any { text.contains(it) }
        }

        val fallback = fallbackMissions(lockMode, missionMinutes, minimumSteps)
        val candidates = (eligible + fallback)
            .distinctBy { it.title.lowercase() }
            .take(safeCount)

        return candidates.map { mission ->
            if (lockMode == EscapeKeys.LOCK_MODE_EVENING) {
                mission.copy(
                    proofTag = "writing",
                    instruction = if (mission.instruction.contains("20 words", ignoreCase = true))
                        mission.instruction
                    else mission.instruction +
                        " Write at least 20 words on paper and photograph the page."
                )
            } else {
                val tag = subjectFromText(mission.instruction)
                mission.copy(
                    proofTag = tag,
                    instruction = mission.instruction + " Upload a clear $tag photo."
                )
            }
        }
    }

    private fun subjectFromText(instruction: String): String {
        val value = instruction.lowercase()
        return when {
            listOf("grass", "lawn", "meadow").any { value.contains(it) } -> "grass"
            listOf("canal", "river", "water", "pond", "reflection", "lake")
                .any { value.contains(it) } -> "water"
            listOf("flower", "petal", "bloom").any { value.contains(it) } -> "flower"
            listOf("tree", "bark", "trunk", "branch").any { value.contains(it) } -> "tree"
            listOf("sky", "cloud", "sunset").any { value.contains(it) } -> "sky"
            else -> "nature"
        }
    }

    fun fallbackMissions(
        lockMode: String, missionMinutes: Int, minimumSteps: Int
    ): List<EscapeMission> = when (lockMode) {
        EscapeKeys.LOCK_MODE_EVENING -> listOf(
            EscapeMission("Tomorrow's Nature Bingo", "On paper, write five nature clues for a family walk tomorrow. Include at least 20 words and photograph the page.", "fallback", "writing"),
            EscapeMission("A Planting Plan", "Write a 20-word plan for growing herbs or a seed at home, with materials and three steps. Photograph the page.", "fallback", "writing"),
            EscapeMission("Nature Memory Letter", "Write a 20-word note about your favourite outdoor moment or walk. Photograph your handwritten note.", "fallback", "writing"),
            EscapeMission("Indoor Nature Detective", "List five natural objects you noticed recently and describe their colours or textures in 20 words. Photograph the page.", "fallback", "writing"),
            EscapeMission("Weekend Park Quest", "Design a three-stop park walk on paper with 20 words describing what to discover at each stop. Photograph the page.", "fallback", "writing")
        )
        EscapeKeys.LOCK_MODE_WEEKEND -> listOf(
            EscapeMission("Grassland Treasure Hunt", "Walk to a safe public green area, spend five calm minutes noticing grass and photograph a patch of grass.", "fallback", "grass"),
            EscapeMission("Cycleway Nature Stop", "Cycle a suitable marked route or stroll instead. Stop somewhere safe and photograph a tree or leaves.", "fallback", "tree"),
            EscapeMission("Canal Reflection Trail", "Walk or cycle safely near a public canal and photograph the reflection from a safe distance from the water.", "fallback", "water"),
            EscapeMission("Green Weekend Bingo", "Take a bike ride or walk to a public park and photograph grass, leaves or a flower.", "fallback", "nature")
        )
        else -> listOf(
            EscapeMission("Touch Grass Detective", "Take a ${missionMinutes}-minute stroll, look for a grassy patch in a public place and photograph the grass.", "fallback", "grass"),
            EscapeMission("Leaf Pattern Explorer", "Walk at least $minimumSteps steps and find patterned leaves on a tree. Photograph them without damaging the plant.", "fallback", "tree"),
            EscapeMission("Water Reflection Quest", "Take a short walk to a safe public waterway or pond and photograph a reflection away from the water's edge.", "fallback", "water"),
            EscapeMission("Sky Colour Safari", "Walk outdoors and notice three shades in the sky. Photograph the clouds or sky from a safe place.", "fallback", "sky"),
            EscapeMission("Flower Trail", "Go for a stroll, find a flower growing in a public area and photograph it without picking it.", "fallback", "flower")
        )
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
