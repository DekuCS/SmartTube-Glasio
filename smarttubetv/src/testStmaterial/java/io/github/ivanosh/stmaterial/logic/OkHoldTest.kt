package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class OkHoldTest {
    // Nine items: eight buttons and More. Subscribe is the eighth, at index 7.
    private val onSubscribe = PlayerUi(PanelFocus.BUTTONS, 7, 9)
    private val onNext = PlayerUi(PanelFocus.BUTTONS, 1, 9)
    private val onMore = PlayerUi(PanelFocus.BUTTONS, 8, 9)

    @Test
    fun aShortPressActsOnReleaseOnly() {
        val (held, down) = OkHold().down(onSubscribe, holdable = true, repeating = false)
        assertEquals(OkAction.Swallow, down)
        val (done, up) = held.up()
        assertEquals(OkAction.Press, up)
        assertEquals(7, held.index)
        assertEquals(OkHold(), done)
    }

    @Test
    fun aHoldFiresOnceOnTheFirstRepeatAndNotOnRelease() {
        var state = OkHold().down(onSubscribe, true, repeating = false).first
        val actions = mutableListOf<OkAction>()
        repeat(4) {
            val (next, action) = state.down(onSubscribe, true, repeating = true)
            state = next
            actions += action
        }
        assertEquals(listOf(OkAction.Hold, OkAction.Swallow, OkAction.Swallow, OkAction.Swallow), actions)
        assertEquals(OkAction.Swallow, state.up().second)
    }

    @Test
    fun aHoldOnAnotherButtonIsOnePlainPressAndItsRepeatsAreSwallowed() {
        val (state, down) = OkHold().down(onNext, holdable = false, repeating = false)
        assertEquals(OkAction.Normal, down)
        assertEquals(OkAction.Swallow, state.down(onNext, false, repeating = true).second)
        assertEquals(OkAction.Swallow, state.up().second)
    }

    @Test
    fun moreAndTheProgressBarAreNeverHeld() {
        assertEquals(OkAction.Normal, OkHold().down(onMore, holdable = true, repeating = false).second)
        assertEquals(OkAction.Normal, OkHold().down(onSubscribe.copy(focus = PanelFocus.PROGRESS), true, false).second)
    }

    @Test
    fun theOkThatOpensThePanelNeverActsOnSubscribeEvenWhenHeldThroughIt() {
        // Hidden panel: the down event opens it and toggles play, it is not tracked.
        val hidden = PlayerUi(PanelFocus.HIDDEN, 0, 9)
        val (state, down) = OkHold().down(hidden, holdable = true, repeating = false)
        assertEquals(OkAction.Normal, down)
        // The panel is now open with Subscribe under the cursor; the repeats and the release must not act.
        assertEquals(OkAction.Swallow, state.down(onSubscribe.copy(buttonIndex = 0), true, repeating = true).second)
        assertEquals(OkAction.Swallow, state.up().second)
    }

    @Test
    fun aReleaseWithoutATrackedPressDoesNothing() {
        assertEquals(OkAction.Swallow, OkHold().up().second)
    }

    @Test
    fun aFreshPressAfterAHoldWhoseReleaseWasNeverSeenStartsOver() {
        val held = OkHold(7, fired = true)
        val (state, action) = held.down(onSubscribe, true, repeating = false)
        assertEquals(OkAction.Swallow, action)
        assertEquals(OkAction.Press, state.up().second)
    }
}
