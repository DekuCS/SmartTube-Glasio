package io.github.ivanosh.stmaterial.logic

import com.liskovsoft.smartyoutubetv2.tv.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionRowsTest {
    private fun item(id: String, pct: Int = 0) = VideoItem(id, pct)

    @Test
    fun continueHoldsProgressFromFiveToNinetyFiveInclusive() {
        val history = listOf(item("a", 4), item("b", 5), item("c", 50), item("d", 95), item("e", 96), item("f", 0))
        val rows = buildRows(listOf(item("s")), history)
        assertEquals(listOf("b", "c", "d"), rows.first { it.id == "continue" }.ids)
    }

    @Test
    fun aStartedVideoStaysInNewInItsPlace() {
        val rows = buildRows(listOf(item("x"), item("a"), item("y")), listOf(item("a", 40)))
        assertEquals(listOf("continue", "new"), rows.map { it.id })
        assertEquals(listOf("a"), rows[0].ids)
        assertEquals(listOf("x", "a", "y"), rows[1].ids)
    }

    @Test
    fun aWatchedVideoStaysInNewWhenHistoryRowsDoNotTakeIt() {
        val rows = buildRows(listOf(item("a"), item("b")), listOf(item("a", 100), item("b", 2)))
        assertEquals(listOf("new"), rows.map { it.id })
        assertEquals(listOf("a", "b"), rows[0].ids)
    }

    @Test
    fun emptyHistoryGivesOneRow() {
        val rows = buildRows(listOf(item("a")), emptyList())
        assertEquals(1, rows.size)
        assertEquals("new", rows[0].id)
    }

    @Test
    fun emptyRowsAreNotCreated() {
        assertTrue(buildRows(emptyList(), emptyList()).isEmpty())
        assertEquals(listOf("continue"), buildRows(emptyList(), listOf(item("a", 10))).map { it.id })
        assertEquals(listOf("continue", "new"), buildRows(listOf(item("a")), listOf(item("a", 10))).map { it.id })
    }

    @Test
    fun repeatsInAListCollapseAndOrderIsKept() {
        val rows = buildRows(listOf(item("b"), item("a"), item("b")), listOf(item("c", 10), item("c", 10)))
        assertEquals(listOf("c"), rows[0].ids)
        assertEquals(listOf("b", "a"), rows[1].ids)
    }

    @Test
    fun rowsHaveTheirTitles() {
        val rows = buildRows(listOf(item("a")), listOf(item("c", 10)))
        assertEquals(R.string.material_row_continue, rows[0].titleRes)
        assertEquals(R.string.material_row_new, rows[1].titleRes)
    }
}
