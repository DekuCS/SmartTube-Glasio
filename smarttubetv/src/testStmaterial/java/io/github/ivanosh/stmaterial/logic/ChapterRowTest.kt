package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterRowTest {
    private val step = 10_000L
    private val starts = listOf(0L, 100_000L, 200_000L, 300_000L)

    private fun ui(focus: PanelFocus, button: Int = 3, chapters: Int = 4, current: Int = 2, chapter: Int = 0) =
        PlayerUi(focus, button, 9, chapters, current, chapter)

    private fun press(from: PlayerUi, key: PlayerKey) = playerKey(from, key, step)

    // Which chapter a position is in

    @Test
    fun theChapterIsTheLastStartAtOrBeforeThePosition() {
        assertEquals(0, chapterIndexAt(starts, 0))
        assertEquals(0, chapterIndexAt(starts, 99_999))
        assertEquals(1, chapterIndexAt(starts, 100_000))
        assertEquals(3, chapterIndexAt(starts, 599_000))
    }

    @Test
    fun beforeTheFirstStartOrWithoutChaptersThereIsNone() {
        assertEquals(-1, chapterIndexAt(emptyList(), 5_000))
        assertEquals(-1, chapterIndexAt(listOf(30_000L, 60_000L), 5_000))
        assertEquals(0, chapterIndexAt(listOf(30_000L, 60_000L), 30_000))
    }

    // The row

    @Test
    fun downFromTheButtonsWithoutChaptersDoesNothing() {
        val buttons = ui(PanelFocus.BUTTONS, chapters = 0)
        assertEquals(buttons to emptyList<PlayerCommand>(), press(buttons, PlayerKey.Down))
    }

    @Test
    fun downFromTheButtonsFocusesTheCurrentChapter() {
        val (next, commands) = press(ui(PanelFocus.BUTTONS, current = 2), PlayerKey.Down)
        assertEquals(PanelFocus.CHAPTERS, next.focus)
        assertEquals(2, next.chapterIndex)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun upReturnsToTheSameButton() {
        val (inRow, _) = press(ui(PanelFocus.BUTTONS, button = 5), PlayerKey.Down)
        val (back, commands) = press(inRow, PlayerKey.Up)
        assertEquals(PanelFocus.BUTTONS, back.focus)
        assertEquals(5, back.buttonIndex)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun leftAndRightMoveAlongTheRowAndStopAtTheEnds() {
        var current = ui(PanelFocus.CHAPTERS, chapter = 1)
        assertEquals(2, press(current, PlayerKey.Right).first.chapterIndex)
        assertEquals(0, press(current, PlayerKey.Left).first.chapterIndex)
        repeat(10) { current = press(current, PlayerKey.Right).first }
        assertEquals(3, current.chapterIndex)
        repeat(10) { current = press(current, PlayerKey.Left).first }
        assertEquals(0, current.chapterIndex)
        assertTrue(press(current, PlayerKey.Left).second.isEmpty())
    }

    @Test
    fun okSeeksToTheFocusedChapterAndReturnsToTheButtons() {
        val (next, commands) = press(ui(PanelFocus.CHAPTERS, button = 4, chapter = 2), PlayerKey.Ok)
        assertEquals(listOf<PlayerCommand>(PlayerCommand.SeekToChapter(2)), commands)
        assertEquals(PanelFocus.BUTTONS, next.focus)
        assertEquals(4, next.buttonIndex)
    }

    @Test
    fun okTargetIsTheStartOfTheChapterAndNeverPastTheVideo() {
        assertEquals(200_000L, chapterStartAt(starts, 2, 600_000))
        assertEquals(0L, chapterStartAt(starts, 0, 600_000))
        assertEquals(250_000L, chapterStartAt(starts, 3, 250_000))
        assertEquals(null, chapterStartAt(starts, 7, 600_000))
    }

    @Test
    fun backReturnsToTheButtonsAndASecondBackHidesAsUsual() {
        val (next, commands) = press(ui(PanelFocus.CHAPTERS, button = 6), PlayerKey.Back)
        assertEquals(PanelFocus.BUTTONS, next.focus)
        assertEquals(6, next.buttonIndex)
        assertTrue(commands.isEmpty())
        val (hidden, second) = press(next, PlayerKey.Back)
        assertEquals(PanelFocus.HIDDEN, hidden.focus)
        assertTrue(second.isEmpty())
    }

    @Test
    fun downInTheRowStays() {
        val row = ui(PanelFocus.CHAPTERS, chapter = 1)
        assertEquals(row to emptyList<PlayerCommand>(), press(row, PlayerKey.Down))
    }

    @Test
    fun theRowDoesNotAutoHide() {
        assertFalse(shouldAutoHide(ui(PanelFocus.CHAPTERS), playing = true, idleMs = 60_000))
        assertTrue(shouldAutoHide(ui(PanelFocus.BUTTONS), playing = true, idleMs = PANEL_AUTO_HIDE_MS))
    }

    @Test
    fun theRowEmptiedByANewVideoSendsFocusBackToTheButtons() {
        val emptied = withChapters(ui(PanelFocus.CHAPTERS, button = 3, chapter = 2), count = 0, current = -1)
        assertEquals(PanelFocus.BUTTONS, emptied.focus)
        assertEquals(3, emptied.buttonIndex)
        assertEquals(0, emptied.chapterCount)
    }

    @Test
    fun aShorterRowKeepsTheFocusInRange() {
        val shorter = withChapters(ui(PanelFocus.CHAPTERS, chapter = 3), count = 2, current = 1)
        assertEquals(PanelFocus.CHAPTERS, shorter.focus)
        assertEquals(1, shorter.chapterIndex)
        assertEquals(2, shorter.chapterCount)
    }

    @Test
    fun noCurrentChapterFocusesTheFirstCard() {
        val (next, _) = press(ui(PanelFocus.BUTTONS, current = -1), PlayerKey.Down)
        assertEquals(0, next.chapterIndex)
    }

    @Test
    fun otherFocusesAreAsBefore() {
        assertEquals(PanelFocus.BUTTONS, press(ui(PanelFocus.HIDDEN), PlayerKey.Down).first.focus)
        assertEquals(PanelFocus.BUTTONS, press(ui(PanelFocus.PROGRESS), PlayerKey.Down).first.focus)
    }
}
