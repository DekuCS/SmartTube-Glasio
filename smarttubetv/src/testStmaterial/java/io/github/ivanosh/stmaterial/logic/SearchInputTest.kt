package io.github.ivanosh.stmaterial.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchInputTest {
    private fun typed(vararg parts: String): SearchInput = parts.fold(SearchInput()) { input, part ->
        when (part) {
            " " -> input.space()
            "<" -> input.backspace()
            else -> input.type(part)
        }
    }

    @Test
    fun lettersAreAppended() {
        assertEquals("cat", typed("c", "a", "t").text)
    }

    @Test
    fun cyrillicLettersAreAppended() {
        assertEquals("кот", typed("к", "о", "т").text)
    }

    @Test
    fun backspaceRemovesTheLastCharacter() {
        assertEquals("ca", typed("c", "a", "t", "<").text)
    }

    @Test
    fun backspaceOnEmptyDoesNothing() {
        assertEquals("", typed("<", "<").text)
    }

    @Test
    fun spaceSeparatesWords() {
        assertEquals("a b", typed("a", " ", "b").text)
    }

    @Test
    fun spaceIsIgnoredAtTheStartAndAfterAnotherSpace() {
        assertEquals("", typed(" ", " ").text)
        assertEquals("a ", typed("a", " ", " ").text)
    }

    @Test
    fun lengthIsLimited() {
        val full = SearchInput(limit = 3)
        val input = listOf("a", "b", "c", "d").fold(full) { acc, c -> acc.type(c) }
        assertEquals("abc", input.text)
        assertEquals("abc", input.space().text)
    }

    @Test
    fun backspaceFreesRoomAgain() {
        val input = SearchInput(limit = 2).type("a").type("b").backspace().type("c")
        assertEquals("ac", input.text)
    }

    @Test
    fun defaultLimitHoldsALongQuery() {
        var input = SearchInput()
        repeat(SEARCH_MAX_LENGTH + 20) { input = input.type("x") }
        assertEquals(SEARCH_MAX_LENGTH, input.text.length)
    }

    @Test
    fun emptyAndBlankQueriesDoNotSearch() {
        assertNull(SearchInput().query)
        assertNull(SearchInput("   ").query)
        assertTrue(!SearchInput().canSearch)
    }

    @Test
    fun queryIsTrimmed() {
        val input = typed("a", "b", " ")
        assertEquals("ab ", input.text)
        assertEquals("ab", input.query)
        assertTrue(input.canSearch)
    }

    @Test
    fun layoutsSwitchInTurn() {
        assertEquals(KeyboardLanguage.RU, KeyboardLanguage.EN.next())
        assertEquals(KeyboardLanguage.EN, KeyboardLanguage.RU.next())
    }

    @Test
    fun englishLayoutHasAllLettersOnce() {
        val letters = KeyboardLanguage.EN.rows.joinToString("")
        assertEquals(('a'..'z').toSet(), letters.toSet())
        assertEquals(26, letters.length)
    }

    @Test
    fun russianLayoutHasAllLettersOnce() {
        val letters = KeyboardLanguage.RU.rows.joinToString("")
        assertEquals((('а'..'я') + 'ё').toSet(), letters.toSet())
        assertEquals(33, letters.length)
    }

    @Test
    fun digitsComeInOneRow() {
        assertEquals("1234567890", DIGIT_ROW)
    }
}

class SuggestionsTest {
    @Test
    fun duplicatesAndNullsAreDropped() {
        assertEquals(listOf("a", "b"), cleanSuggestions(listOf("a", null, "b", "a", "", "  "), 8))
    }

    @Test
    fun limitIsHeldAfterDedupe() {
        assertEquals(listOf("a", "b"), cleanSuggestions(listOf("a", "a", "b", "c"), 2))
    }

    @Test
    fun nullListIsEmpty() {
        assertEquals(emptyList<String>(), cleanSuggestions(null, 8))
    }

    @Test
    fun answerCountsOnlyWhileTheQueryIsUnchanged() {
        assertTrue(isCurrentAnswer(asked = "ca", now = "ca"))
        assertTrue(!isCurrentAnswer(asked = "ca", now = "cat"))
    }
}
