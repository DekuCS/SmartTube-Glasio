package io.github.ivanosh.stmaterial.logic

/** More than a person types into a TV search; the service takes less than this anyway. */
const val SEARCH_MAX_LENGTH = 100

const val DIGIT_ROW = "1234567890"

/** The letter rows of the on-screen keyboard. The UI is English, the owner searches in Russian too. */
enum class KeyboardLanguage(val label: String, val rows: List<String>) {
    EN("EN", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")),
    RU("RU", listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбюё"));

    fun next(): KeyboardLanguage = entries[(ordinal + 1) % entries.size]
}

/** The query being typed. Every key returns a new value; a key that cannot apply returns the same text. */
data class SearchInput(val text: String = "", val limit: Int = SEARCH_MAX_LENGTH) {
    fun type(characters: String): SearchInput =
        if (text.length + characters.length > limit) this else copy(text = text + characters)

    /** Never first, never twice in a row: a query that starts with a space or has two is not one anybody meant. */
    fun space(): SearchInput = if (text.isEmpty() || text.endsWith(' ')) this else type(" ")

    fun backspace(): SearchInput = if (text.isEmpty()) this else copy(text = text.dropLast(1))

    /** What to search for, or null when there is nothing to search for. */
    val query: String? get() = text.trim().ifEmpty { null }

    val canSearch: Boolean get() = query != null
}
