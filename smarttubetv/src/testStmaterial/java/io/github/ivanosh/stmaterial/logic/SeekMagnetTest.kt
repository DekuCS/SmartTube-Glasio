package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekMagnetTest {
    private val starts = listOf(0L, 100_000L, 200_000L, 300_000L)
    private val duration = 600_000L

    private fun key(from: SeekDebounce, position: Long, delta: Long, repeating: Boolean = false, chapters: List<Long> = starts) =
        from.onKey(position, delta, duration, 0, chapters, repeating)

    @Test
    fun aStepForwardAcrossAStartStopsOnIt() {
        assertEquals(200_000L, key(SeekDebounce(), 195_000, 10_000).targetMs)
    }

    @Test
    fun aStepBackAcrossAStartStopsOnIt() {
        assertEquals(200_000L, key(SeekDebounce(), 205_000, -10_000).targetMs)
    }

    @Test
    fun theNextStepGoesOnFromTheStart() {
        val first = key(SeekDebounce(), 195_000, 10_000)
        assertEquals(210_000L, key(first, 195_000, 10_000).targetMs)
        val back = key(SeekDebounce(), 205_000, -10_000)
        assertEquals(190_000L, key(back, 205_000, -10_000).targetMs)
    }

    @Test
    fun landingExactlyOnAStartIsJustThatStart() {
        assertEquals(200_000L, key(SeekDebounce(), 190_000, 10_000).targetMs)
        assertEquals(200_000L, key(SeekDebounce(), 210_000, -10_000).targetMs)
    }

    @Test
    fun severalStartsInOneStepStopOnTheNearest() {
        val close = listOf(0L, 100_000L, 104_000L, 108_000L)
        assertEquals(100_000L, key(SeekDebounce(), 95_000, 30_000, chapters = close).targetMs)
        assertEquals(108_000L, key(SeekDebounce(), 125_000, -30_000, chapters = close).targetMs)
    }

    @Test
    fun noChaptersIsAPlainStep() {
        assertEquals(205_000L, key(SeekDebounce(), 195_000, 10_000, chapters = emptyList()).targetMs)
        assertEquals(185_000L, key(SeekDebounce(), 195_000, -10_000, chapters = emptyList()).targetMs)
    }

    @Test
    fun aRepeatOfAHeldKeyIsNotStopped() {
        val first = key(SeekDebounce(), 195_000, 10_000)
        assertEquals(200_000L, first.targetMs)
        val repeated = key(first, 195_000, 10_000, repeating = true)
        assertEquals(210_000L, repeated.targetMs)
        // The hold goes through the next start too.
        assertEquals(305_000L, key(SeekDebounce(targetMs = 255_000), 255_000, 50_000, repeating = true).targetMs)
    }

    @Test
    fun aStartAtZeroIsReachedByTheClampAndNeverTrapsAStepBack() {
        assertEquals(0L, key(SeekDebounce(), 4_000, -10_000).targetMs)
        assertEquals(0L, key(SeekDebounce(), 0, -10_000).targetMs)
        // Standing on a start, a step back does not stop on the same start.
        assertEquals(90_000L, key(SeekDebounce(), 100_000, -10_000).targetMs)
        assertEquals(110_000L, key(SeekDebounce(), 100_000, 10_000).targetMs)
    }

    @Test
    fun aStartPastTheEndIsIgnored() {
        assertEquals(600_000L, key(SeekDebounce(), 595_000, 10_000, chapters = listOf(0L, 700_000L)).targetMs)
    }
}
