package com.scrollmate.core.progress

import java.time.Duration

data class Progress(
    val used: Duration,
    val limit: Duration,
    /** used / limit, not capped (1.25 means 125 %). */
    val fraction: Double,
    /** Floor of fraction * 100, not capped. */
    val percent: Int,
    /** Time left until the limit, never negative. */
    val remaining: Duration,
    /** Time beyond the limit, never negative. */
    val overBy: Duration,
) {
    val limitReached: Boolean get() = used >= limit
    val isOverLimit: Boolean get() = !overBy.isZero

    /** Fraction for drawing a ring, capped at 1.0. */
    val ringFraction: Float get() = fraction.coerceIn(0.0, 1.0).toFloat()
}

object ProgressCalculator {

    fun calculate(used: Duration, limit: Duration): Progress {
        require(!limit.isNegative && !limit.isZero) { "limit must be positive" }
        val safeUsed = if (used.isNegative) Duration.ZERO else used
        val fraction = safeUsed.toMillis().toDouble() / limit.toMillis().toDouble()
        val percent = Math.floor(fraction * 100.0 + 1e-9).toInt()
        val remaining = if (safeUsed >= limit) Duration.ZERO else limit.minus(safeUsed)
        val overBy = if (safeUsed > limit) safeUsed.minus(limit) else Duration.ZERO
        return Progress(safeUsed, limit, fraction, percent, remaining, overBy)
    }
}
