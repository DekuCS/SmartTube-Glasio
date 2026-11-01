package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackGeometryTest {
    private val width = 1000f
    private val gap = 4f
    private val handle = 4f
    private val clear = 6f
    private val out = FloatArray(trackBufferSize(8))

    private fun pieces(progress: Float, vararg starts: Float): List<TrackPiece> {
        val n = layoutTrack(width, progress, floatArrayOf(*starts), starts.size, gap, handle, clear, out)
        return (0 until n).map { pieceAt(out, it) }
    }

    @Test
    fun plainBarSplitsAtTheHandle() {
        val p = pieces(0.5f)
        assertEquals(2, p.size)
        assertEquals(TrackPiece(0f, 500f - 2f - 6f, active = true, leftFull = true, rightFull = true), p[0])
        assertEquals(TrackPiece(500f + 2f + 6f, 1000f, active = false, leftFull = true, rightFull = true), p[1])
    }

    @Test
    fun atTheStartTheHandleStaysOnTheTrack() {
        val p = pieces(0f)
        assertEquals(1, p.size)
        assertFalse(p[0].active)
        assertEquals(handle + clear, p[0].left, 0f)
    }

    @Test
    fun atTheEndTheHandleStaysOnTheTrack() {
        val p = pieces(1f)
        assertEquals(1, p.size)
        assertTrue(p[0].active)
        assertEquals(width - handle - clear, p[0].right, 0f)
    }

    @Test
    fun chapterGapsHaveSmallEndsAndTheBarEndsAreFull() {
        val p = pieces(0.25f, 0.5f)
        assertEquals(3, p.size)
        // The inactive part up to the gap: full at the handle, small at the gap.
        assertEquals(TrackPiece(250f + 2f + 6f, 500f - 2f, active = false, leftFull = true, rightFull = false), p[1])
        assertEquals(TrackPiece(500f + 2f, 1000f, active = false, leftFull = false, rightFull = true), p[2])
    }

    @Test
    fun aGapUnderTheHandleIsSwallowedByItsClearance() {
        val p = pieces(0.5f, 0.5f)
        assertEquals(2, p.size)
        assertTrue(p[0].active && p[0].rightFull)
        assertTrue(!p[1].active && p[1].leftFull)
    }

    @Test
    fun segmentsShorterThanTheGapAreDropped() {
        val p = pieces(0.9f, 0.1f, 0.1005f)
        assertTrue(p.all { it.right > it.left })
        assertTrue(p.all { it.left >= 0f && it.right <= width })
    }

    @Test
    fun aCutAtTheHandleIsFullEvenInsideAChapter() {
        val p = pieces(0.75f, 0.5f)
        assertEquals(TrackPiece(500f + 2f, 750f - 2f - 6f, active = true, leftFull = false, rightFull = true), p[1])
    }

    @Test
    fun noPieceLeavesTheBarForAnyProgress() {
        for (progress in listOf(0f, 0.001f, 0.5f, 0.999f, 1f)) {
            pieces(progress, 0.3f, 0.6f).forEach { assertTrue("$progress $it", it.left >= 0f && it.right <= width && it.right > it.left) }
        }
    }
}
