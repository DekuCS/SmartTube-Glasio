package io.github.ivanosh.stmaterial.ui

import io.github.ivanosh.stmaterial.logic.EditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListEditorKeysTest {
    private fun state(shown: List<String>, hidden: List<String>, limit: Int = 4, picked: String? = null) =
        EditorState(shown, hidden, emptySet(), limit, picked)

    private val defaults = state(listOf("a", "b", "c"), listOf("x", "y"))

    private fun ui(
        shown: List<String> = listOf("a", "b", "c"),
        hidden: List<String> = listOf("x", "y"),
        side: Side = Side.Shown,
        index: Int = 0,
        picked: String? = null,
        limit: Int = 4,
    ) = EditorUi(state(shown, hidden, limit, picked), defaults, EditorCursor(side, index))

    private fun EditorUi<String>.press(vararg keys: EditorKey): KeyResult<String> {
        var result = KeyResult(this)
        for (k in keys) result = editorKey(result.ui, k)
        return result
    }

    @Test
    fun upDownMoveCursorWhenNothingPicked() {
        val r = ui(index = 0).press(EditorKey.Down, EditorKey.Down)
        assertEquals(EditorCursor(Side.Shown, 2), r.ui.cursor)
        assertEquals(listOf("a", "b", "c"), r.ui.state.shown)
        assertEquals(EditorCursor(Side.Shown, 1), r.ui.press(EditorKey.Up).ui.cursor)
    }

    @Test
    fun upStopsAtTop() {
        assertEquals(0, ui(index = 0).press(EditorKey.Up).ui.cursor.index)
    }

    @Test
    fun downFromLastShownReachesResetThenStops() {
        val r = ui(index = 2).press(EditorKey.Down)
        assertEquals(EditorCursor(Side.Shown, 3), r.ui.cursor)
        assertEquals(3, r.ui.press(EditorKey.Down).ui.cursor.index)
    }

    @Test
    fun hiddenListHasNoResetRow() {
        val r = ui(side = Side.Hidden, index = 1).press(EditorKey.Down)
        assertEquals(EditorCursor(Side.Hidden, 1), r.ui.cursor)
    }

    @Test
    fun okPicksAndReleases() {
        val picked = ui(index = 1).press(EditorKey.Ok)
        assertEquals("b", picked.ui.state.picked)
        assertNull(picked.ui.press(EditorKey.Ok).ui.state.picked)
    }

    @Test
    fun upDownReorderPickedItemAndCursorFollows() {
        val r = ui(index = 1).press(EditorKey.Ok, EditorKey.Up)
        assertEquals(listOf("b", "a", "c"), r.ui.state.shown)
        assertEquals(EditorCursor(Side.Shown, 0), r.ui.cursor)
        val d = r.ui.press(EditorKey.Down, EditorKey.Down)
        assertEquals(listOf("a", "c", "b"), d.ui.state.shown)
        assertEquals(2, d.ui.cursor.index)
    }

    @Test
    fun pickedItemStopsAtListEndsAndNeverReachesReset() {
        val r = ui(index = 2).press(EditorKey.Ok, EditorKey.Down)
        assertEquals(listOf("a", "b", "c"), r.ui.state.shown)
        assertEquals(EditorCursor(Side.Shown, 2), r.ui.cursor)
    }

    @Test
    fun leftRightSwitchListWithoutPick() {
        val r = ui(index = 1).press(EditorKey.Right)
        assertEquals(EditorCursor(Side.Hidden, 1), r.ui.cursor)
        assertEquals(listOf("a", "b", "c"), r.ui.state.shown)
        assertEquals(Side.Shown, r.ui.press(EditorKey.Left).ui.cursor.side)
    }

    @Test
    fun switchClampsCursorToTargetList() {
        val r = ui(shown = listOf("a", "b", "c", "d"), hidden = listOf("x"), index = 3).press(EditorKey.Right)
        assertEquals(EditorCursor(Side.Hidden, 0), r.ui.cursor)
    }

    @Test
    fun switchToEmptyListStays() {
        val r = ui(hidden = emptyList(), index = 1).press(EditorKey.Right)
        assertEquals(EditorCursor(Side.Shown, 1), r.ui.cursor)
    }

    @Test
    fun rightCarriesPickedItemToHidden() {
        val r = ui(index = 1).press(EditorKey.Ok, EditorKey.Right)
        assertEquals(listOf("a", "c"), r.ui.state.shown)
        assertEquals(listOf("x", "y", "b"), r.ui.state.hidden)
        assertEquals(EditorCursor(Side.Hidden, 2), r.ui.cursor)
        assertEquals("b", r.ui.state.picked)
    }

    @Test
    fun leftCarriesPickedItemToShown() {
        val r = ui(side = Side.Hidden, index = 0).press(EditorKey.Ok, EditorKey.Left)
        assertEquals(listOf("a", "b", "c", "x"), r.ui.state.shown)
        assertEquals(EditorCursor(Side.Shown, 3), r.ui.cursor)
        assertNull(r.displaced)
    }

    @Test
    fun leftAtLimitDisplacesLastShownAndReportsIt() {
        val r = ui(shown = listOf("a", "b", "c", "d"), side = Side.Hidden, index = 0).press(EditorKey.Ok, EditorKey.Left)
        assertEquals(listOf("a", "b", "c", "x"), r.ui.state.shown)
        assertEquals("d", r.displaced)
        assertEquals(EditorNotice.Displaced, r.notice)
        assertEquals(EditorCursor(Side.Shown, 3), r.ui.cursor)
    }

    @Test
    fun lastShownItemCannotLeave() {
        val r = ui(shown = listOf("a"), index = 0).press(EditorKey.Ok, EditorKey.Right)
        assertEquals(listOf("a"), r.ui.state.shown)
        assertEquals(EditorNotice.KeepOne, r.notice)
        assertEquals(EditorCursor(Side.Shown, 0), r.ui.cursor)
    }

    @Test
    fun pickedItemOnItsOwnSideIgnoresThatDirection() {
        val r = ui(index = 1).press(EditorKey.Ok, EditorKey.Left)
        assertEquals(listOf("a", "b", "c"), r.ui.state.shown)
        assertEquals(Side.Shown, r.ui.cursor.side)
        assertNull(r.notice)
    }

    @Test
    fun okOnResetRestoresDefaultsAndCursorGoesToTop() {
        val reset = ui(shown = listOf("c", "a"), hidden = listOf("b", "x", "y"), index = 1)
            .press(EditorKey.Down, EditorKey.Ok)
        assertEquals(defaults.shown, reset.ui.state.shown)
        assertEquals(defaults.hidden, reset.ui.state.hidden)
        assertNull(reset.ui.state.picked)
        assertEquals(EditorNotice.Reset, reset.notice)
        assertEquals(EditorCursor(Side.Shown, 0), reset.ui.cursor)
    }

    @Test
    fun backFinishesWithPickDropped() {
        val r = ui(index = 1).press(EditorKey.Ok, EditorKey.Up, EditorKey.Back)
        assertTrue(r.done)
        assertNull(r.ui.state.picked)
        assertEquals(listOf("b", "a", "c"), r.ui.state.shown)
    }

    @Test
    fun otherKeysDoNotFinish() {
        assertTrue(!ui().press(EditorKey.Down, EditorKey.Ok).done)
    }
}
