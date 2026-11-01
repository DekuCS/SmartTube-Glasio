package io.github.ivanosh.stmaterial.player

/** A swatch and a name for one SponsorBlock category on the bar. */
data class LegendEntry(val argb: Int, val name: String)

/**
 * The categories present on the bar, each once, in order of first appearance along it. Chapters are not
 * categories. [nameOf] gives the name of a mark colour, null for one that is none of the categories.
 */
fun legendEntries(marks: List<ProgressMark>, nameOf: (Int) -> String?): List<LegendEntry> {
    val result = LinkedHashMap<Int, LegendEntry>()
    for (mark in marks.filter { !it.isChapter }.sortedBy { it.start }) {
        if (mark.argb in result) continue
        val name = nameOf(mark.argb) ?: continue
        result[mark.argb] = LegendEntry(mark.argb, name)
    }
    return result.values.toList()
}

private const val SWATCH_DP = 28f
private const val SWATCH_GAP_DP = 12f
private const val ENTRY_GAP_DP = 24f
private const val CHAR_DP = 13f
/** The label of "Marked by": the legend starts after it. */
private const val LEAD_DP = 0f

/** Whether the legend fits in [availableDp] on one line, by a rough text width: it is decided once, not measured per frame. */
fun legendFits(entries: List<LegendEntry>, availableDp: Float): Boolean {
    val width = entries.sumOf { (SWATCH_DP + SWATCH_GAP_DP + it.name.length * CHAR_DP + ENTRY_GAP_DP).toDouble() }.toFloat()
    return LEAD_DP + width <= availableDp
}
