package io.github.ivanosh.stmaterial.browse

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.RowModel
import io.github.ivanosh.stmaterial.logic.SubscriptionRowIds
import io.github.ivanosh.stmaterial.logic.VideoItem
import io.github.ivanosh.stmaterial.logic.buildRows

/**
 * Lays the Subscriptions tab out in [rows]: "Continue watching", "New in subscriptions" (the first page
 * of the feed), then the rest of the pages as a grid, "Earlier". The history and the feed arrive in any
 * order; nothing is shown twice. Membership is decided by [buildRows] when a list arrives, a card whose
 * progress changes later stays where it is.
 */
internal class SubscriptionsFeed(private val rows: MutableList<VideoRow>, private val title: (Int) -> String) {
    private var history: List<Video> = emptyList()
    private var firstPage: List<Video>? = null

    /** Videos the user removed during this visit; a late history or a cumulative page must not show them. */
    private val removed = HashSet<String>()

    /** The presenter sends one group again and again, with the next page added. Videos find their next page through it. */
    private var group: VideoGroup? = null
    private var historyGroup: VideoGroup? = null

    /** A new load of the feed. The history stays: it is fetched once per visit of the tab. */
    fun restart() {
        firstPage = null
        group = null
    }

    fun reset() {
        restart()
        history = emptyList()
        historyGroup = null
        removed.clear()
    }

    fun onHistory(videos: List<Video>, group: VideoGroup?) {
        history = visible(videos)
        historyGroup = group
        arrange()
    }

    /** Called after videos left [rows]: the row that may ask for a page can have changed. */
    fun onRowsChanged() = finish()

    /**
     * The video to continue the feed from when no row of it is left to ask: the last of its group, which
     * knows the next page. Null before the feed has arrived.
     */
    fun nextPageVideo(): Video? = group?.videos?.lastOrNull()

    fun onFeed(feedGroup: VideoGroup, videos: List<Video>, atStart: Boolean) {
        group = feedGroup
        if (firstPage == null) {
            firstPage = visible(videos)
            arrange()
        } else {
            addLater(videos, atStart)
        }
    }

    /** Call before [rows] lose the videos: a rebuild must not bring them back. */
    fun forget(predicate: (Video) -> Boolean) {
        (rows.flatMap { it.videos } + history + firstPage.orEmpty()).filter(predicate).mapTo(removed, ::videoKey)
        history = history.filterNot(predicate)
        firstPage = firstPage?.filterNot(predicate)
    }

    private fun arrange() {
        val feed = firstPage.orEmpty()
        val models = buildRows(feed.map(::item), history.map(::item))

        // Each row shows its own list's videos: a feed video that is also in the history belongs to the
        // feed group, which is the one that has the next page.
        val ordered = models.map { model ->
            rowFor(model, if (model.id == SubscriptionRowIds.CONTINUE) history else feed)
        }.toMutableList()
        val claimed = ordered.flatMapTo(HashSet()) { row -> row.videos.map(::videoKey) }
        rows.firstOrNull { it.id == SubscriptionRowIds.EARLIER }?.let { earlier ->
            earlier.drop(claimed)
            if (earlier.videos.isNotEmpty()) ordered.add(earlier)
        }
        rows.clear()
        rows.addAll(ordered)
        finish()
    }

    private fun rowFor(model: RowModel, source: List<Video>): VideoRow {
        val byId = source.associateBy(::videoKey)
        val existing = rows.firstOrNull { it.id == model.id }
        if (existing != null && existing.videos.map(::videoKey) == model.ids) return existing
        return VideoRow(model.id, title(model.titleRes), isGrid = false).also { row ->
            row.add(model.ids.mapNotNull(byId::get))
        }
    }

    private fun addLater(videos: List<Video>, atStart: Boolean) {
        val claimed = rows
            .filter { it.id != SubscriptionRowIds.EARLIER }
            .flatMapTo(HashSet()) { row -> row.videos.map(::videoKey) }
        val fresh = visible(videos).filter { videoKey(it) !in claimed }
        val earlier = rows.firstOrNull { it.id == SubscriptionRowIds.EARLIER }
        when {
            earlier != null -> earlier.add(fresh, atStart)
            fresh.isNotEmpty() -> rows.add(
                VideoRow(SubscriptionRowIds.EARLIER, title(R.string.material_row_earlier), isGrid = true).also { it.add(fresh) }
            )
            // A page of repeats: the last row may ask for the next one.
            else -> rows.lastOrNull()?.add(emptyList())
        }
        finish()
    }

    /**
     * The last row of the feed asks for the next page, and does it as soon as a card of it is focused: a
     * row has no cards below it to scroll to. The history row asks only when it is all there is, and then
     * the page is found through [nextPageVideo].
     */
    private fun finish() {
        val lastFeed = rows.lastOrNull { it.id != SubscriptionRowIds.CONTINUE }
        rows.forEach { row ->
            val asks = if (row.id == SubscriptionRowIds.CONTINUE) lastFeed == null else row === lastFeed
            row.pageable = asks
            row.eager = asks && !row.isGrid
            row.group = if (row.id == SubscriptionRowIds.CONTINUE) historyGroup else group
        }
    }

    private fun visible(videos: List<Video>): List<Video> = videos.filter { videoKey(it) !in removed }.distinctBy(::videoKey)

    private fun item(video: Video) = VideoItem(videoKey(video), percentOf(video))

    private fun percentOf(video: Video): Int = video.percentWatched.toInt().coerceAtLeast(0)
}
