package io.github.ivanosh.stmaterial.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogStateTest {
    private fun radio(vararg selected: Boolean) = selected.mapIndexed { i, s ->
        ChoiceRow(key = i, title = "r$i", description = null, selected = s, multi = false)
    }

    private fun checks(vararg selected: Boolean) = selected.mapIndexed { i, s ->
        ChoiceRow(key = i, title = "c$i", description = null, selected = s, multi = true)
    }

    private fun DialogState.selected() = page!!.rows.filterIsInstance<ChoiceRow>().filter { it.selected }.map { it.key }

    @Test
    fun `radio keeps exactly one selected`() {
        val state = DialogState()
        state.push(DialogPage.of("Speed", radio(true, false, false)))
        state.choose(2)
        assertEquals(listOf(2), state.selected())
        state.choose(2)
        assertEquals(listOf(2), state.selected())
        state.choose(0)
        assertEquals(listOf(0), state.selected())
    }

    @Test
    fun `checkbox toggles one item and leaves the others`() {
        val state = DialogState()
        state.push(DialogPage.of("Tweaks", checks(true, false, true)))
        state.toggle(1)
        assertEquals(listOf(0, 1, 2), state.selected())
        state.toggle(0)
        assertEquals(listOf(1, 2), state.selected())
    }

    @Test
    fun `a switch toggles`() {
        val state = DialogState()
        state.push(DialogPage.of(null, listOf(SwitchRow(7, "Loop", false, null))))
        state.toggle(7)
        assertTrue((state.page!!.rows.single() as SwitchRow).selected)
    }

    @Test
    fun `back returns to the previous page`() {
        val state = DialogState()
        state.push(DialogPage.of("Root", radio(false)))
        state.push(DialogPage.of("Sub", radio(true)), openedFrom = 0)
        assertTrue(state.canGoBack)
        assertEquals("Sub", state.page!!.title)
        assertTrue(state.pop())
        assertEquals("Root", state.page!!.title)
        assertFalse(state.canGoBack)
        assertFalse(state.pop())
        assertEquals("Root", state.page!!.title)
    }

    @Test
    fun `clearing the stack leaves nothing`() {
        val state = DialogState()
        state.push(DialogPage.of("A", emptyList()))
        state.push(DialogPage.of("B", emptyList()))
        state.clear()
        assertNull(state.page)
        assertFalse(state.canGoBack)
    }

    @Test
    fun `an empty category is not shown`() {
        val full = EntryRow(1, "Full", size = 2, open = { DialogPage.of("Full", radio(false, false)) })
        val empty = EntryRow(2, "Empty", size = 0, open = { DialogPage.of("Empty", emptyList()) })
        val page = DialogPage.of("Settings", listOf(empty, full))
        assertEquals(listOf(1), page.rows.map { it.key })
    }

    @Test
    fun `opening an entry pushes its page and back returns focus to the entry`() {
        val state = DialogState()
        val entry = EntryRow(5, "Quality", size = 1, open = { DialogPage.of("Quality", radio(true)) })
        state.push(DialogPage.of("Root", listOf(SwitchRow(1, "a", false, null), entry)))
        state.open(entry)
        assertEquals("Quality", state.page!!.title)
        assertEquals(0, state.initialFocus())
        state.pop()
        assertEquals(5, state.initialFocus())
    }

    @Test
    fun `focus starts on the selected item or else the first row`() {
        val state = DialogState()
        state.push(DialogPage.of("Speed", radio(false, false, true, false)))
        assertEquals(2, state.initialFocus())
        state.push(DialogPage.of("Plain", radio(false, false)))
        assertEquals(0, state.initialFocus())
    }

    @Test
    fun `sync rereads the selection of the items`() {
        val state = DialogState()
        state.push(DialogPage.of("Tweaks", checks(true, true)))
        state.sync { row -> row.key == 1 }
        assertEquals(listOf(1), state.selected())
    }

    @Test
    fun `sync leaves a radio list as chosen`() {
        val state = DialogState()
        state.push(DialogPage.of("Speed", radio(true, false)))
        state.choose(1)
        state.sync { true }
        assertEquals(listOf(1), state.selected())
    }

    @Test
    fun `pages get distinct ids and keep theirs when the selection changes`() {
        val state = DialogState()
        state.push(DialogPage.of("A", radio(true, false)))
        val first = state.page!!.id
        state.choose(1)
        assertEquals(first, state.page!!.id)
        state.push(DialogPage.of("B", radio(true)))
        assertTrue(state.page!!.id != first)
    }

    @Test
    fun `replace swaps the lines of the top page under the same id and keeps the one below with its focus`() {
        val state = DialogState()
        val entry = EntryRow(7, "Account", 1) { DialogPage.of("Account", listOf(ButtonRow(1, "Sign in", null))) }
        state.push(DialogPage.of("Settings", listOf(entry)))
        state.open(entry)
        val before = state.page!!.id
        state.replace(DialogPage.of("Account", listOf(ButtonRow(2, "Sign out", null))))
        assertEquals(listOf(2), state.page!!.rows.map { it.key })
        assertEquals(2, state.depth)
        assertEquals(before, state.page!!.id)
        assertTrue(state.pop())
        assertEquals(7, state.initialFocus())
    }

    @Test
    fun `replace on an empty dialog just shows the page`() {
        val state = DialogState()
        state.replace(DialogPage.of("Blocked", listOf(TextRow(1, "none"))))
        assertEquals(1, state.depth)
        assertEquals("Blocked", state.page!!.title)
    }
}
