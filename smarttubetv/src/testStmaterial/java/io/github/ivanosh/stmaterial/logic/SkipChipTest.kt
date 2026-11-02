package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkipChipTest {
    private val sponsor = SkipSpan(60_000, 90_000, "sponsor")
    private val promo = SkipSpan(92_000, 120_000, "selfpromo")
    private val intro = SkipSpan(300_000, 330_000, "intro")
    private val highlight = SkipSpan(500_000, 500_000, HIGHLIGHT_CATEGORY)
    private val all = listOf(intro, promo, sponsor, highlight)

    @Test
    fun noChipOutsideASegment() {
        assertNull(skipTargetAt(10_000, all))
        assertNull(skipTargetAt(90_001, listOf(sponsor)))
    }

    @Test
    fun insideASegmentItIsTheTargetWhateverItsCategory() {
        assertEquals(listOf(intro), skipTargetAt(310_000, all)?.chain)
        assertEquals(sponsor, skipTargetAt(60_000, all)?.first)
    }

    @Test
    fun theTargetEndsWhereTheChainOfAdjacentSegmentsEnds() {
        val target = skipTargetAt(70_000, all)!!
        assertEquals(listOf(sponsor, promo), target.chain)
        assertEquals(120_000L, target.endMs)
    }

    @Test
    fun theChainDoesNotLookBackward() {
        assertEquals(listOf(promo), skipTargetAt(100_000, all)?.chain)
    }

    @Test
    fun aSegmentFarAfterTheEndIsNotInTheChain() {
        assertEquals(listOf(sponsor), skipTargetAt(70_000, listOf(sponsor, intro))?.chain)
    }

    @Test
    fun aHighlightAndAnEmptySegmentGiveNoChip() {
        assertNull(skipTargetAt(500_000, all))
        assertNull(skipTargetAt(5, listOf(SkipSpan(5, 5, "sponsor"))))
    }

    @Test
    fun theUndoChipWinsWhileItLives() {
        val undo = SkipChip(60_000, sponsor, 0)
        val target = skipTargetAt(61_000, all)
        assertEquals(ChipChoice.Undo(undo), chooseChip(undo, target))
        assertEquals(ChipChoice.Skip(target!!), chooseChip(null, target))
        assertNull(chooseChip(null, null))
    }

    @Test
    fun rightJumpsToTheEndOnlyWhileInsideTheTarget() {
        val target = skipTargetAt(70_000, all)
        assertEquals(120_000L, skipJumpTo(target, 70_000))
        assertEquals(120_000L, skipJumpTo(target, 60_000))
        assertNull(skipJumpTo(target, 59_999))
        assertNull(skipJumpTo(target, 120_000))
        assertNull(skipJumpTo(null, 70_000))
    }
}
