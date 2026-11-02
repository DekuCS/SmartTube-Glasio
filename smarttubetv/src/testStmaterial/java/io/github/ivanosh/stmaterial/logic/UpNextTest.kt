package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextTest {
    private val step = 10_000L

    private fun ui(focus: PanelFocus, button: Int = 3, chapters: Int = 0, current: Int = 1, chapter: Int = 0, upNext: Int = 6, card: Int = 0) =
        PlayerUi(focus, button, 9, chapters, current, chapter, upNext, card)

    private fun press(from: PlayerUi, key: PlayerKey) = playerKey(from, key, step)

    // Down

    @Test
    fun downFromTheButtonsWithoutChaptersEntersTheRowAtTheFirstCard() {
        val (next, commands) = press(ui(PanelFocus.BUTTONS), PlayerKey.Down)
        assertEquals(PanelFocus.UPNEXT, next.focus)
        assertEquals(0, next.upNextIndex)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun downFromTheButtonsWithChaptersEntersTheChaptersFirst() {
        val (next, _) = press(ui(PanelFocus.BUTTONS, chapters = 4), PlayerKey.Down)
        assertEquals(PanelFocus.CHAPTERS, next.focus)
    }

    @Test
    fun downFromTheChaptersEntersTheRow() {
        val (next, _) = press(ui(PanelFocus.CHAPTERS, chapters = 4, chapter = 2), PlayerKey.Down)
        assertEquals(PanelFocus.UPNEXT, next.focus)
    }

    @Test
    fun downWithoutARowStaysWhereItIs() {
        val buttons = ui(PanelFocus.BUTTONS, upNext = 0)
        assertEquals(buttons to emptyList<PlayerCommand>(), press(buttons, PlayerKey.Down))
        val chapters = ui(PanelFocus.CHAPTERS, chapters = 4, upNext = 0)
        assertEquals(chapters to emptyList<PlayerCommand>(), press(chapters, PlayerKey.Down))
    }

    @Test
    fun downInTheRowStays() {
        val row = ui(PanelFocus.UPNEXT, card = 2)
        assertEquals(row to emptyList<PlayerCommand>(), press(row, PlayerKey.Down))
    }

    // Up and Back

    @Test
    fun upAndBackGoToTheSameButtonWhenThereAreNoChapters() {
        for (key in listOf(PlayerKey.Up, PlayerKey.Back)) {
            val (inRow, _) = press(ui(PanelFocus.BUTTONS, button = 5), PlayerKey.Down)
            val (back, commands) = press(inRow, key)
            assertEquals(PanelFocus.BUTTONS, back.focus)
            assertEquals(5, back.buttonIndex)
            assertTrue(commands.isEmpty())
        }
    }

    @Test
    fun upAndBackGoToTheSameChapterCardWhenThereAreChapters() {
        for (key in listOf(PlayerKey.Up, PlayerKey.Back)) {
            val (chapters, _) = press(ui(PanelFocus.BUTTONS, button = 5, chapters = 4, current = 1), PlayerKey.Down)
            val (moved, _) = press(chapters, PlayerKey.Right)
            val (row, _) = press(moved, PlayerKey.Down)
            val (back, commands) = press(row, key)
            assertEquals(PanelFocus.CHAPTERS, back.focus)
            assertEquals(2, back.chapterIndex)
            // And one more up reaches the button it came from.
            assertEquals(5, press(back, PlayerKey.Up).first.buttonIndex)
            assertTrue(commands.isEmpty())
        }
    }

    @Test
    fun theCardInFocusIsRememberedWhileUpThere() {
        val (row, _) = press(ui(PanelFocus.BUTTONS), PlayerKey.Down)
        val (moved, _) = press(press(row, PlayerKey.Right).first, PlayerKey.Right)
        val (up, _) = press(moved, PlayerKey.Up)
        assertEquals(2, up.upNextIndex)
        assertEquals(2, press(up, PlayerKey.Down).first.upNextIndex)
    }

    // Left and right

    @Test
    fun leftAndRightMoveAlongTheRowAndStopAtTheEnds() {
        val last = ui(PanelFocus.UPNEXT, upNext = 6, card = 5)
        assertEquals(5, press(last, PlayerKey.Right).first.upNextIndex)
        assertEquals(4, press(last, PlayerKey.Left).first.upNextIndex)
        val first = ui(PanelFocus.UPNEXT, card = 0)
        assertEquals(0, press(first, PlayerKey.Left).first.upNextIndex)
        assertEquals(1, press(first, PlayerKey.Right).first.upNextIndex)
    }

    // OK

    @Test
    fun okOpensTheVideoOfTheCardInFocusAndClosesThePanel() {
        val (next, commands) = press(ui(PanelFocus.UPNEXT, card = 3), PlayerKey.Ok)
        assertEquals(listOf<PlayerCommand>(PlayerCommand.OpenUpNext(3)), commands)
        assertEquals(PanelFocus.HIDDEN, next.focus)
    }

    // The row changes

    @Test
    fun aRowThatEmptiesOnAVideoChangeSendsFocusBackToTheChapterRowOrTheButtons() {
        assertEquals(PanelFocus.BUTTONS, withUpNext(ui(PanelFocus.UPNEXT, card = 3), 0).focus)
        assertEquals(PanelFocus.CHAPTERS, withUpNext(ui(PanelFocus.UPNEXT, chapters = 4, card = 3), 0).focus)
    }

    @Test
    fun anEmptiedRowForgetsTheCardAndKeepsFocusElsewhere() {
        val emptied = withUpNext(ui(PanelFocus.BUTTONS, card = 3), 0)
        assertEquals(PanelFocus.BUTTONS, emptied.focus)
        assertEquals(0, emptied.upNextCount)
        assertEquals(0, emptied.upNextIndex)
    }

    @Test
    fun aShorterRowKeepsTheCardInRange() {
        val shorter = withUpNext(ui(PanelFocus.UPNEXT, card = 5), 3)
        assertEquals(PanelFocus.UPNEXT, shorter.focus)
        assertEquals(2, shorter.upNextIndex)
        assertEquals(3, shorter.upNextCount)
    }

    // The end of a video

    @Test
    fun atTheEndOfAVideoFocusIsOnTheFirstCardWhenThereIsARow() {
        assertEquals(PanelFocus.UPNEXT, endOfVideoFocus(6))
        assertEquals(PanelFocus.BUTTONS, endOfVideoFocus(0))
    }

    // Removing a card

    @Test
    fun removingTheFocusedCardMovesFocusToItsNeighbour() {
        val ids = listOf("a", "b", "c", "d")
        assertEquals(1, focusIndexAfterRemoval(ids, 1, setOf("b")))   // c, which is now at 1
        assertEquals(2, focusIndexAfterRemoval(ids, 3, setOf("d")))   // at the end: the previous one, c
        assertEquals(0, focusIndexAfterRemoval(ids, 0, setOf("a")))   // b, now first
    }

    @Test
    fun removingAnotherCardKeepsFocusOnTheSameCard() {
        val ids = listOf("a", "b", "c", "d")
        assertEquals(1, focusIndexAfterRemoval(ids, 2, setOf("a")))   // c is now at 1
        assertEquals(2, focusIndexAfterRemoval(ids, 2, setOf("d")))
    }

    @Test
    fun removingSeveralCardsAtOnceFindsTheNearestSurvivor() {
        val ids = listOf("a", "b", "c", "d", "e")
        assertEquals(0, focusIndexAfterRemoval(ids, 1, setOf("a", "b", "c")))  // d and e remain; d is the next survivor
        assertEquals(0, focusIndexAfterRemoval(listOf("a"), 0, setOf("a")))
    }

    // Row shapes: the chapter row and the Up next row share the panel's bottom

    @Test
    fun theChapterRowIsFullInFocusHiddenWhenTheRowBelowHasFocusAndPeeksOtherwise() {
        assertEquals(PanelRowShape.Full, chapterRowShape(PanelFocus.CHAPTERS))
        assertEquals(PanelRowShape.Hidden, chapterRowShape(PanelFocus.UPNEXT))
        assertEquals(PanelRowShape.Peek, chapterRowShape(PanelFocus.BUTTONS))
        assertEquals(PanelRowShape.Peek, chapterRowShape(PanelFocus.PROGRESS))
    }

    @Test
    fun theRowPeeksUnderTheButtonsOnlyWhenThereAreNoChaptersAndUnderTheChaptersOtherwise() {
        assertEquals(PanelRowShape.Full, upNextRowShape(PanelFocus.UPNEXT, chaptersPresent = true))
        assertEquals(PanelRowShape.Full, upNextRowShape(PanelFocus.UPNEXT, chaptersPresent = false))
        assertEquals(PanelRowShape.Peek, upNextRowShape(PanelFocus.BUTTONS, chaptersPresent = false))
        assertEquals(PanelRowShape.Peek, upNextRowShape(PanelFocus.PROGRESS, chaptersPresent = false))
        assertEquals(PanelRowShape.Hidden, upNextRowShape(PanelFocus.BUTTONS, chaptersPresent = true))
        assertEquals(PanelRowShape.Peek, upNextRowShape(PanelFocus.CHAPTERS, chaptersPresent = true))
    }

    // The card menu

    @Test
    fun theMenuMovesAndStopsAtTheEnds() {
        val menu = PickMenu(count = 3)
        assertEquals(PickMenu(3, 0), menu.key(PlayerKey.Up).first)
        assertEquals(PickMenu(3, 1), menu.key(PlayerKey.Down).first)
        assertEquals(PickMenu(3, 2), PickMenu(3, 2).key(PlayerKey.Down).first)
        assertEquals(PickMenu(3, 0), menu.key(PlayerKey.Left).first)
    }

    @Test
    fun theMenuChoosesWithOkAndClosesWithBack() {
        assertEquals(PickStep.Choose(1), PickMenu(3, 1).key(PlayerKey.Ok).second)
        assertEquals(PickStep.Close, PickMenu(3, 1).key(PlayerKey.Back).second)
        assertEquals(PickStep.None, PickMenu(3, 1).key(PlayerKey.Down).second)
        assertEquals(PickStep.None, PickMenu(0).key(PlayerKey.Ok).second)
    }
}
