package com.example.escape

import org.junit.Assert.assertEquals
import org.junit.Test

class RewardRulesTest {
    @Test
    fun accessDurationHasAMinimumOfOneMinute() {
        assertEquals(60, RewardRules.safeAccessSeconds(0))
        assertEquals(60, RewardRules.safeAccessSeconds(-30))
        assertEquals(60, RewardRules.safeAccessSeconds(60))
    }

    @Test
    fun rewardMinutesRoundUpToCoverTheGrantedDuration() {
        assertEquals(1, RewardRules.displayMinutes(60))
        assertEquals(2, RewardRules.displayMinutes(61))
        assertEquals(2, RewardRules.displayMinutes(120))
        assertEquals(3, RewardRules.displayMinutes(121))
    }
}
