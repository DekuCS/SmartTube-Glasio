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
        assertEquals(listOf(PlayPause, Next, Quality, Subtitles, Speed, Like, Dislike, Subscribe), DEFAULT_BUTTONS)
        assertEquals(8, PANEL_LIMIT)
        assertEquals(PANEL_LIMIT, DEFAULT_BUTTONS.size)
        assertEquals(listOf(PlayPause, Next, Quality, Subtitles, Speed), LEGACY_DEFAULT_BUTTONS)
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
        val editor = buttonsEditorState(DEFAULT_BUTTONS).pick(Queue)
        val result = editor.toggleList()
        assertEquals(Subscribe, result.displaced)
        assertEquals(PANEL_LIMIT, result.state.shown.size)
        assertTrue(Subscribe in moreGroups(result.state.shown).flatMap { it.buttons })
        assertFalse(Queue in moreGroups(result.state.shown).flatMap { it.buttons })
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
        assertEquals(DEFAULT_BUTTONS, resolveButtons(null))
        assertEquals(DEFAULT_BUTTONS, resolveButtons(listOf("Chat", "Pip")))
    }

    @Test
    fun aSavedEmptyListStaysEmpty() {
        assertEquals(emptyList<PlayerButton>(), resolveButtons(emptyList()))
    }

    @Test
    fun theOldDefaultSavedAsItWasBecomesTheNewDefault() {
        assertEquals(DEFAULT_BUTTONS, parseSavedButtons("PlayPause,Next,Quality,Subtitles,Speed", marked = false))
    }

    @Test
    fun theOldFiveSavedAfterTheMarkerStaysFive() {
        assertEquals(LEGACY_DEFAULT_BUTTONS, parseSavedButtons("PlayPause,Next,Quality,Subtitles,Speed", marked = true))
    }

    @Test
    fun anEmptyListSavedWithTheMarkerStaysEmptyAndWithoutItToo() {
        assertEquals(emptyList<PlayerButton>(), parseSavedButtons("", marked = true))
        assertEquals(emptyList<PlayerButton>(), parseSavedButtons("", marked = false))
    }

    @Test
    fun nothingSavedGivesTheDefaultWhateverTheMarker() {
        assertEquals(DEFAULT_BUTTONS, parseSavedButtons(null, marked = false))
        assertEquals(DEFAULT_BUTTONS, parseSavedButtons(null, marked = true))
    }

    @Test
    fun otherListsAreKeptWithOrWithoutTheMarker() {
        assertEquals(listOf(Like, Next), parseSavedButtons("Like, Next", marked = false))
        assertEquals(listOf(Like, Next), parseSavedButtons("Like,Next", marked = true))
    }

    @Test
    fun anyOtherSavedListIsKeptAsItIs() {
        // Another order, one more, one less, and the new default itself.
        val reordered = listOf("Next", "PlayPause", "Quality", "Subtitles", "Speed")
        assertEquals(reordered.map { PlayerButton.valueOf(it) }, resolveButtons(reordered))
        assertEquals(listOf(PlayPause, Next, Quality, Subtitles, Speed, Like), resolveButtons(listOf("PlayPause", "Next", "Quality", "Subtitles", "Speed", "Like")))
        assertEquals(listOf(PlayPause, Next, Quality, Subtitles), resolveButtons(listOf("PlayPause", "Next", "Quality", "Subtitles")))
        assertEquals(DEFAULT_BUTTONS, resolveButtons(DEFAULT_BUTTONS.map { it.name }))
        assertEquals(LEGACY_DEFAULT_BUTTONS, resolveButtons(LEGACY_DEFAULT_BUTTONS.map { it.name }))
    }

    @Test
    fun theButtonsEditorLetsEveryButtonGoDownToNone() {
        var state = buttonsEditorState(listOf(PlayPause, Next))
        assertEquals(0, state.minShown)
        for (button in listOf(Next, PlayPause)) {
            val result = state.pick(button).toggleList()
            assertEquals(null, result.displaced)
            state = result.state
        }
        assertEquals(emptyList<PlayerButton>(), state.shown)
        assertTrue(PlayPause in state.hidden && Next in state.hidden)
    }

    @Test
    fun aSmallPanelTakesAnotherButtonWithoutDisplacingAnything() {
        val result = buttonsEditorState(listOf(PlayPause, Next, Quality)).pick(Like).toggleList()
        assertEquals(null, result.displaced)
        assertEquals(listOf(PlayPause, Next, Quality, Like), result.state.shown)
    }

    @Test
    fun theSectionsEditorStillKeepsOne() {
        val state = EditorState(listOf("a"), listOf("b"), emptySet(), 3).pick("a")
        assertEquals(1, state.minShown)
        assertEquals(state, state.toggleList().state)
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
