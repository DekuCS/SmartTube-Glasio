package io.github.ivanosh.stmaterial.logic

/** The chip after an automatic SponsorBlock skip stays this long, and so does the undo. */
const val SKIP_CHIP_MS = 5_000L

/** A second skip within this much of where the chain ended joins the chain (upstream groups skips by the same 3 s). */
const val CHAIN_GAP_MS = 3_000L

/** A SponsorBlock segment. The [category] is the SponsorBlock id and takes no part in telling segments apart by time. */
data class SkipSpan(val startMs: Long, val endMs: Long, val category: String = "")

/** What the chip shows: it names the last skip of a chain, and the undo goes back to the start of the first. */
data class SkipChip(val chainStartMs: Long, val last: SkipSpan, val shownAtMs: Long)

/**
 * What was skipped and what was undone for one video. Times are a monotonic clock in ms. The controller asks
 * [maySkip] before an automatic skip, and tells [onAutoSkip] after it.
 */
class SkipLedger {
    private val undone = mutableSetOf<SkipSpan>()
    private val skippedAt = mutableMapOf<SkipSpan, Long>()
    private var chipSpans = emptyList<SkipSpan>()
    private var current: SkipChip? = null

    /**
     * False for a segment the owner undid (for good, this video), and for one skipped within the chip window:
     * a manual rewind into it plays it, so a missed chip costs nothing. Later, a rewind skips again.
     */
    fun maySkip(span: SkipSpan, nowMs: Long): Boolean {
        if (span in undone) return false
        val at = skippedAt[span] ?: return true
        return nowMs - at >= SKIP_CHIP_MS
    }

    /** [spans] are the segments one skip jumped over, in order. Returns the chip to show. */
    fun onAutoSkip(spans: List<SkipSpan>, nowMs: Long): SkipChip? {
        if (spans.isEmpty()) return chip(nowMs)
        val live = chip(nowMs)
        val joins = live != null && spans.first().startMs in live.chainStartMs..(live.last.endMs + CHAIN_GAP_MS)
        chipSpans = if (joins) chipSpans + spans else spans
        spans.forEach { skippedAt[it] = nowMs }
        val chain = SkipChip(if (joins) live!!.chainStartMs else spans.first().startMs, spans.last(), nowMs)
        current = chain
        return chain
    }

    fun chip(nowMs: Long): SkipChip? = current?.takeIf { nowMs - it.shownAtMs < SKIP_CHIP_MS }

    /** Where the undo goes, or null when no chip is live. The chain is then undone for good. */
    fun undo(nowMs: Long): Long? {
        val live = chip(nowMs) ?: return null
        undone.addAll(chipSpans)
        chipSpans.forEach { skippedAt.remove(it) }
        chipSpans = emptyList()
        current = null
        return live.chainStartMs
    }

    fun clear() {
        undone.clear()
        skippedAt.clear()
        chipSpans = emptyList()
        current = null
    }
}

/** A point, not a stretch: there is nothing to skip. The SponsorBlock id of the Highlight category. */
const val HIGHLIGHT_CATEGORY = "poi_highlight"

/** The segments under the position, from the one it is in to the end of the chain of adjacent ones. */
data class SkipTarget(val chain: List<SkipSpan>) {
    val first get() = chain.first()
    val endMs get() = chain.last().endMs
}

/** What the chip slot shows. The undo chip, while it lives, wins. */
sealed interface ChipChoice {
    data class Undo(val chip: SkipChip) : ChipChoice
    data class Skip(val target: SkipTarget) : ChipChoice
}

/** The segment the position is in, whatever its category or action, but not a Highlight and not an empty one. */
fun skipTargetAt(positionMs: Long, spans: List<SkipSpan>): SkipTarget? {
    val sorted = spans.filter { it.category != HIGHLIGHT_CATEGORY && it.endMs > it.startMs }.sortedBy { it.startMs }
    val at = sorted.indexOfFirst { positionMs >= it.startMs && positionMs < it.endMs }
    if (at < 0) return null
    val chain = mutableListOf(sorted[at])
    for (next in sorted.drop(at + 1)) {
        val end = chain.last().endMs
        if (next.startMs > end + CHAIN_GAP_MS) break
        if (next.endMs > end) chain += next
    }
    return SkipTarget(chain)
}

fun chooseChip(undo: SkipChip?, target: SkipTarget?): ChipChoice? = when {
    undo != null -> ChipChoice.Undo(undo)
    target != null -> ChipChoice.Skip(target)
    else -> null
}

/** Where RIGHT jumps to, or null when the position is not inside the target (a stale chip). */
fun skipJumpTo(target: SkipTarget?, positionMs: Long): Long? =
    target?.takeIf { positionMs >= it.first.startMs && positionMs < it.endMs }?.endMs
