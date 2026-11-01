package io.github.ivanosh.stmaterial.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegendTest {
    private val green = 0xFF00FF00.toInt()
    private val cyan = 0xFF00FFFF.toInt()
    private val magenta = 0xFFFF00FF.toInt()
    private val names = mapOf(green to "Sponsor", cyan to "Intro", magenta to "Interaction")

    private fun mark(argb: Int, chapter: Boolean = false) = ProgressMark(0.1f, 0.2f, chapter, argb)

    @Test
    fun distinctInOrderOfFirstAppearanceOnTheBar() {
        val marks = listOf(mark(magenta).copy(start = 0.7f), mark(green).copy(start = 0.1f), mark(magenta).copy(start = 0.9f), mark(cyan).copy(start = 0.4f))
        assertEquals(listOf("Sponsor", "Intro", "Interaction"), legendEntries(marks, names::get).map { it.name })
    }

    @Test
    fun givenOrderBreaksTiesAtTheSameStart() {
        assertEquals(listOf("Intro", "Sponsor"), legendEntries(listOf(mark(cyan), mark(green)), names::get).map { it.name })
    }

    @Test
    fun chaptersAreNotInTheLegend() {
        assertTrue(legendEntries(listOf(mark(0xFF000000.toInt(), chapter = true)), names::get).isEmpty())
    }

    @Test
    fun aColourWithoutANameIsLeftOut() {
        assertEquals(listOf("Sponsor"), legendEntries(listOf(mark(0xFF123456.toInt()), mark(green)), names::get).map { it.name })
    }

    @Test
    fun threeEntriesFitTheRowAndNineMayNot() {
        val three = legendEntries(listOf(mark(green), mark(cyan), mark(magenta)), names::get)
        assertTrue(legendFits(three, availableDp = 1500f))
        val nine = (1..9).map { LegendEntry(it, "Self-promo long") }
        assertFalse(legendFits(nine, availableDp = 1000f))
        assertTrue(legendFits(emptyList(), availableDp = 0f))
    }
}
