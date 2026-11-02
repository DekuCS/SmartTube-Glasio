package io.github.ivanosh.stmaterial.browse

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.tv.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionsFeedTest {
    private val home = BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, -1)
    private val subs = BrowseSection(MediaGroup.TYPE_SUBSCRIPTIONS, "Subs", BrowseSection.TYPE_GRID, -1)
    private val historySection = BrowseSection(MediaGroup.TYPE_HISTORY, "History", BrowseSection.TYPE_GRID, -1)

    private fun video(id: String, pct: Float = -1f) = Video().apply { videoId = id; percentWatched = pct }

    private fun subsGroup(action: Int, vararg ids: String): VideoGroup =
        VideoGroup.from(ids.map { video(it) }, subs, 0).apply { setAction(action) }

    /** What a history fetch hands over: id to percent. */
    private fun historyGroup(vararg entries: Pair<String, Float>): VideoGroup =
        VideoGroup.from(entries.map { video(it.first, it.second) }, null, 0)

    private val loads = mutableListOf<Int>()

    private fun stateOnSubs() = BrowseState(onSubscriptionsLoad = { loads.add(it) }, rowTitle = { "t$it" }, authorOf = { it.author }).apply {
        sections.addAll(listOf(subs, home, historySection))
        selectTab(subs.id)
    }

    private fun BrowseState.layout() = rows.map { it.id }
    private fun BrowseState.ids(id: String) = rows.first { it.id == id }.videos.map { it.videoId }

    @Test
    fun historyBeforeSubscriptionsGivesContinueThenNew() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("h1" to 50f, "h2" to 3f, "h3" to 96f), state.epoch)
        assertEquals(listOf("continue"), state.layout())
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "h1", "s2"))
        assertEquals(listOf("continue", "new"), state.layout())
        assertEquals(listOf("h1"), state.ids("continue"))
        assertEquals(listOf("s1", "h1", "s2"), state.ids("new"))
        assertEquals("t${R.string.material_row_continue}", state.rows[0].title)
        assertEquals("t${R.string.material_row_new}", state.rows[1].title)
        assertFalse(state.rows[0].isGrid)
        assertFalse(state.rows[1].isGrid)
    }

    @Test
    fun historyAfterSubscriptionsLeavesNewAsItIs() {
        val state = stateOnSubs()
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "h1", "s2"))
        assertEquals(listOf("new"), state.layout())
        assertEquals(listOf("s1", "h1", "s2"), state.ids("new"))
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        assertEquals(listOf("continue", "new"), state.layout())
        assertEquals(listOf("h1"), state.ids("continue"))
        assertEquals(listOf("s1", "h1", "s2"), state.ids("new"))
    }

    @Test
    fun historyThatFailsLeavesJustNew() {
        val state = stateOnSubs()
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        assertEquals(listOf("new"), state.layout())
        assertNull(state.error)
    }

    @Test
    fun historyWithNothingToContinueMakesNoRow() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("a" to -1f, "b" to 100f, "c" to 4.9f), state.epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1"))
        assertEquals(listOf("new"), state.layout())
    }

    @Test
    fun percentIsRoundedDown() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("a" to 4.99f, "b" to 5.01f, "c" to 95.9f, "d" to 96f), state.epoch)
        assertEquals(listOf("b", "c"), state.ids("continue"))
    }

    @Test
    fun laterPagesGoToEarlierBelowAndSkipNewOnly() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        // The presenter sends its group again with the next page added to it.
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2", "s3", "h1", "s4"))
        assertEquals(listOf("continue", "new", "earlier"), state.layout())
        assertEquals(listOf("s1", "s2"), state.ids("new"))
        assertEquals(listOf("s3", "h1", "s4"), state.ids("earlier"))
        assertTrue(state.rows[2].isGrid)
        assertEquals("t${R.string.material_row_earlier}", state.rows[2].title)
    }

    @Test
    fun aPageThatBringsNothingNewMakesNoEarlierRow() {
        val state = stateOnSubs()
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        assertEquals(listOf("new"), state.layout())
    }

    @Test
    fun historyArrivingAfterPagingLeavesNewAndEarlierAsTheyAre() {
        val state = stateOnSubs()
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s3", "s4"))
        state.updateHistory(historyGroup("s3" to 30f, "s1" to 30f), state.epoch)
        assertEquals(listOf("continue", "new", "earlier"), state.layout())
        assertEquals(listOf("s3", "s1"), state.ids("continue"))
        assertEquals(listOf("s1", "s2"), state.ids("new"))
        assertEquals(listOf("s3", "s4"), state.ids("earlier"))
    }

    @Test
    fun earlierKeepsAVideoThatIsAlsoInContinue() {
        val state = stateOnSubs()
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1"))
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s2"))
        state.updateHistory(historyGroup("s2" to 30f), state.epoch)
        assertEquals(listOf("continue", "new", "earlier"), state.layout())
        assertEquals(listOf("s2"), state.ids("earlier"))
    }

    @Test
    fun aPageOfTheFeedCanComeBeforeAnyHistoryAndLaterPagesStillAppend() {
        val state = stateOnSubs()
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1"))
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s2"))
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s3"))
        assertEquals(listOf("s2", "s3"), state.ids("earlier"))
    }

    @Test
    fun syncOfAContinueVideoUpdatesItInPlace() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1"))
        val row = state.rows[0]
        val shown = row.videos[0]
        val before = row.revision
        state.updateSection(VideoGroup.from(listOf(video("h1", 99f))).apply { setAction(VideoGroup.ACTION_SYNC) })
        assertSame(shown, row.videos[0])
        assertEquals(99f, shown.percentWatched, 0f)
        assertNotEquals(before, row.revision)
        assertEquals(listOf("continue", "new"), state.layout())
    }

    @Test
    fun aRemovedVideoDoesNotComeBack() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        state.updateSection(VideoGroup.from(listOf(video("h1"), video("s1"))).apply { setAction(VideoGroup.ACTION_REMOVE) })
        assertEquals(listOf("new"), state.layout())
        assertEquals(listOf("s2"), state.ids("new"))
        // A late history page and a cumulative page must not bring them back.
        state.updateHistory(historyGroup("h1" to 50f, "h9" to 20f), state.epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2", "s3"))
        assertEquals(listOf("h9"), state.ids("continue"))
        assertEquals(listOf("s2"), state.ids("new"))
        assertEquals(listOf("s3"), state.ids("earlier"))
    }

    @Test
    fun aRefreshStartsTheFeedAgainButKeepsTheHistory() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        state.updateSection(subsGroup(VideoGroup.ACTION_REPLACE))
        assertTrue(state.rows.isEmpty())
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "n1", "h1"))
        assertEquals(listOf("continue", "new"), state.layout())
        assertEquals(listOf("n1", "h1"), state.ids("new"))
    }

    @Test
    fun historyOfAnotherTabIsDropped() {
        val state = stateOnSubs()
        val epoch = state.epoch
        state.selectTab(home.id)
        state.updateHistory(historyGroup("h1" to 50f), epoch)
        assertTrue(state.rows.isEmpty())
        // Back on Subscriptions: the old request's answer is stale.
        state.selectTab(subs.id)
        state.updateHistory(historyGroup("h1" to 50f), epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "h1"))
        assertEquals(listOf("new"), state.layout())
        assertEquals(listOf("s1", "h1"), state.ids("new"))
    }

    @Test
    fun leavingTheTabForgetsItsHistory() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        state.selectTab(home.id)
        state.selectTab(subs.id)
        assertTrue(state.rows.isEmpty())
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1"))
        assertEquals(listOf("new"), state.layout())
    }

    @Test
    fun otherTabsKeepTheirRows() {
        val state = stateOnSubs()
        state.selectTab(home.id)
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        state.updateSection(VideoGroup.from(listOf(video("a"), video("h1")), home, 0).apply { setId(7) })
        assertEquals(listOf("7"), state.layout())
        assertEquals(listOf("a", "h1"), state.ids("7"))
    }

    @Test
    fun theHistoryTabIsAnOrdinaryGrid() {
        val state = stateOnSubs()
        state.selectTab(historySection.id)
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        state.updateSection(VideoGroup.from(listOf(video("a"), video("h1")), historySection, 0))
        assertEquals(listOf(VideoRow.GRID_ID), state.layout())
        assertEquals(listOf("a", "h1"), state.ids(VideoRow.GRID_ID))
    }

    @Test
    fun onlyTheLastRowAsksForMoreAndNeverContinue() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        assertNull(state.takeMoreRequest(state.rows[0]))
        assertEquals("s2", state.takeMoreRequest(state.rows[1])?.videoId)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s3"))
        assertNull(state.takeMoreRequest(state.rows[1]))
        assertEquals("s3", state.takeMoreRequest(state.rows[2])?.videoId)
    }

    @Test
    fun rowsKeepTheGroupSoVideosCanFindTheirNextPage() {
        val state = stateOnSubs()
        val group = subsGroup(VideoGroup.ACTION_APPEND, "s1")
        state.updateSection(group)
        assertSame(group, state.rows[0].group)
    }

    @Test
    fun aFeedVideoAlsoInTheHistoryStaysOnTheFeedsGroup() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("x" to 100f), state.epoch)
        val feed = subsGroup(VideoGroup.ACTION_APPEND, "s1", "x")
        state.updateSection(feed)
        val last = state.takeMoreRequest(state.rows.first { it.id == "new" })
        assertEquals("x", last?.videoId)
        assertSame(feed, last?.group)
    }

    @Test
    fun theLastFeedRowAsksAsSoonAsACardIsFocused() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        assertFalse(state.rows[0].eager)
        assertTrue(state.rows[1].eager)
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2", "s3"))
        assertFalse(state.rows[1].eager)
        assertFalse(state.rows[2].eager)
        assertTrue(state.rows[2].pageable)
    }

    @Test
    fun everyLoadOfTheFeedAsksForTheHistoryOnce() {
        val state = stateOnSubs()
        val first = state.epoch
        state.updateSection(subsGroup(VideoGroup.ACTION_REPLACE))
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1"))
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        assertEquals(listOf(first), loads)
        state.updateSection(subsGroup(VideoGroup.ACTION_REPLACE))
        assertEquals(listOf(first, first), loads)
    }

    @Test
    fun otherTabsNeverAskForTheHistory() {
        val state = stateOnSubs()
        state.selectTab(home.id)
        state.updateSection(VideoGroup.from(home).apply { setAction(VideoGroup.ACTION_REPLACE) })
        assertTrue(loads.isEmpty())
    }

    @Test
    fun emptyingEarlierLetsNewAskAgain() {
        val state = stateOnSubs()
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1"))
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1", "s2"))
        state.updateSection(VideoGroup.from(listOf(video("s2"))).apply { setAction(VideoGroup.ACTION_REMOVE) })
        assertEquals(listOf("new"), state.layout())
        assertTrue(state.rows[0].pageable)
        assertTrue(state.rows[0].eager)
    }

    @Test
    fun whenContinueIsAllThereIsItPagesTheFeedByItsGroup() {
        val state = stateOnSubs()
        state.updateHistory(historyGroup("h1" to 50f), state.epoch)
        assertNull(state.takeMoreRequest(state.rows[0]))
        assertTrue(loads.isEmpty())
        val feed = subsGroup(VideoGroup.ACTION_APPEND, "s9")
        state.updateSection(feed)
        state.updateSection(VideoGroup.from(listOf(video("s9"))).apply { setAction(VideoGroup.ACTION_REMOVE) })
        assertEquals(listOf("continue"), state.layout())
        assertTrue(state.rows[0].eager)
        val next = state.takeMoreRequest(state.rows[0])
        assertSame(feed, next?.group)
        assertNull(state.takeMoreRequest(state.rows[0]))
    }

    @Test
    fun continueKeepsItsGroupAfterTheFeedRestarts() {
        val state = stateOnSubs()
        val history = historyGroup("h1" to 50f)
        state.updateHistory(history, state.epoch)
        state.updateSection(subsGroup(VideoGroup.ACTION_REPLACE))
        state.updateSection(subsGroup(VideoGroup.ACTION_APPEND, "s1"))
        assertSame(history, state.rows[0].group)
    }
}
