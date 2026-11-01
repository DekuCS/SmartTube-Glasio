package io.github.ivanosh.stmaterial.logic

import io.github.ivanosh.stmaterial.logic.PlayerButton.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerButtonsTest {
    @Test
    fun theDefaultIsTheOneInGlobalConstraints() {
        assertEquals(listOf(PlayPause, Next, Quality, Subtitles, Speed), DEFAULT_BUTTONS)
        assertEquals(5, PANEL_LIMIT)
    }

    @Test
    fun theFirstVersionHasTwentyThreeButtonsAndNoneOfTheExcludedOnes() {
        assertEquals(23, PlayerButton.values().size)
        val names = PlayerButton.values().map { it.name }
        for (excluded in listOf("Chat", "Pip", "Rotate", "Flip", "ScreenOff", "Comments")) assertFalse(excluded, excluded in names)
    }

    @Test
    fun everyButtonIsInExactlyOneGroup() {
        val all = moreGroups(emptyList()).flatMap { it.buttons }
        assertEquals(PlayerButton.values().toList().sortedBy { it.ordinal }, all.sortedBy { it.ordinal })
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun theGroupsAreByMeaningAndInOrder() {
        val groups = moreGroups(emptyList())
        assertEquals(listOf(ButtonGroupKind.Playback, ButtonGroupKind.PictureAndSound, ButtonGroupKind.VideoAndChannel), groups.map { it.kind })
        assertTrue(Speed in groups[0].buttons)
        assertTrue(Quality in groups[1].buttons)
        assertTrue(Subscribe in groups[2].buttons)
    }

    @Test
    fun buttonsOnThePanelAreNotRepeatedInMore() {
        val inMore = moreGroups(DEFAULT_BUTTONS).flatMap { it.buttons }
        for (button in DEFAULT_BUTTONS) assertFalse(button.name, button in inMore)
        assertEquals(PlayerButton.values().size - DEFAULT_BUTTONS.size, inMore.size)
    }

    @Test
    fun aGroupWithNothingLeftIsLeftOut() {
        val everythingOfPlayback = PlayerButton.values().filter { groupOf(it) == ButtonGroupKind.Playback }
        assertFalse(moreGroups(everythingOfPlayback).any { it.kind == ButtonGroupKind.Playback })
    }

    @Test
    fun aButtonPushedOffTheFullPanelShowsUpInMore() {
        val editor = buttonsEditorState(DEFAULT_BUTTONS).pick(Like)
        val result = editor.toggleList()
        assertEquals(Speed, result.displaced)
        assertEquals(PANEL_LIMIT, result.state.shown.size)
        assertTrue(Speed in moreGroups(result.state.shown).flatMap { it.buttons })
        assertFalse(Like in moreGroups(result.state.shown).flatMap { it.buttons })
    }

    @Test
    fun theEditorStateHoldsEveryOtherButtonAsHiddenWithTheLimit() {
        val state = buttonsEditorState(DEFAULT_BUTTONS)
        assertEquals(DEFAULT_BUTTONS, state.shown)
        assertEquals(PlayerButton.values().filter { it !in DEFAULT_BUTTONS }, state.hidden)
        assertEquals(PANEL_LIMIT, state.limit)
        assertTrue(state.locked.isEmpty())
    }

    @Test
    fun savedNamesBecomeButtons() {
        assertEquals(listOf(Like, PlayPause), resolveButtons(listOf("Like", "PlayPause")))
    }

    @Test
    fun anUnknownSavedNameIsSkipped() {
        assertEquals(listOf(Like, Next), resolveButtons(listOf("Like", "Teleport", "Chat", "Next")))
    }

    @Test
    fun aRepeatedNameCountsOnceAndTheLimitHolds() {
        assertEquals(listOf(Like, Next), resolveButtons(listOf("Like", "Next", "Like")))
        val many = resolveButtons(PlayerButton.values().map { it.name })
        assertEquals(PANEL_LIMIT, many.size)
        assertEquals(PlayerButton.values().take(PANEL_LIMIT), many)
    }

    @Test
    fun nothingValidFallsBackToTheDefault() {
        assertEquals(DEFAULT_BUTTONS, resolveButtons(emptyList()))
        assertEquals(DEFAULT_BUTTONS, resolveButtons(listOf("Chat", "Pip")))
    }

    @Test
    fun theSheetCursorStopsAtBothEnds() {
        assertEquals(0 to null, moreSheetKey(0, 4, PlayerKey.Up))
        assertEquals(1 to null, moreSheetKey(0, 4, PlayerKey.Down))
        assertEquals(3 to null, moreSheetKey(3, 4, PlayerKey.Down))
        assertEquals(2 to null, moreSheetKey(3, 4, PlayerKey.Up))
    }

    @Test
    fun theSheetActivatesTheRowUnderTheCursorAndClosesOnBack() {
        assertEquals(2 to SheetCommand.Activate(2), moreSheetKey(2, 4, PlayerKey.Ok))
        assertEquals(2 to SheetCommand.Close, moreSheetKey(2, 4, PlayerKey.Back))
    }

    @Test
    fun leftAndRightDoNothingInTheSheet() {
        assertEquals(2 to null, moreSheetKey(2, 4, PlayerKey.Left))
        assertNull(moreSheetKey(2, 4, PlayerKey.Right).second)
    }

    @Test
    fun aSwitchIsOnWhenThePresenterSaidOnAndRepeatWhenItRepeatsOneVideo() {
        assertTrue(buttonIsOn(Like, 1))
        assertFalse(buttonIsOn(Like, 0))
        assertFalse(buttonIsOn(Like, null))
        assertTrue(buttonIsOn(Repeat, 3))
        assertFalse(buttonIsOn(Repeat, 1))
    }
}
