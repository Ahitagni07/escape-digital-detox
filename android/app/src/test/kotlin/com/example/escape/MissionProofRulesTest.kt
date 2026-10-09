package com.example.escape

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionProofRulesTest {
    @Test
    fun writtenProofRequiresBothCodeWordsAndAtLeastTwentyReadableWords() {
        val text = "PINE CLOUD " + (1..18).joinToString(" ") { "word$it" }

        assertTrue(MissionProofRules.checkWrittenProof(text, "PINE CLOUD").passed)
        assertFalse(MissionProofRules.checkWrittenProof(text, "PINE RIVER").hasCode)
        assertFalse(
            MissionProofRules.checkWrittenProof(
                "PINE CLOUD " + (1..17).joinToString(" ") { "word$it" },
                "PINE CLOUD"
            ).enoughWriting
        )
    }

    @Test
    fun writtenProofRequiresWholeCodeWords() {
        val text = "PINECLOUD " + (1..20).joinToString(" ") { "word$it" }

        assertFalse(MissionProofRules.checkWrittenProof(text, "PINE CLOUD").hasCode)
    }

    @Test
    fun natureProofMatchesExpectedSubjectLabels() {
        assertEquals(true, MissionProofRules.matchesNatureSubject("water", listOf("river")))
        assertEquals(false, MissionProofRules.matchesNatureSubject("water", listOf("building")))
        assertNull(MissionProofRules.matchesNatureSubject("unknown", listOf("river")))
    }
}
