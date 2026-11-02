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
