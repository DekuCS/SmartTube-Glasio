package io.github.ivanosh.stmaterial.logic

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ButtonMenuTest {
    private val b = PlayerButton.values()

    @Test fun subscribeHasOpenChannelFirst() {
        assertEquals(
            listOf(MenuAction.OpenChannel, MenuAction.Move, MenuAction.Remove, MenuAction.Customize),
            panelMenuActions(PlayerButton.Subscribe),
        )
    }

    @Test fun otherButtonsHaveNoSpecialAction() {
        for (button in PlayerButton.values().filter { it != PlayerButton.Subscribe }) {
            assertEquals(button.name, listOf(MenuAction.Move, MenuAction.Remove, MenuAction.Customize), panelMenuActions(button))
        }
    }

    @Test fun moreOffersOnlyCustomize() = assertEquals(listOf(MenuAction.Customize), panelMenuActions(null))

    @Test fun anItemInMoreOffersAddToPanel() = assertEquals(listOf(MenuAction.AddToPanel), moreMenuActions())

    @Test fun menuKeys() {
        val menu = ButtonMenu(panelMenuActions(PlayerButton.Subscribe))
        val (down, none) = menu.key(PlayerKey.Down)
        assertEquals(1, down.cursor); assertEquals(MenuStep.None, none)
        assertEquals(0, down.key(PlayerKey.Up).first.cursor)
        assertEquals(0, menu.key(PlayerKey.Up).first.cursor)
        var m = menu
        repeat(10) { m = m.key(PlayerKey.Down).first }
        assertEquals(3, m.cursor)
        assertEquals(MenuStep.Choose(MenuAction.OpenChannel), menu.key(PlayerKey.Ok).second)
        assertEquals(MenuStep.Choose(MenuAction.Move), down.key(PlayerKey.Ok).second)
        assertEquals(MenuStep.Close, menu.key(PlayerKey.Back).second)
        assertEquals(menu, menu.key(PlayerKey.Left).first)
        assertEquals(MenuStep.None, menu.key(PlayerKey.Right).second)
    }

    private val five = listOf(PlayerButton.PlayPause, PlayerButton.Next, PlayerButton.Quality, PlayerButton.Subtitles, PlayerButton.Speed)

    @Test fun moveRight() {
        val r = moveButton(five, 1, +1)
        assertEquals(listOf(PlayerButton.PlayPause, PlayerButton.Quality, PlayerButton.Next, PlayerButton.Subtitles, PlayerButton.Speed), r.buttons)
        assertEquals(2, r.focusIndex)
    }

    @Test fun moveLeft() {
        val r = moveButton(five, 1, -1)
        assertEquals(PlayerButton.Next, r.buttons[0]); assertEquals(0, r.focusIndex)
    }

    @Test fun moveStopsAtTheEnds() {
        assertEquals(PanelEdit(five, 0), moveButton(five, 0, -1))
        assertEquals(PanelEdit(five, 4), moveButton(five, 4, +1))
    }

    @Test fun removeMiddleFocusesNext() {
        val r = removeButton(five, 1)
        assertEquals(listOf(PlayerButton.PlayPause, PlayerButton.Quality, PlayerButton.Subtitles, PlayerButton.Speed), r.buttons)
        assertEquals(1, r.focusIndex)
    }

    @Test fun removeLastFocusesPrevious() {
        val r = removeButton(five, 4)
        assertEquals(4, r.buttons.size); assertEquals(3, r.focusIndex)
    }

    @Test fun removeTheOnlyButtonFocusesMore() {
        val r = removeButton(listOf(PlayerButton.Mute), 0)
        assertTrue(r.buttons.isEmpty()); assertEquals(0, r.focusIndex)
    }

    @Test fun removeOutOfRangeChangesNothing() = assertEquals(PanelEdit(five, 9), removeButton(five, 9))

    @Test fun addGoesLast() {
        val r = addButton(five, PlayerButton.Mute)
        assertEquals(five + PlayerButton.Mute, r.buttons); assertNull(r.displaced)
    }

    @Test fun addAtTheLimitDisplacesTheLast() {
        val full = DEFAULT_BUTTONS
        assertEquals(PANEL_LIMIT, full.size)
        val r = addButton(full, PlayerButton.Mute)
        assertEquals(PlayerButton.Subscribe, r.displaced)
        assertEquals(full.dropLast(1) + PlayerButton.Mute, r.buttons)
        assertEquals(PANEL_LIMIT, r.buttons.size)
    }

    @Test fun addOfAButtonAlreadyThereChangesNothing() {
        val r = addButton(five, PlayerButton.Next)
        assertEquals(five, r.buttons); assertNull(r.displaced)
    }

    @Test fun aModalPanelDoesNotAutoHide() {
        val ui = PlayerUi(PanelFocus.BUTTONS, 0, 6)
        assertTrue(shouldAutoHide(ui, playing = true, idleMs = PANEL_AUTO_HIDE_MS))
        assertFalse(shouldAutoHide(ui, playing = true, idleMs = PANEL_AUTO_HIDE_MS, modal = true))
    }

    @Test fun autoHideWaitsForMenuAndMove() {
        assertTrue(menuKeepsPanel(menuOpen = true, moving = false))
        assertTrue(menuKeepsPanel(menuOpen = false, moving = true))
        assertFalse(menuKeepsPanel(false, false))
    }
}
