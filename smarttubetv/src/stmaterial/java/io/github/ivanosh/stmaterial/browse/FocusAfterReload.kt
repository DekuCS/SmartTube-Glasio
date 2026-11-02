package io.github.ivanosh.stmaterial.browse

/**
 * Where focus goes when [new] rows replace [old] ones under it. The same video stays focused, in its row
 * or, when the row got another id, in the row that now holds it (the one at the same index first). A video
 * that is gone gives focus to the nearest position of its row: the row with the same id, else the row at
 * the same index, else the last row that has cards. Null when no card is left.
 */
fun focusAfterReload(old: List<CardRow>, focused: FocusKey, new: List<CardRow>): FocusKey? {
    val oldIndex = old.indexOfFirst { it.id == focused.rowId }
    val holders = new.withIndex().filter { focused.videoId in it.value.ids }
    val holder = holders.firstOrNull { it.value.id == focused.rowId }
        ?: holders.firstOrNull { it.index == oldIndex }
        ?: holders.firstOrNull()
    if (holder != null) return FocusKey(holder.value.id, focused.videoId)

    val position = old.getOrNull(oldIndex)?.ids?.indexOf(focused.videoId)?.coerceAtLeast(0) ?: 0
    val row = new.firstOrNull { it.id == focused.rowId && it.ids.isNotEmpty() }
        ?: new.getOrNull(oldIndex)?.takeIf { it.ids.isNotEmpty() }
        ?: new.lastOrNull { it.ids.isNotEmpty() }
        ?: return null
    return FocusKey(row.id, row.ids[position.coerceIn(0, row.ids.size - 1)])
}
