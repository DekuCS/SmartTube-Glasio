package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerKeysTest {
    private val step = 10_000L

    private fun ui(focus: PanelFocus, index: Int = 0, count: Int = 6) = PlayerUi(focus, index, count)
    private fun press(from: PlayerUi, key: PlayerKey) = playerKey(from, key, step)

    // Panel hidden

    @Test
    fun hiddenOkTogglesAndOpensOnFirstButton() {
        val (next, commands) = press(ui(PanelFocus.HIDDEN, index = 3), PlayerKey.Ok)
        assertEquals(ui(PanelFocus.BUTTONS, index = 0), next)
        assertEquals(listOf(PlayerCommand.TogglePlay), commands)
    }

    @Test
    fun hiddenLeftRightSeekAndStayHidden() {
        val hidden = ui(PanelFocus.HIDDEN)
        assertEquals(hidden to listOf<PlayerCommand>(PlayerCommand.Seek(-step)), press(hidden, PlayerKey.Left))
        assertEquals(hidden to listOf<PlayerCommand>(PlayerCommand.Seek(step)), press(hidden, PlayerKey.Right))
    }

    @Test
    fun hiddenUpOpensOnTheSeekBarWithoutCommands() {
        val (next, commands) = press(ui(PanelFocus.HIDDEN, index = 2), PlayerKey.Up)
        assertEquals(ui(PanelFocus.PROGRESS, index = 0), next)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun hiddenDownOpensOnTheButtonsWithoutCommands() {
        val (next, commands) = press(ui(PanelFocus.HIDDEN, index = 2), PlayerKey.Down)
        assertEquals(ui(PanelFocus.BUTTONS, index = 0), next)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun openingWithUpOrDownKeepsWhatTheChapterRowKnows() {
        val hidden = PlayerUi(PanelFocus.HIDDEN, 3, 9, chapterCount = 4, currentChapter = 2, chapterIndex = 1)
        assertEquals(hidden.copy(focus = PanelFocus.PROGRESS, buttonIndex = 0), press(hidden, PlayerKey.Up).first)
        assertEquals(hidden.copy(focus = PanelFocus.BUTTONS, buttonIndex = 0), press(hidden, PlayerKey.Down).first)
    }

    @Test
    fun hiddenBackExits() {
        val hidden = ui(PanelFocus.HIDDEN)
        assertEquals(hidden to listOf<PlayerCommand>(PlayerCommand.Exit), press(hidden, PlayerKey.Back))
    }

    // Panel open, focus on the buttons

    @Test
    fun buttonsUpMovesToProgressKeepingIndex() {
        val (next, commands) = press(ui(PanelFocus.BUTTONS, index = 2), PlayerKey.Up)
        assertEquals(ui(PanelFocus.PROGRESS, index = 2), next)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun buttonsDownStays() {
        val buttons = ui(PanelFocus.BUTTONS, index = 2)
        assertEquals(buttons to emptyList<PlayerCommand>(), press(buttons, PlayerKey.Down))
    }

    @Test
    fun buttonsLeftRightMoveWithoutSeeking() {
        val (right, rightCommands) = press(ui(PanelFocus.BUTTONS, index = 2), PlayerKey.Right)
        assertEquals(3, right.buttonIndex)
        assertTrue(rightCommands.isEmpty())
        val (left, leftCommands) = press(ui(PanelFocus.BUTTONS, index = 2), PlayerKey.Left)
        assertEquals(1, left.buttonIndex)
        assertTrue(leftCommands.isEmpty())
    }

    @Test
    fun buttonIndexStopsAtBothEnds() {
        assertEquals(0, press(ui(PanelFocus.BUTTONS, index = 0), PlayerKey.Left).first.buttonIndex)
        assertEquals(5, press(ui(PanelFocus.BUTTONS, index = 5), PlayerKey.Right).first.buttonIndex)
    }

    @Test
    fun buttonsOkPressesTheFocusedButton() {
        val buttons = ui(PanelFocus.BUTTONS, index = 2)
        assertEquals(buttons to listOf<PlayerCommand>(PlayerCommand.Press(2)), press(buttons, PlayerKey.Ok))
    }

    @Test
    fun okOnTheLastButtonOpensMore() {
        val more = ui(PanelFocus.BUTTONS, index = 5)
        assertEquals(more to listOf<PlayerCommand>(PlayerCommand.OpenMore), press(more, PlayerKey.Ok))
    }

    @Test
    fun buttonsBackHidesWithoutExit() {
        val (next, commands) = press(ui(PanelFocus.BUTTONS, index = 2), PlayerKey.Back)
        assertEquals(PanelFocus.HIDDEN, next.focus)
        assertTrue(commands.isEmpty())
    }

    // Panel open, focus on the progress bar

    @Test
    fun progressLeftRightSeekByStepAndStay() {
        val progress = ui(PanelFocus.PROGRESS, index = 1)
        assertEquals(progress to listOf<PlayerCommand>(PlayerCommand.Seek(-step)), press(progress, PlayerKey.Left))
        assertEquals(progress to listOf<PlayerCommand>(PlayerCommand.Seek(step)), press(progress, PlayerKey.Right))
    }

    @Test
    fun progressUsesTheStepItIsGiven() {
        val progress = ui(PanelFocus.PROGRESS)
        assertEquals(listOf<PlayerCommand>(PlayerCommand.Seek(30_000)), playerKey(progress, PlayerKey.Right, 30_000).second)
    }

    @Test
    fun progressDownReturnsToTheSameButton() {
        val (next, commands) = press(ui(PanelFocus.PROGRESS, index = 2), PlayerKey.Down)
        assertEquals(ui(PanelFocus.BUTTONS, index = 2), next)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun progressUpHides() {
        val (next, commands) = press(ui(PanelFocus.PROGRESS), PlayerKey.Up)
        assertEquals(PanelFocus.HIDDEN, next.focus)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun progressOkTogglesAndStays() {
        val progress = ui(PanelFocus.PROGRESS)
        assertEquals(progress to listOf<PlayerCommand>(PlayerCommand.TogglePlay), press(progress, PlayerKey.Ok))
    }

    @Test
    fun progressBackHidesWithoutExit() {
        val (next, commands) = press(ui(PanelFocus.PROGRESS), PlayerKey.Back)
        assertEquals(PanelFocus.HIDDEN, next.focus)
        assertTrue(commands.isEmpty())
    }

    // Review Focus 5: a held key

    @Test
    fun everyRepeatGivesExactlyOneSeek() {
        var current = ui(PanelFocus.HIDDEN)
        repeat(20) {
            val (next, commands) = press(current, PlayerKey.Right)
            assertEquals(listOf<PlayerCommand>(PlayerCommand.Seek(step)), commands)
            current = next
        }
    }

    @Test
    fun heldKeysOnTheButtonsStayInRange() {
        var current = ui(PanelFocus.BUTTONS)
        repeat(20) { current = press(current, PlayerKey.Right).first }
        assertEquals(5, current.buttonIndex)
        repeat(20) { current = press(current, PlayerKey.Left).first }
        assertEquals(0, current.buttonIndex)
    }

    // Seek debounce

    @Test
    fun firstSeekKeyStartsFromThePlayerPosition() {
        val seek = SeekDebounce().onKey(positionMs = 60_000, deltaMs = 10_000, durationMs = 600_000, nowMs = 1_000)
        assertEquals(70_000L, seek.targetMs)
    }

    @Test
    fun repeatsBuildOnTheTargetNotOnThePosition() {
        val seek = SeekDebounce()
            .onKey(60_000, 10_000, 600_000, 1_000)
            .onKey(60_000, 10_000, 600_000, 1_050)
            .onKey(60_000, -5_000, 600_000, 1_100)
        assertEquals(75_000L, seek.targetMs)
    }

    @Test
    fun targetIsClampedToTheVideo() {
        assertEquals(0L, SeekDebounce().onKey(3_000, -10_000, 600_000, 0).targetMs)
        assertEquals(600_000L, SeekDebounce().onKey(595_000, 10_000, 600_000, 0).targetMs)
    }

    @Test
    fun unknownDurationHasNoUpperBoundAndUnknownPositionStartsAtZero() {
        assertEquals(70_000L, SeekDebounce().onKey(60_000, 10_000, -1, 0).targetMs)
        assertEquals(10_000L, SeekDebounce().onKey(-1, 10_000, 600_000, 0).targetMs)
    }

    @Test
    fun nothingReachesTheEngineBeforeTheDelay() {
        val seek = SeekDebounce().onKey(60_000, 10_000, 600_000, 1_000)
        val (kept, due) = seek.takeDue(1_000 + SEEK_DEBOUNCE_MS - 1)
        assertNull(due)
        assertEquals(seek, kept)
    }

    @Test
    fun theDelayCountsFromTheLastRepeat() {
        val seek = SeekDebounce()
            .onKey(60_000, 10_000, 600_000, 1_000)
            .onKey(60_000, 10_000, 600_000, 1_250)
        assertNull(seek.takeDue(1_300 + 1).second)
        assertEquals(80_000L, seek.takeDue(1_250 + SEEK_DEBOUNCE_MS).second)
    }

    @Test
    fun aDueSeekIsSentOnceAndNothingAccumulatesAfterRelease() {
        val seek = SeekDebounce().onKey(60_000, 10_000, 600_000, 1_000)
        val (cleared, due) = seek.takeDue(2_000)
        assertEquals(70_000L, due)
        assertNull(cleared.targetMs)
        assertNull(cleared.takeDue(9_000).second)
        // The next press starts over from where the player is now.
        assertEquals(80_000L, cleared.onKey(70_000, 10_000, 600_000, 9_000).targetMs)
    }

    // Auto-hide

    @Test
    fun panelHidesAfterFiveIdleSecondsWhilePlaying() {
        assertFalse(shouldAutoHide(ui(PanelFocus.BUTTONS), playing = true, idleMs = PANEL_AUTO_HIDE_MS - 1))
        assertTrue(shouldAutoHide(ui(PanelFocus.BUTTONS), playing = true, idleMs = PANEL_AUTO_HIDE_MS))
        assertTrue(shouldAutoHide(ui(PanelFocus.PROGRESS), playing = true, idleMs = PANEL_AUTO_HIDE_MS))
    }

    @Test
    fun panelStaysWhilePaused() {
        assertFalse(shouldAutoHide(ui(PanelFocus.BUTTONS), playing = false, idleMs = 60_000))
    }

    @Test
    fun aHiddenPanelHasNothingToHide() {
        assertFalse(shouldAutoHide(ui(PanelFocus.HIDDEN), playing = true, idleMs = 60_000))
    }

    // The presenter opens the panel by itself

    @Test
    fun aPresenterOpenTakesTheButtonsWhenNoSeekIsGoingOn() {
        assertEquals(PanelFocus.BUTTONS, presenterOpenFocus(lastSeekKeyMs = null, nowMs = 10_000))
        assertEquals(PanelFocus.BUTTONS, presenterOpenFocus(lastSeekKeyMs = 1_000, nowMs = 1_000 + SEEK_KEEPS_FOCUS_MS))
    }

    @Test
    fun aPresenterOpenRightAfterASeekKeyKeepsTheKeySeeking() {
        assertEquals(PanelFocus.PROGRESS, presenterOpenFocus(lastSeekKeyMs = 1_000, nowMs = 1_000))
        assertEquals(PanelFocus.PROGRESS, presenterOpenFocus(lastSeekKeyMs = 1_000, nowMs = 1_000 + SEEK_KEEPS_FOCUS_MS - 1))
    }

    @Test
    fun theRestOfAHoldAfterAPresenterOpenStillSeeks() {
        val opened = ui(PanelFocus.HIDDEN).copy(focus = presenterOpenFocus(lastSeekKeyMs = 1_000, nowMs = 1_100))
        assertEquals(listOf<PlayerCommand>(PlayerCommand.Seek(step)), press(opened, PlayerKey.Right).second)
    }

    // Time labels

    @Test
    fun timeIsMinutesAndSecondsWithHoursWhenNeeded() {
        assertEquals("0:00", formatPlayerTime(0))
        assertEquals("12:40", formatPlayerTime(760_000))
        assertEquals("1:02:03", formatPlayerTime(3_723_000))
        assertEquals("0:00", formatPlayerTime(-1))
    }
}
