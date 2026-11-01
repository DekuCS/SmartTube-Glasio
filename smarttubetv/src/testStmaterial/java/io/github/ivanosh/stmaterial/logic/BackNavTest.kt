package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavTest {
    private val tabs = listOf(10, 20, 30)

    @Test
    fun contentGoesToTabs() {
        assertEquals(BackStep.FOCUS_TABS, backStep(contentFocused = true, selected = 20, start = 10, tabs = tabs))
    }

    @Test
    fun otherTabGoesToStart() {
        assertEquals(BackStep.SELECT_START, backStep(false, 30, 10, tabs))
    }

    @Test
    fun startTabLeavesToTheActivity() {
        assertEquals(BackStep.LEAVE, backStep(false, 10, 10, tabs))
    }

    @Test
    fun startThatIsNotATabLeaves() {
        assertEquals(BackStep.LEAVE, backStep(false, 20, 99, tabs))
    }

    @Test
    fun nothingSelectedLeaves() {
        assertEquals(BackStep.LEAVE, backStep(false, null, 10, tabs))
    }
}
