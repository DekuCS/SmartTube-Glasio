package io.github.ivanosh.stmaterial.browse

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The quiet reload of the Subscriptions tab: feed and history rows, the grid, focus and removed videos. */
class SubscriptionsQuietReloadTest {
    private val subs = BrowseSection(MediaGroup.TYPE_SUBSCRIPTIONS, "Subs", BrowseSection.TYPE_GRID, -1)
    private var time = 1_000L
    private val loads = mutableListOf<Int>()

    private fun video(id: String, pct: Float = -1f) = Video().apply { videoId = id; percentWatched = pct }

    private fun feed(action: Int, vararg ids: String): VideoGroup =
        VideoGroup.from(ids.map { video(it) }, subs, 0).apply { setAction(action) }

    private fun historyOf(vararg entries: Pair<String, Float>): VideoGroup =
        VideoGroup.from(entries.map { video(it.first, it.second) }, null, 0)

    private fun state() = BrowseState(onSubscriptionsLoad = { loads.add(it) }, clock = { time }, rowTitle = { "t$it" }) { it.author }.apply {
        sections.add(subs)
        selectTab(subs.id)
    }

    private fun BrowseState.layout() = rows.map { it.id }
    private fun BrowseState.ids(id: String) = rows.first { it.id == id }.videos.map { it.videoId }

    /** Loaded with history h1 (50 percent), feed s1 h1 s2, and a second page s3 in "Earlier". */
    private fun loaded(): BrowseState = state().also { state ->
        state.updateHistory(historyOf("h1" to 50f), state.epoch)
        state.updateSection(feed(VideoGroup.ACTION_REPLACE, "s1", "h1", "s2"))
        state.updateSection(feed(VideoGroup.ACTION_APPEND, "s3"))
        state.endReload()
        loads.clear()
    }

    @Test
    fun swapReplacesTheGridPagesAndAsksForTheHistoryOnce() {
        val state = loaded()
        assertEquals(listOf("continue", "new", "earlier"), state.layout())
        val epoch = state.epoch
        assertTrue(state.beginQuietReload())
        state.updateSection(feed(VideoGroup.ACTION_REPLACE))
        assertEquals(listOf("continue", "new", "earlier"), state.layout())
        assertTrue(loads.isEmpty())
        state.updateSection(feed(VideoGroup.ACTION_APPEND, "n1", "s1", "h1"))
        assertEquals(listOf(epoch), loads)
        assertEquals(epoch, state.epoch)
        assertEquals(listOf("continue", "new"), state.layout())
        assertEquals(listOf("n1", "s1", "h1"), state.ids("new"))
        assertFalse(state.quiet)
        // The next page of the new answer makes Earlier again, and not with the old videos.
        state.updateSection(feed(VideoGroup.ACTION_APPEND, "s4"))
        assertEquals(listOf("continue", "new", "earlier"), state.layout())
        assertEquals(listOf("s4"), state.ids("earlier"))
    }

    @Test
    fun historyAfterTheSwapUpdatesContinueWithoutDuplicates() {
        val state = loaded()
        state.beginQuietReload()
        state.updateSection(feed(VideoGroup.ACTION_REPLACE, "n1", "s1", "h2"))
        // The old history stays until the new one arrives.
        assertEquals(listOf("h1"), state.ids("continue"))
        state.updateHistory(historyOf("h2" to 30f, "h1" to 99f), state.epoch)
        assertEquals(listOf("continue", "new"), state.layout())
        assertEquals(listOf("h2"), state.ids("continue"))
        assertEquals(listOf("n1", "s1", "h2"), state.ids("new"))
    }

    @Test
    fun historyBeforeTheSwapIsKept() {
        val state = loaded()
        state.beginQuietReload()
        state.updateHistory(historyOf("h2" to 30f), state.epoch)
        assertEquals(listOf("h2"), state.ids("continue"))
        assertEquals(listOf("s1", "h1", "s2"), state.ids("new"))
        state.updateSection(feed(VideoGroup.ACTION_REPLACE, "n1", "h2"))
        assertEquals(listOf("continue", "new"), state.layout())
        assertEquals(listOf("h2"), state.ids("continue"))
        assertEquals(listOf("n1", "h2"), state.ids("new"))
    }

