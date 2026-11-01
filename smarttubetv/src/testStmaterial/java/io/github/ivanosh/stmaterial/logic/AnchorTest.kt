package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class AnchorTest {
    // Measured on the emulator: a move to the next card of a row asks for +6 px, the scale of 1.04 of a 313 px card.
    @Test
    fun smallRequestOfAnIdleColumnIsIgnored() {
        assertEquals(0f, anchorDistance(offset = 80f, target = 74f, scrolling = false, tolerance = 16f), 0f)
        assertEquals(0f, anchorDistance(offset = 68f, target = 74f, scrolling = false, tolerance = 16f), 0f)
    }

    @Test
    fun aRowStepAnchors() {
        assertEquals(415f, anchorDistance(489f, 74f, scrolling = false, tolerance = 16f), 0f)
        assertEquals(-413f, anchorDistance(-339f, 74f, scrolling = false, tolerance = 16f), 0f)
    }

    @Test
    fun aMovingColumnFinishesTheAnchor() {
        assertEquals(3.5f, anchorDistance(77.5f, 74f, scrolling = true, tolerance = 16f), 0f)
    }

    @Test
    fun theEdgeOfTheToleranceIsIgnored() {
        assertEquals(0f, anchorDistance(90f, 74f, scrolling = false, tolerance = 16f), 0f)
        assertEquals(17f, anchorDistance(91f, 74f, scrolling = false, tolerance = 16f), 0f)
    }
}
