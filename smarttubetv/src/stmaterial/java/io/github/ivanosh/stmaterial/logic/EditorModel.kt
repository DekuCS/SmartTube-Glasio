package io.github.ivanosh.stmaterial.logic

/** Shared state for the section and player-button editors. */
data class EditorState<T>(
    val shown: List<T>,
    val hidden: List<T>,
    val locked: Set<T>,
    val limit: Int,
    val picked: T? = null,
    /** The editor will not take the last items out of the shown list below this many. */
    val minShown: Int = 1
)

data class EditResult<T>(val state: EditorState<T>, val displaced: T? = null)

/** Picks [item]; picking the already-picked item drops the pick. */
fun <T> EditorState<T>.pick(item: T): EditorState<T> =
    copy(picked = if (picked == item) null else item)

/** Moves the picked shown item by [delta] places, stopping at the list ends and before locked items. */
fun <T> EditorState<T>.moveBy(delta: Int): EditorState<T> {
    val item = picked ?: return this
    val from = shown.indexOf(item)
    if (from < 0 || delta == 0) return this
    val step = if (delta > 0) 1 else -1
    var to = from
    repeat(kotlin.math.abs(delta)) {
        val next = to + step
        if (next !in shown.indices || shown[next] in locked) return@repeat
        to = next
    }
    if (to == from) return this
    val list = shown.toMutableList()
    list.removeAt(from)
    list.add(to, item)
    return copy(shown = list)
}

/** Moves the picked item between shown and hidden, displacing the last non-locked shown item at the limit. */
fun <T> EditorState<T>.toggleList(): EditResult<T> {
    val item = picked ?: return EditResult(this)
    if (item in shown) {
        if (item in locked || shown.size <= minShown) return EditResult(this)
        return EditResult(copy(shown = shown - item, hidden = hidden + item))
    }
    if (item !in hidden) return EditResult(this)
    if (shown.size < limit) {
        return EditResult(copy(shown = shown + item, hidden = hidden - item))
    }
    val victim = shown.lastOrNull { it !in locked } ?: return EditResult(this)
    return EditResult(
        copy(shown = shown - victim + item, hidden = listOf(victim) + (hidden - item)),
        victim
    )
}

fun <T> EditorState<T>.reset(defaults: EditorState<T>): EditorState<T> = defaults.copy(picked = null)