    @Test
    fun videosTheUserRemovedStayGoneAcrossTheReload() {
        val state = loaded()
        state.updateSection(feed(VideoGroup.ACTION_REMOVE, "s2", "h1"))
        assertEquals(listOf("continue", "new", "earlier").filter { it != "continue" }, state.layout())
        state.beginQuietReload()
        state.updateSection(feed(VideoGroup.ACTION_REPLACE, "n1", "s2", "s1", "h1"))
        assertEquals(listOf("n1", "s1"), state.ids("new"))
        state.updateHistory(historyOf("h1" to 50f, "h5" to 40f), state.epoch)
        assertEquals(listOf("h5"), state.ids("continue"))
    }

    @Test
    fun staleHistoryIsIgnored() {
        val state = loaded()
        val old = state.epoch
        state.restartTab()
        state.updateSection(feed(VideoGroup.ACTION_REPLACE, "s1"))
        state.updateHistory(historyOf("h9" to 50f), old)
        assertEquals(listOf("new"), state.layout())
        // Not for this tab any more either.
        state.updateHistory(historyOf("h9" to 50f), state.epoch - 1)
        assertEquals(listOf("new"), state.layout())
    }

    @Test
    fun aStartedVideoStaysInNewAfterTheReload() {
        val state = loaded()
        state.beginQuietReload()
        val answer = VideoGroup.from(listOf(video("s1", 40f), video("s2")), subs, 0).apply { setAction(VideoGroup.ACTION_REPLACE) }
        state.updateSection(answer)
        state.updateHistory(historyOf("s1" to 40f), state.epoch)
        assertEquals(listOf("s1", "s2"), state.ids("new"))
        assertEquals(listOf("s1"), state.ids("continue"))
    }

    @Test
    fun continueWatchingKeepsFocusWhenTheNewHistoryArrives() {
        val state = loaded()
        state.updateHistory(historyOf("h1" to 50f, "h2" to 30f), state.epoch)
        state.onCardFocused(FocusKey("continue", "h2"), 0)
        state.beginQuietReload()
        state.updateSection(feed(VideoGroup.ACTION_REPLACE, "n1", "s1"))
        state.endReload()
        state.pendingFocus = null
        // h2 is still in the old history shown at the swap; the new history moves it.
        state.updateHistory(historyOf("h3" to 10f, "h1" to 50f, "h2" to 30f), state.epoch)
        assertEquals(FocusKey("continue", "h2"), state.pendingFocus)
        // When the focused card is gone, the nearest card of the same row takes focus.
        state.updateHistory(historyOf("h3" to 10f, "h1" to 50f), state.epoch)
        assertEquals(FocusKey("continue", "h1"), state.pendingFocus)
    }

    @Test
    fun continueAndNewAreTheFirstPageButEarlierIsNot() {
        val state = loaded()
        state.onCardFocused(FocusKey("continue", "h1"), 0)
        assertTrue(state.canQuietReload())
        state.onCardFocused(FocusKey("new", "s2"), 1)
        assertTrue(state.canQuietReload())
        state.onCardFocused(FocusKey("earlier", "s3"), 2)
        assertFalse(state.canQuietReload())
        assertFalse(state.beginQuietReload())
        assertNull(state.pendingFocus)
    }

    @Test
    fun continueRowArrivingLateIsStillTheFirstPage() {
        val state = state()
        state.updateSection(feed(VideoGroup.ACTION_REPLACE, "s1", "s2"))
        state.endReload()
        state.updateHistory(historyOf("h1" to 50f), state.epoch)
        state.onCardFocused(FocusKey("continue", "h1"), 0)
        assertTrue(state.canQuietReload())
    }
}
