package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedLayoutTest {
    private val rows = listOf(RowShape(false, 20), RowShape(false, 7), RowShape(true, 9), RowShape(false, 3))

    @Test
    fun aRowIsOneItemAndAGridOneItemPerLine() {
        assertEquals(1, itemCount(rows[0], 4))
        assertEquals(3, itemCount(rows[2], 4))
        assertEquals(0, itemCount(RowShape(true, 0), 4))
        assertEquals(2, itemCount(RowShape(true, 8), 4))
    }

    @Test
    fun aCardOfARowIsInTheItemOfItsRow() {
        assertEquals(0, outerIndex(rows, 0, 19, 4))
        assertEquals(1, outerIndex(rows, 1, 6, 4))
    }

    @Test
    fun aCardOfAGridIsInTheItemOfItsLine() {
        assertEquals(2, outerIndex(rows, 2, 0, 4))
        assertEquals(2, outerIndex(rows, 2, 3, 4))
        assertEquals(3, outerIndex(rows, 2, 4, 4))
        assertEquals(4, outerIndex(rows, 2, 8, 4))
    }

    @Test
    fun rowsAfterAGridCountItsLines() {
        assertEquals(5, outerIndex(rows, 3, 1, 4))
    }

    @Test
    fun aGridAloneStartsAtZero() {
        assertEquals(0, outerIndex(listOf(RowShape(true, 10)), 0, 2, 4))
        assertEquals(2, outerIndex(listOf(RowShape(true, 10)), 0, 9, 4))
    }
}
