package io.github.ivanosh.stmaterial.logic

import com.liskovsoft.smartyoutubetv2.tv.R

/** [progressPct] is whole percents watched, 0 when unknown. */
data class VideoItem(val id: String, val progressPct: Int)

data class RowModel(val id: String, val titleRes: Int, val ids: List<String>)

object SubscriptionRowIds {
    const val CONTINUE = "continue"
    const val NEW = "new"
    const val EARLIER = "earlier"
}

private const val CONTINUE_MIN_PCT = 5
private const val CONTINUE_MAX_PCT = 95

/**
 * "Continue watching" is the history with 5 to 95 percent watched. "New in subscriptions" is the first page of the
 * subscriptions as it is: a started video stays in its place there with its progress, so a new video never seems to
 * vanish (owner's choice, 2026-11-02). A row without videos is not made.
 */
fun buildRows(subscriptions: List<VideoItem>, history: List<VideoItem>): List<RowModel> {
    val resume = history
        .filter { it.progressPct in CONTINUE_MIN_PCT..CONTINUE_MAX_PCT }
        .map { it.id }
        .distinct()
    val fresh = subscriptions.map { it.id }.distinct()
    return listOfNotNull(
        RowModel(SubscriptionRowIds.CONTINUE, R.string.material_row_continue, resume).takeIf { resume.isNotEmpty() },
        RowModel(SubscriptionRowIds.NEW, R.string.material_row_new, fresh).takeIf { fresh.isNotEmpty() },
    )
}
