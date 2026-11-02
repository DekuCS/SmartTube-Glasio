package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipUndoTest {
    private val a = SkipSpan(60_000, 90_000)
    private val b = SkipSpan(92_000, 120_000)
    private val far = SkipSpan(300_000, 330_000)

    @Test
    fun aFreshSegmentMaySkip() {
        assertTrue(SkipLedger().maySkip(a, 0))
    }

    @Test
    fun theChipNamesTheSkipAndUndoGoesToItsStart() {
        val ledger = SkipLedger()
        val chip = ledger.onAutoSkip(listOf(a), 1_000)
        assertEquals(SkipChip(60_000, a, 1_000), chip)
        assertEquals(chip, ledger.chip(2_000))
        assertEquals(60_000L, ledger.undo(2_000))
        assertNull(ledger.chip(2_000))
    }

    @Test
    fun theChipEndsAfterTheWindow() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(a), 1_000)
        assertNotNullAt(ledger, 1_000 + SKIP_CHIP_MS - 1)
        assertNull(ledger.chip(1_000 + SKIP_CHIP_MS))
        assertNull(ledger.undo(1_000 + SKIP_CHIP_MS))
    }

    private fun assertNotNullAt(ledger: SkipLedger, now: Long) = assertTrue(ledger.chip(now) != null)

    @Test
    fun undoWithoutAChipDoesNothing() {
        assertNull(SkipLedger().undo(0))
    }

    @Test
    fun anUndoneSegmentNeverSkipsAgainForThisVideo() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(a), 0)
        ledger.undo(1_000)
        assertFalse(ledger.maySkip(a, 1_100))
        assertFalse(ledger.maySkip(a, 10 * 60_000))
        assertTrue(ledger.maySkip(far, 1_100))
    }

    @Test
    fun aRewindIntoAJustSkippedSegmentPlaysIt() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(a), 0)
        assertFalse(ledger.maySkip(a, SKIP_CHIP_MS - 1))
    }

    @Test
    fun aSegmentAutoSkipsOncePerOpenHoweverTheOwnerGotBackToIt() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(a), 0)
        assertFalse(ledger.maySkip(a, SKIP_CHIP_MS))
        assertFalse(ledger.maySkip(a, 60 * 60_000))
        assertTrue(ledger.maySkip(far, SKIP_CHIP_MS))
    }

    @Test
    fun theCategoryDoesNotTellSegmentsApart() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(SkipSpan(60_000, 90_000, "sponsor")), 0)
        assertFalse(ledger.maySkip(SkipSpan(60_000, 90_000, "selfpromo"), 10))
    }

    @Test
    fun aSkipByTheOwnerDoesNotUseUpTheAutoSkip() {
        val ledger = SkipLedger()
        val chip = ledger.onAutoSkip(listOf(a), 0, auto = false)
        assertEquals(60_000L, chip?.chainStartMs)
        assertTrue(ledger.maySkip(a, SKIP_CHIP_MS))
    }

    @Test
    fun aChainInOneJumpNamesTheLastAndUndoGoesToTheFirstStart() {
        val ledger = SkipLedger()
        val chip = ledger.onAutoSkip(listOf(a, b), 0)
        assertEquals(b, chip?.last)
        assertEquals(60_000L, ledger.undo(10))
        assertEquals(listOf(a, b), ledger.lastUndone)
        assertFalse(ledger.maySkip(a, 20))
        assertFalse(ledger.maySkip(b, 20))
    }

    @Test
    fun aSecondSkipWhileTheChipIsLiveJoinsAnAdjacentChain() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(a), 0)
        val chip = ledger.onAutoSkip(listOf(b), 1_000)
        assertEquals(SkipChip(60_000, b, 1_000), chip)
        assertEquals(60_000L, ledger.undo(1_500))
        assertFalse(ledger.maySkip(a, 9_000))
        assertFalse(ledger.maySkip(b, 9_000))
    }

    @Test
    fun aDistantSkipStartsANewChain() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(a), 0)
        val chip = ledger.onAutoSkip(listOf(far), 1_000)
        assertEquals(300_000L, chip?.chainStartMs)
        assertEquals(300_000L, ledger.undo(1_200))
        assertFalse(ledger.maySkip(far, 9_000))
    }

    @Test
    fun aSkipAfterTheChipEndedStartsANewChain() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(a), 0)
        val chip = ledger.onAutoSkip(listOf(b), SKIP_CHIP_MS + 1)
        assertEquals(92_000L, chip?.chainStartMs)
    }

    @Test
    fun clearForgetsEverything() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(a), 0)
        ledger.undo(10)
        ledger.clear()
        assertTrue(ledger.maySkip(a, 20))
        assertNull(ledger.chip(20))
    }

    @Test
    fun undoingASkipMadeByRightPlaysTheSegmentThrough() {
        val ledger = SkipLedger()
        ledger.onAutoSkip(listOf(a), 0, auto = false)
        assertTrue(ledger.maySkip(a, 1))
        assertEquals(60_000L, ledger.undo(10))
        assertFalse(ledger.maySkip(a, 20))
    }

    @Test
    fun rightIsTheChipOnlyWithNoSeekPending() {
        assertTrue(chipKeyMayAct(SeekDebounce()))
        assertFalse(chipKeyMayAct(SeekDebounce().onKey(10_000, 10_000, 100_000, 0)))
    }
}
