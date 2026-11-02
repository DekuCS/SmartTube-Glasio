package io.github.ivanosh.stmaterial.logic

/** Whole percent of [done] in [total], 0 to 100; 0 when the total is not known. */
fun percentOf(done: Long, total: Long): Int =
    if (total <= 0) 0 else (done.coerceIn(0, total).toDouble() * 100 / total).toInt().coerceIn(0, 100)

/** Reports a percent only when it differs from the last one reported, so a copy loop posts at most 101 times. */
class PercentGate {
    private var last = -1

    /** The percent to show now, or null when it has not changed since the last one given. */
    fun next(done: Long, total: Long): Int? {
        val percent = percentOf(done, total)
        if (percent == last) return null
        last = percent
        return percent
    }
}
