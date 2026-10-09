package com.example.escape

internal data class WrittenProofCheck(
    val hasCode: Boolean,
    val enoughWriting: Boolean
) {
    val passed: Boolean
        get() = hasCode && enoughWriting
}

internal object MissionProofRules {
    private val expectedLabels = mapOf(
        "grass" to listOf("grass", "lawn", "meadow", "vegetation", "field", "plant", "greenery"),
        "tree" to listOf("tree", "plant", "leaf", "forest", "wood", "branch"),
        "water" to listOf("water", "lake", "river", "canal", "pond", "sea", "reflection"),
        "sky" to listOf("sky", "cloud", "sunset", "sunrise"),
        "flower" to listOf("flower", "plant", "petal", "garden"),
        "nature" to listOf(
            "tree", "plant", "grass", "leaf", "flower", "sky",
            "cloud", "landscape", "forest", "garden", "river", "water", "outdoor"
        )
    )

    fun checkWrittenProof(text: String, code: String): WrittenProofCheck {
        val normalized = text.uppercase().replace(Regex("[^A-Z0-9]+"), " ").trim()
        val codeWords = code.uppercase().split(" ").filter { it.isNotBlank() }
        val hasCode = codeWords.size == 2 &&
            codeWords.all {
                Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(normalized)
            }
        val wordCount = normalized.split(Regex("\\s+")).count { it.length >= 2 }
        return WrittenProofCheck(hasCode, wordCount >= 20)
    }

    fun isSupportedNatureSubject(tag: String): Boolean = tag in expectedLabels

    fun matchesNatureSubject(tag: String, labels: List<String>): Boolean? {
        val expected = expectedLabels[tag] ?: return null
        return labels.any { label ->
            expected.any { keyword -> label.contains(keyword) }
        }
    }
}
