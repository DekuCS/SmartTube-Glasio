package io.github.ivanosh.stmaterial.browse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusAfterReloadTest {
    private val old = listOf(CardRow("r1", listOf("a", "b", "c", "d")), CardRow("r2", listOf("e", "f")))

    @Test
    fun sameCardInTheSameRowStays() {
        val new = listOf(CardRow("r1", listOf("x", "a", "b", "c", "d")), CardRow("r2", listOf("e", "f")))
        assertEquals(FocusKey("r1", "c"), focusAfterReload(old, FocusKey("r1", "c"), new))
    }

    @Test
    fun cardFoundInARowWithAnotherId() {
        val new = listOf(CardRow("n1", listOf("a", "b", "c")), CardRow("n2", listOf("e", "f")))
        assertEquals(FocusKey("n2", "f"), focusAfterReload(old, FocusKey("r2", "f"), new))
    }

    @Test
    fun goneCardTakesTheNearestPositionOfItsRow() {
        val new = listOf(CardRow("r1", listOf("a", "x", "y")), CardRow("r2", listOf("e", "f")))
        assertEquals(FocusKey("r1", "y"), focusAfterReload(old, FocusKey("r1", "d"), new))
        assertEquals(FocusKey("r1", "x"), focusAfterReload(old, FocusKey("r1", "b"), new))
    }

    @Test
    fun goneCardOfARenamedRowUsesTheRowAtTheSameIndex() {
        val new = listOf(CardRow("n1", listOf("p", "q")), CardRow("n2", listOf("r", "s", "t")))
        assertEquals(FocusKey("n2", "s"), focusAfterReload(old, FocusKey("r2", "f"), new))
    }

    @Test
    fun rowThatIsNoMoreFallsToTheLastRow() {
        val new = listOf(CardRow("r1", listOf("p", "q")))
        assertEquals(FocusKey("r1", "q"), focusAfterReload(old, FocusKey("r2", "f"), new))
    }

    @Test
    fun emptyRowsAreSkippedAndNothingLeftIsNull() {
        val new = listOf(CardRow("r1", emptyList()), CardRow("r2", listOf("z")))
        assertEquals(FocusKey("r2", "z"), focusAfterReload(old, FocusKey("r1", "a"), new))
        assertNull(focusAfterReload(old, FocusKey("r1", "a"), emptyList()))
    }

    @Test
    fun focusOnAnUnknownRowOfOldStillFindsItsCard() {
        val new = listOf(CardRow("r1", listOf("a", "b")))
        assertEquals(FocusKey("r1", "b"), focusAfterReload(emptyList(), FocusKey("zz", "b"), new))
    }
}
