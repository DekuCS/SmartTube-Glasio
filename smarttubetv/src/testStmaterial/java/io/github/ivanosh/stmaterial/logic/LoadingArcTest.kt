package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadingArcTest {
    private fun wrapped(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    @Test
    fun theTailAndTheStartAreContinuousAcrossEveryCycleBoundary() {
        val e = 1e-4f
        for (cycle in 1..CYCLES_PER_TURN) {
            val t = cycle / CYCLES_PER_TURN.toFloat()
            val before = t - e
            val after = if (cycle == CYCLES_PER_TURN) e else t + e
            assertEquals("start at cycle $cycle", 0f, wrapped(arcStart(before), arcStart(after)), 0.5f)
            assertEquals("sweep at cycle $cycle", arcSweep(before), arcSweep(after), 0.5f)
        }
    }

    @Test
    fun theArcGrowsAndShrinksWithinTheLimits() {
        var min = Float.MAX_VALUE
        var max = 0f
        for (i in 0..1000) {
            val sweep = arcSweep(i / 1000f)
            assertTrue(sweep >= 0f && sweep <= ARC_SPAN + 0.01f)
            min = minOf(min, sweep)
            max = maxOf(max, sweep)
        }
        assertTrue(min < 1f)
        assertTrue(max > ARC_SPAN - 1f)
    }

    @Test
    fun noFrameStepsByMoreThanTheSpeedOfTheMotionAllows() {
        // 6660 ms for a turn, 60 frames a second: no step may be a jump.
        var last = arcStart(0f)
        for (i in 1..400) {
            val now = arcStart(i / 400f)
            assertTrue("step at $i", Math.abs(wrapped(now, last)) < 25f)
            last = now
        }
    }
}
