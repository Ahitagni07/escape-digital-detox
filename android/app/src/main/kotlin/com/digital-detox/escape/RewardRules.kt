package com.example.escape

internal object RewardRules {
    fun safeAccessSeconds(requestedSeconds: Int): Int = maxOf(60, requestedSeconds)

    fun displayMinutes(accessSeconds: Int): Int =
        maxOf(1, ((accessSeconds.toLong() + 59L) / 60L).toInt())
}
