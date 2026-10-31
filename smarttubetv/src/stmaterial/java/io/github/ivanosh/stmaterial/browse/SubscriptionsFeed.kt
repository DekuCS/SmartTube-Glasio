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

    /** A new load of the feed. The history stays: it is fetched once per visit of the tab. */
    fun restart() {
        firstPage = null
        group = null
    }

    fun reset() {
        restart()
        history = emptyList()
        removed.clear()
    }

    fun onHistory(videos: List<Video>, historyGroup: VideoGroup?) {
        history = visible(videos)
        arrange()
        rows.firstOrNull { it.id == SubscriptionRowIds.CONTINUE }?.group = historyGroup
    }

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
        val byId = HashMap<String, Video>()
        history.forEach { byId.putIfAbsent(videoKey(it), it) }
        feed.forEach { byId.putIfAbsent(videoKey(it), it) }
        val models = buildRows(feed.map(::item), history.map(::item))

        val ordered = models.map { model -> rowFor(model, byId) }.toMutableList()
        val claimed = ordered.flatMapTo(HashSet()) { row -> row.videos.map(::videoKey) }
        rows.firstOrNull { it.id == SubscriptionRowIds.EARLIER }?.let { earlier ->
            earlier.drop(claimed)
            if (earlier.videos.isNotEmpty()) ordered.add(earlier)
        }
        rows.clear()
        rows.addAll(ordered)
        finish()
    }

    private fun rowFor(model: RowModel, byId: Map<String, Video>): VideoRow {
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
            else -> rows.lastOrNull { it.id != SubscriptionRowIds.CONTINUE }?.add(emptyList())
        }
        finish()
    }

    /** Only the last row of the feed has a next page, and the history row never has one. */
    private fun finish() {
        rows.forEach { row ->
            row.pageable = row.id != SubscriptionRowIds.CONTINUE && row === rows.last()
            if (row.id != SubscriptionRowIds.CONTINUE) row.group = group
        }
    }

    private fun visible(videos: List<Video>): List<Video> = videos.filter { videoKey(it) !in removed }.distinctBy(::videoKey)

    private fun item(video: Video) = VideoItem(videoKey(video), percentOf(video))

    private fun percentOf(video: Video): Int = video.percentWatched.toInt().coerceAtLeast(0)
}
