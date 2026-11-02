package io.github.ivanosh.stmaterial.logic

const val LEARN_MIN = -3
const val LEARN_MAX = 3

/** Auto-skip for a channel and category is off from this score up. One accidental undo must not switch it off. */
const val LEARN_OFF_AT = 2
const val LEARN_MAX_ENTRIES = 500

/** A rewind into a segment counts only within this long of its auto-skip, and only if it then plays [REWIND_PLAYS_MS]. */
const val REWIND_WINDOW_MS = 30_000L
const val REWIND_PLAYS_MS = 3_000L

fun autoSkipOn(score: Int): Boolean = score < LEARN_OFF_AT

/**
 * What was learned about each channel and category: +1 for an undo or a rewind into a skipped segment, -1 for
 * RIGHT on the Skip chip. Oldest-touched first, capped, and an entry back at 0 is dropped.
 */
class LearnedScores(entries: Map<Pair<String, String>, Int> = emptyMap()) {
    private val scores = LinkedHashMap<Pair<String, String>, Int>(entries)

    fun score(channel: String, category: String): Int = scores[channel to category] ?: 0

    /** The new state of auto-skip when this changed it, else null. */
    fun add(channel: String, category: String, delta: Int): Boolean? {
        val key = channel to category
        val before = score(channel, category)
        val after = (before + delta).coerceIn(LEARN_MIN, LEARN_MAX)
        scores.remove(key)
        if (after != 0) scores[key] = after // put last: the most recently touched
        while (scores.size > LEARN_MAX_ENTRIES) scores.remove(scores.keys.first())
        val was = autoSkipOn(before)
        val now = autoSkipOn(after)
        return if (was != now) now else null
    }

    /** How many channels have something learned. */
    fun channelCount(): Int = scores.keys.map { it.first }.distinct().size

    fun size(): Int = scores.size

    /** One line per entry: channel, category and score, tab-separated. Ids and categories hold no tab or newline. */
    fun serialize(): String = scores.entries.joinToString("\n") { "${it.key.first}\t${it.key.second}\t${it.value}" }

    companion object {
        fun parse(text: String?): LearnedScores {
            val entries = LinkedHashMap<Pair<String, String>, Int>()
            text.orEmpty().lines().forEach { line ->
                val parts = line.split('\t')
                val score = parts.getOrNull(2)?.toIntOrNull()
                if (parts.size == 3 && parts[0].isNotEmpty() && parts[1].isNotEmpty() && score != null && score != 0) {
                    entries[parts[0] to parts[1]] = score.coerceIn(LEARN_MIN, LEARN_MAX)
                }
            }
            return LearnedScores(entries.entries.toList().takeLast(LEARN_MAX_ENTRIES).associate { it.key to it.value })
        }
    }
}

/**
 * Finds the rewinds into a segment that was just auto-skipped, which the owner then watches: a position inside it
 * within [REWIND_WINDOW_MS] of the skip, and [REWIND_PLAYS_MS] further on without leaving it. Each skip counts once.
 */
class RewindWatch {
    private class Pending(val span: SkipSpan, val skippedAtMs: Long) {
        var enteredPosMs: Long? = null
    }

    private val pending = mutableListOf<Pending>()

    fun onAutoSkipped(spans: List<SkipSpan>, nowMs: Long) {
        spans.forEach { span ->
            pending.removeAll { it.span.startMs == span.startMs && it.span.endMs == span.endMs }
            pending += Pending(span, nowMs)
        }
    }

    /** The segments that earned a +1 at this check. */
    fun onPoll(positionMs: Long, nowMs: Long): List<SkipSpan> {
        val earned = mutableListOf<SkipSpan>()
        val it = pending.iterator()
        while (it.hasNext()) {
            val p = it.next()
            val inside = positionMs >= p.span.startMs && positionMs < p.span.endMs
            val entered = p.enteredPosMs
            when {
                !inside -> {
                    p.enteredPosMs = null
                    if (nowMs - p.skippedAtMs > REWIND_WINDOW_MS) it.remove()
                }
                entered == null -> if (nowMs - p.skippedAtMs <= REWIND_WINDOW_MS) p.enteredPosMs = positionMs else it.remove()
                positionMs - entered >= REWIND_PLAYS_MS -> {
                    earned += p.span
                    it.remove()
                }
            }
        }
        return earned
    }

    fun forget(spans: List<SkipSpan>) {
        pending.removeAll { p -> spans.any { it.startMs == p.span.startMs && it.endMs == p.span.endMs } }
    }
}
