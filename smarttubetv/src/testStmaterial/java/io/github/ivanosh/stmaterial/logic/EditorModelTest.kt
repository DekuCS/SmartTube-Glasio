package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorModelTest {
    private fun state(
        shown: List<String>,
        hidden: List<String> = emptyList(),
        locked: Set<String> = emptySet(),
        limit: Int = 10,
        picked: String? = null
    ) = EditorState(shown, hidden, locked, limit, picked)

    @Test
    fun pickTogglesSelection() {
        val s = state(listOf("a", "b"))
        assertEquals("a", s.pick("a").picked)
        assertEquals("b", s.pick("a").pick("b").picked)
        assertNull(s.pick("a").pick("a").picked)
    }

    @Test
    fun moveByReordersAndKeepsPick() {
        val s = state(listOf("a", "b", "c"), picked = "a")
        val moved = s.moveBy(1)
        assertEquals(listOf("b", "a", "c"), moved.shown)
        assertEquals("a", moved.picked)
        assertEquals(listOf("a", "b", "c"), moved.moveBy(-1).shown)
    }

    @Test
    fun moveByStopsAtBounds() {
        val first = state(listOf("a", "b", "c"), picked = "a")
        assertEquals(listOf("a", "b", "c"), first.moveBy(-1).shown)
        assertEquals(listOf("b", "c", "a"), first.moveBy(5).shown)
        val last = state(listOf("a", "b", "c"), picked = "c")
        assertEquals(listOf("a", "b", "c"), last.moveBy(1).shown)
    }

    @Test
    fun moveByStopsBeforeLockedItem() {
        val s = state(listOf("a", "b", "c"), locked = setOf("c"), picked = "a")
        assertEquals(listOf("b", "a", "c"), s.moveBy(5).shown)
    }

    @Test
    fun moveByWithoutPickIsNoOp() {
        val s = state(listOf("a", "b"))
        assertEquals(s, s.moveBy(1))
    }

    @Test
    fun toggleListHidesShownItem() {
        val s = state(listOf("a", "b", "c"), hidden = listOf("x"), picked = "b")
        val r = s.toggleList()
        assertEquals(listOf("a", "c"), r.state.shown)
        assertEquals(listOf("x", "b"), r.state.hidden)
        assertNull(r.displaced)
    }

    @Test
    fun cannotHideLastShown() {
        val s = state(listOf("a"), picked = "a")
        val r = s.toggleList()
        assertEquals(s, r.state)
        assertNull(r.displaced)
    }

    @Test
    fun cannotHideLocked() {
        val s = state(listOf("a", "b"), locked = setOf("a"), picked = "a")
        assertEquals(s, s.toggleList().state)
    }

    @Test
    fun showsHiddenItemAtEnd() {
        val s = state(listOf("a"), hidden = listOf("x", "y"), picked = "y")
        val r = s.toggleList()
        assertEquals(listOf("a", "y"), r.state.shown)
        assertEquals(listOf("x"), r.state.hidden)
        assertNull(r.displaced)
    }

    @Test
    fun atLimitDisplacesLastNonLockedToFrontOfHidden() {
        val s = state(listOf("a", "b", "c"), hidden = listOf("x", "y"),
            locked = setOf("c"), limit = 3, picked = "y")
        val r = s.toggleList()
        assertEquals("b", r.displaced)
        assertEquals(listOf("a", "c", "y"), r.state.shown)
        assertEquals(listOf("b", "x"), r.state.hidden)
        assertEquals(3, r.state.shown.size)
    }

    @Test
    fun atLimitWithAllLockedIsNoOp() {
        val s = state(listOf("a", "b"), hidden = listOf("x"),
            locked = setOf("a", "b"), limit = 2, picked = "x")
        val r = s.toggleList()
        assertEquals(s, r.state)
        assertNull(r.displaced)
    }

    @Test
    fun toggleListWithoutPickIsNoOp() {
        val s = state(listOf("a", "b"))
        assertEquals(s, s.toggleList().state)
    }

    @Test
    fun resetRestoresDefaultsAndClearsPick() {
        val defaults = state(listOf("a", "b"), hidden = listOf("c"))
        val s = state(listOf("c"), hidden = listOf("a", "b"), picked = "c")
        assertEquals(defaults, s.reset(defaults))
        assertNull(s.reset(defaults.copy(picked = "a")).picked)
    }
}
