package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CopyProgressTest {
    @Test fun percentIsWhole() {
        assertEquals(0, percentOf(0, 100))
        assertEquals(42, percentOf(42, 100))
        assertEquals(42, percentOf(4_299, 10_000))
        assertEquals(100, percentOf(100, 100))
    }

    @Test fun percentIsClamped() {
        assertEquals(100, percentOf(150, 100))
        assertEquals(0, percentOf(-5, 100))
    }

    @Test fun anUnknownTotalIsZero() {
        assertEquals(0, percentOf(50, 0))
        assertEquals(0, percentOf(50, -1))
    }

    @Test fun largeFilesDoNotOverflow() {
        assertEquals(50, percentOf(Long.MAX_VALUE / 2, Long.MAX_VALUE))
    }

    @Test fun theGateReportsOnlyAChange() {
        val gate = PercentGate()
        assertEquals(0, gate.next(0, 1000))
        assertNull(gate.next(5, 1000))
        assertEquals(1, gate.next(10, 1000))
        assertNull(gate.next(19, 1000))
        assertEquals(2, gate.next(20, 1000))
    }

    @Test fun aWholeCopyPostsAtMostOncePerPercent() {
        val gate = PercentGate()
        val total = 78L * 1024 * 1024
        var posts = 0
        var done = 0L
        while (done < total) {
            done = minOf(total, done + 64 * 1024)
            if (gate.next(done, total) != null) posts++
        }
        // 0 (the first chunk is under 1%) through 100.
        assertEquals(101, posts)
    }
}
