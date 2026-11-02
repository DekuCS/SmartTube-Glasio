package io.github.ivanosh.stmaterial.logic

/** How much of a row below the buttons the panel's layout gives room: none, the tops of its cards, or all of it. */
enum class PanelRowShape { Hidden, Peek, Full }

/** The chapter row is the first below the buttons. With focus in the row under it, it gives way. */
fun chapterRowShape(focus: PanelFocus): PanelRowShape = when (focus) {
    PanelFocus.CHAPTERS -> PanelRowShape.Full
    PanelFocus.UPNEXT -> PanelRowShape.Hidden
    else -> PanelRowShape.Peek
}

/**
 * The Up next row is the last. Its cards peek at the screen edge from the row above it: the buttons when there are no chapters,
 * the chapter row otherwise. With the chapter row peeking, this one has no room.
 */
fun upNextRowShape(focus: PanelFocus, chaptersPresent: Boolean): PanelRowShape = when {
    focus == PanelFocus.UPNEXT -> PanelRowShape.Full
    focus == PanelFocus.CHAPTERS -> PanelRowShape.Peek
    chaptersPresent -> PanelRowShape.Hidden
    else -> PanelRowShape.Peek
}

/** At the end of a video the panel opens on the first card of the row, or on the buttons when there is none. */
fun endOfVideoFocus(upNextCount: Int, lastSeekKeyMs: Long? = null, nowMs: Long = 0L): PanelFocus {
    // A seek key held or just pressed goes on seeking: focus stays on the progress bar, as for any open the presenter asks for.
    if (presenterOpenFocus(lastSeekKeyMs, nowMs) == PanelFocus.PROGRESS) return PanelFocus.PROGRESS
    return if (upNextCount > 0) PanelFocus.UPNEXT else PanelFocus.BUTTONS
}

/** The row changed (a new video, a card gone): the card in focus stays in range, and focus in a row that is gone goes up. */
fun withUpNext(ui: PlayerUi, count: Int): PlayerUi {
    val moved = ui.copy(upNextCount = count, upNextIndex = ui.upNextIndex.coerceIn(0, (count - 1).coerceAtLeast(0)))
    return if (count == 0 && moved.focus == PanelFocus.UPNEXT) moved.copy(focus = if (moved.chapterCount > 0) PanelFocus.CHAPTERS else PanelFocus.BUTTONS) else moved
}

/**
 * Where focus goes in [ids] with [gone] taken out: the same card if it stays, else the next one, at the end the previous one.
 * The index is into the list that remains; 0 when nothing does.
 */
fun focusIndexAfterRemoval(ids: List<String>, focusedIndex: Int, gone: Set<String>): Int {
    val kept = ids.count { it !in gone }
    if (kept == 0) return 0
    val before = ids.take(focusedIndex.coerceIn(0, ids.size)).count { it !in gone }
    return before.coerceAtMost(kept - 1)
}

/** The card menu in the player: a list of [count] lines with a cursor. Draws and acts on lines the Activity holds. */
data class PickMenu(val count: Int, val cursor: Int = 0)

sealed interface PickStep {
    object None : PickStep
    object Close : PickStep
    data class Choose(val index: Int) : PickStep
}

/** One remote key in the menu: Up and Down move, OK chooses, Back closes. */
fun PickMenu.key(key: PlayerKey): Pair<PickMenu, PickStep> = when (key) {
    PlayerKey.Up -> copy(cursor = (cursor - 1).coerceAtLeast(0)) to PickStep.None
    PlayerKey.Down -> copy(cursor = (cursor + 1).coerceAtMost((count - 1).coerceAtLeast(0))) to PickStep.None
    PlayerKey.Ok -> this to (if (cursor in 0 until count) PickStep.Choose(cursor) else PickStep.None)
    PlayerKey.Back -> this to PickStep.Close
    PlayerKey.Left, PlayerKey.Right -> this to PickStep.None
}

/** What the first page of a group does to the row: [taken] afterwards, and the cards to show (null: leave the row as it is). */
data class UpNextChoice<T>(val taken: Boolean, val items: List<T>?)

fun <T> chooseUpNext(taken: Boolean, queueLike: Boolean, page: List<T>, isCurrent: (T) -> Boolean): UpNextChoice<T> =
    when {
        taken || page.isEmpty() -> UpNextChoice(taken, null)
        !queueLike -> UpNextChoice(true, page)
        else -> {
            // A playlist or the queue: the row starts after the video that plays. A page without it may come later.
            val at = page.indexOfFirst(isCurrent)
            if (at < 0) UpNextChoice(false, null)
            else page.drop(at + 1).let { UpNextChoice(true, it.ifEmpty { null }) }
        }
    }

/** The card OK or a hold acts on: the focused one, or none when the index is outside the row. */
fun okCardPlace(index: Int, count: Int): Int? = index.takeIf { it in 0 until count }

/** The indexes of [cards] that [removed] took out: the same card, or any card of the author when the whole channel went. */
fun <T> goneCards(cards: List<T>, removed: List<T>, byAuthor: Boolean, authorOf: (T) -> String?): List<Int> =
    cards.indices.filter { i ->
        val card = cards[i]
        val author = authorOf(card)
        removed.any { it == card || (byAuthor && author != null && authorOf(it) == author) }
    }

/** The panel was open on the row, the row was cleared and refilled for the same video: focus goes back to the card it had. */
fun reopenedRow(ui: PlayerUi, lostIndex: Int?, count: Int): PlayerUi =
    if (lostIndex == null || count <= 0 || (ui.focus != PanelFocus.BUTTONS && ui.focus != PanelFocus.CHAPTERS)) ui
    else ui.copy(focus = PanelFocus.UPNEXT, upNextIndex = lostIndex.coerceIn(0, count - 1))
