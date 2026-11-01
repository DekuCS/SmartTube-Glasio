package io.github.ivanosh.stmaterial.logic

/** What the vertical list of the feed needs to know of a row: a grid is cut into lines, a row is one item. */
data class RowShape(val isGrid: Boolean, val cards: Int)

fun lineCount(cards: Int, columns: Int): Int = (cards + columns - 1) / columns

/** Items a row takes in the vertical list: one for a row, one per line for a grid (its heading is in the first). */
fun itemCount(row: RowShape, columns: Int): Int = if (row.isGrid) lineCount(row.cards, columns) else 1

/** Index in the vertical list of the item holding the card [cardIndex] of the row [rowIndex]. */
fun outerIndex(rows: List<RowShape>, rowIndex: Int, cardIndex: Int, columns: Int): Int {
    val before = (0 until rowIndex).sumOf { itemCount(rows[it], columns) }
    return if (rows[rowIndex].isGrid) before + cardIndex / columns else before
}
