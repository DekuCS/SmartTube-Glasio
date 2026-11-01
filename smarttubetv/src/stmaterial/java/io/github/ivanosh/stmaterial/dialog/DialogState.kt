package io.github.ivanosh.stmaterial.dialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One line of a dialog page. [key] is unique inside its page. [ref] is whatever the owner needs to act on the line. */
sealed interface DialogRow {
    val key: Int
    val title: String
}

/** A line of a radio list ([multi] false) or of a checkbox list. */
data class ChoiceRow(
    override val key: Int,
    override val title: String,
    val description: String?,
    val selected: Boolean,
    val multi: Boolean,
    val ref: Any? = null,
) : DialogRow

data class SwitchRow(override val key: Int, override val title: String, val selected: Boolean, val ref: Any?) : DialogRow

/** A line that runs something when chosen. */
data class ButtonRow(override val key: Int, override val title: String, val ref: Any?) : DialogRow

/** Text to read: a line of a long text, or a kind of item this dialog does not support. */
data class TextRow(override val key: Int, override val title: String) : DialogRow

/** A line that opens a page of [size] lines, built by [open] when it is chosen so that it shows the current state. */
class EntryRow(override val key: Int, override val title: String, val size: Int, val open: () -> DialogPage) : DialogRow

class DialogPage private constructor(val id: Int, val title: String?, val rows: List<DialogRow>) {
    fun withRows(rows: List<DialogRow>) = DialogPage(id, title, rows)
    internal fun withId(id: Int) = DialogPage(id, title, rows)

    companion object {
        /** A page without the entries that open nothing. */
        /** The only way to make a page. */
        fun of(title: String?, rows: List<DialogRow>) =
            DialogPage(0, title, rows.filterNot { it is EntryRow && it.size == 0 })

    }
}

/**
 * The pages of the dialog, the last one on screen, and the state of their lines.
 * It knows nothing of the SmartTube option classes: the activity turns them into rows,
 * and applies a choice to them after the state has been changed.
 */
class DialogState {
    private class Level(val page: DialogPage, val returnFocus: Int? = null)

    private var levels by mutableStateOf<List<Level>>(emptyList())
    private var nextId = 1

    var isTransparent by mutableStateOf(false)
    var isOverlay = false
    var viewId = 0

    val page: DialogPage? get() = levels.lastOrNull()?.page
    val depth: Int get() = levels.size
    val canGoBack: Boolean get() = levels.size > 1

    /** Puts [page] on top. [openedFrom] is the line of the page below that gets focus when this one is left. */
    fun push(page: DialogPage, openedFrom: Int? = null) {
        val below = levels.lastOrNull()
        val kept = if (below == null) levels else levels.dropLast(1) + Level(below.page, openedFrom)
        levels = kept + Level(page.withId(nextId++))
    }

    /**
     * Takes the top page's place with [page], for a page whose lines changed under it (an account gone, a
     * channel unblocked). The id stays, so the screen keeps its scroll position and focus.
     */
    fun replace(page: DialogPage) {
        val top = levels.lastOrNull()
        levels = levels.dropLast(1) + Level(page.withId(top?.page?.id ?: nextId++))
    }

    fun open(entry: EntryRow) = push(entry.open(), openedFrom = entry.key)

    /** False when there is no page below. */
    fun pop(): Boolean {
        if (!canGoBack) return false
        levels = levels.dropLast(1)
        return true
    }

    fun clear() {
        levels = emptyList()
    }

    /** Selects [key] and deselects the other lines of the radio list. */
    fun choose(key: Int) = update { row ->
        when {
            row is ChoiceRow && !row.multi -> row.copy(selected = row.key == key)
            else -> row
        }
    }

    fun toggle(key: Int) = update { row ->
        when {
            row.key != key -> row
            row is ChoiceRow -> row.copy(selected = !row.selected)
            row is SwitchRow -> row.copy(selected = !row.selected)
            else -> row
        }
    }

    /**
     * Takes the selection of the checkboxes and switches from their owner again, since an item may change others.
     * A radio list is not touched: its items do not all tell when another one is chosen.
     */
    fun sync(selectedOf: (DialogRow) -> Boolean) = update { row ->
        when {
            row is ChoiceRow && row.multi -> row.copy(selected = selectedOf(row))
            row is SwitchRow -> row.copy(selected = selectedOf(row))
            else -> row
        }
    }

    /** The line that has focus when the page opens, or comes back: the one it was left from, the selected one, the first. */
    fun initialFocus(): Int? {
        val top = levels.lastOrNull() ?: return null
        top.returnFocus?.let { return it }
        val rows = top.page.rows
        return (rows.firstOrNull { it is ChoiceRow && it.selected } ?: rows.firstOrNull())?.key
    }

    private fun update(transform: (DialogRow) -> DialogRow) {
        val top = levels.lastOrNull() ?: return
        val changed = top.page.withRows(top.page.rows.map(transform))
        levels = levels.dropLast(1) + Level(changed, top.returnFocus)
    }
}

/**
 * The player opens a dialog on the down of OK, so a held OK goes on in the new window: its repeats and its
 * up would activate the focused row. Confirm keys count only from a press that starts in this window.
 */
class ConfirmKeyGuard {
    private var pressStartedHere = false

    fun shouldDrop(isDown: Boolean, repeatCount: Int): Boolean {
        if (isDown && repeatCount == 0) pressStartedHere = true
        return !pressStartedHere
    }
}
