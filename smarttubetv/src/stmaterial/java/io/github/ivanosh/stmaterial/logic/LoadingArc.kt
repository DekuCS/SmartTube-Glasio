package io.github.ivanosh.stmaterial.logic

import androidx.compose.animation.core.CubicBezierEasing

// The angles of the Material 3 indeterminate circular indicator, from one clock of 0..1 over a turn of five cycles.
// In a cycle the head runs ahead by up to 290 degrees, then the tail catches up. The next cycle starts where the
// tail ended, so the start steps by 290 degrees per cycle (a stepped offset, not a continuous one) and the base
// rotation covers the rest of a full turn: 5 x 290 + 350 is 1800, five full turns, so the clock wraps seamlessly.
const val CYCLES_PER_TURN = 5
const val ARC_SPAN = 290f
private const val START_OFFSET = -90f
private const val BASE_PER_TURN = 350f
private val CircularEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private fun head(phase: Float) = CircularEasing.transform((phase * 2f).coerceAtMost(1f)) * ARC_SPAN
private fun tail(phase: Float) = CircularEasing.transform(((phase - 0.5f) * 2f).coerceAtLeast(0f)) * ARC_SPAN

/** Where the arc starts, in degrees from the 3 o'clock position, for a [clock] of 0..1. Not wrapped to 360. */
fun arcStart(clock: Float): Float {
    val position = clock * CYCLES_PER_TURN
    val cycle = position.toInt()
    return START_OFFSET + clock * BASE_PER_TURN + cycle * ARC_SPAN + tail(position - cycle)
}

/** How far the arc reaches from its start, in degrees. */
fun arcSweep(clock: Float): Float {
    val position = clock * CYCLES_PER_TURN
    val phase = position - position.toInt()
    return head(phase) - tail(phase)
}
