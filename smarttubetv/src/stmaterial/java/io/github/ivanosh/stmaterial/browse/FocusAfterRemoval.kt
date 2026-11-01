package io.github.ivanosh.stmaterial.browse

/** The cards of a row, by id. */
class CardRow(val id: String, val ids: List<String>)

/**
 * Where focus goes when the focused card is among [gone]: the next card of its row, at the end the
 * previous one; with the row emptied, the card in the same place of the row above, or below when
 * there is none above. Null when focus stays where it is, or nothing is left.
 */
fun focusAfterRemoval(rows: List<CardRow>, focused: FocusKey, gone: Set<String>): FocusKey? {
    if (focused.videoId !in gone) return null
    val rowIndex = rows.indexOfFirst { it.id == focused.rowId }
    if (rowIndex < 0) return null
    val ids = rows[rowIndex].ids
    val at = ids.indexOf(focused.videoId)
    val next = ids.drop(at + 1).firstOrNull { it !in gone } ?: ids.take(maxOf(at, 0)).lastOrNull { it !in gone }
    if (next != null) return FocusKey(focused.rowId, next)

    fun survivors(row: CardRow) = row.ids.filter { it !in gone }
    val other = (rowIndex - 1 downTo 0).firstOrNull { survivors(rows[it]).isNotEmpty() }
        ?: (rowIndex + 1 until rows.size).firstOrNull { survivors(rows[it]).isNotEmpty() }
        ?: return null
    val left = survivors(rows[other])
    return FocusKey(rows[other].id, left[at.coerceIn(0, left.size - 1)])
}
