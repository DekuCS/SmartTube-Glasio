package io.github.ivanosh.stmaterial.logic

import kotlin.math.max
import kotlin.math.min

/**
 * One rounded piece of the progress track, in pixels along the bar. [leftFull] / [rightFull]: that end is
 * a full round end (the ends of the bar and the cuts at the handle), else a chapter gap's slightly rounded one.
 */
data class TrackPiece(val left: Float, val right: Float, val active: Boolean, val leftFull: Boolean, val rightFull: Boolean)

private const val STRIDE = 5

/** Floats [layoutTrack] needs for a bar with [chapters] chapter starts: every segment has at most an active and an inactive piece. */
fun trackBufferSize(chapters: Int): Int = (chapters + 1) * 2 * STRIDE

fun pieceAt(buffer: FloatArray, index: Int): TrackPiece {
    val i = index * STRIDE
    return TrackPiece(buffer[i], buffer[i + 1], buffer[i + 2] != 0f, buffer[i + 3] != 0f, buffer[i + 4] != 0f)
}

/** Writes a piece at [index] and returns the next index: a plain function, so no closure is allocated per call. */
private fun putPiece(out: FloatArray, index: Int, left: Float, right: Float, active: Boolean, leftFull: Boolean, rightFull: Boolean): Int {
    val i = index * STRIDE
    out[i] = left
    out[i + 1] = right
    out[i + 2] = if (active) 1f else 0f
    out[i + 3] = if (leftFull) 1f else 0f
    out[i + 4] = if (rightFull) 1f else 0f
    return index + 1
}

/**
 * Cuts the track of [width] into pieces: the chapter starts ([chapterStarts], ascending fractions strictly
 * between 0 and 1) leave gaps of [gap], and the handle of [handleWidth] leaves [handleGap] clear on each side.
 * The handle stays on the track at 0 and 100%. Writes into [out] without allocating; returns the piece count.
 * Pieces of no width are left out, and pieces are in order along the bar.
 */
fun layoutTrack(
    width: Float,
    progress: Float,
    chapterStarts: FloatArray,
    chapters: Int,
    gap: Float,
    handleWidth: Float,
    handleGap: Float,
    out: FloatArray,
): Int {
    val half = handleWidth / 2
    val center = handleCenter(width, progress, handleWidth)
    val activeEnd = center - half - handleGap
    val inactiveStart = center + half + handleGap
    var count = 0

    var segmentStart = 0f
    var startFull = true
    for (k in 0..chapters) {
        val last = k == chapters
        val boundary = if (last) width else chapterStarts[k] * width
        val end = if (last) width else boundary - gap / 2
        val endFull = last
        if (end > segmentStart) {
            val activeRight = min(end, activeEnd)
            if (activeRight > segmentStart) count = putPiece(out, count, segmentStart, activeRight, true, startFull, if (activeRight < end) true else endFull)
            val inactiveLeft = max(segmentStart, inactiveStart)
            if (end > inactiveLeft) count = putPiece(out, count, inactiveLeft, end, false, if (inactiveLeft > segmentStart) true else startFull, endFull)
        }
        segmentStart = max(segmentStart, boundary + gap / 2)
        startFull = false
    }
    return count
}

/** Where the middle of the handle is: on the track at every progress. */
fun handleCenter(width: Float, progress: Float, handleWidth: Float): Float =
    if (width <= handleWidth) width / 2 else (progress.coerceIn(0f, 1f) * width).coerceIn(handleWidth / 2, width - handleWidth / 2)
