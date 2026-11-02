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

class QuietReloadTest {
    private val home = BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, -1)
    private val history = BrowseSection(MediaGroup.TYPE_HISTORY, "History", BrowseSection.TYPE_GRID, -1)
    private var time = 1_000L

    private fun video(id: String) = Video().apply { videoId = id }

    private fun group(section: BrowseSection, rowId: Int, action: Int, vararg ids: String): VideoGroup =
        VideoGroup.from(ids.map(::video), section, 0).apply {
            setId(rowId)
            setAction(action)
        }

    private fun state(section: BrowseSection) = BrowseState(clock = { time }) { it.author }.apply {
        sections.add(section)
        selectTab(section.id)
    }

    private fun BrowseState.ids(row: Int = 0) = rows[row].videos.map { it.videoId }

    @Test
    fun loadTimeIsTheArrivalOfTheFirstVideos() {
        val state = state(home)
        assertNull(state.loadedAt)
        time = 5_000
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        time = 9_000
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "b"))
        assertEquals(5_000L, state.loadedAt)
        assertEquals(4_000L, state.ageMs())
    }

    @Test
    fun ageFollowsTheInjectedClockAcrossALongStandby() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        time += 6 * 3_600_000L
        assertEquals(6 * 3_600_000L, state.ageMs())
        assertEquals(time, state.now())
    }

    @Test
    fun nothingShownMeansNoQuietReload() {
        assertFalse(state(home).beginQuietReload())
    }

    @Test
    fun rowsStayWhileTheReloadIsLoading() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        assertTrue(state.beginQuietReload())
        state.updateSection(group(home, 7, VideoGroup.ACTION_REPLACE))
        assertEquals(listOf("a", "b"), state.ids())
        assertFalse(state.beginQuietReload())
    }

    @Test
    fun aPageOfTheShownRowsDoesNotSwap() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.beginQuietReload()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "b"))
        assertEquals(listOf("a", "b"), state.ids())
        assertTrue(state.quiet)
    }

    @Test
    fun theAnswerSwapsTheRowsAndKeepsTheirIds() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.updateSection(group(home, 2, VideoGroup.ACTION_APPEND, "c").apply { setPosition(1) })
        state.beginQuietReload()
        state.updateSection(group(home, 7, VideoGroup.ACTION_REPLACE))
        state.updateSection(group(home, 8, VideoGroup.ACTION_APPEND, "n", "a", "b"))
        state.updateSection(group(home, 9, VideoGroup.ACTION_APPEND, "c").apply { setPosition(1) })
        assertFalse(state.quiet)
        assertEquals(listOf("1", "2"), state.rows.map { it.id })
        assertEquals(listOf("n", "a", "b"), state.ids(0))
        // A later page of a new row finds its row through the old id.
        state.updateSection(group(home, 8, VideoGroup.ACTION_APPEND, "z"))
        assertEquals(listOf("n", "a", "b", "z"), state.ids(0))
        assertEquals(2, state.rows.size)
    }

    @Test
    fun focusedCardStaysAndOtherwiseTakesTheNearestOne() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b", "c"))
        state.onCardFocused(FocusKey("1", "b"), 0)
        state.beginQuietReload()
        state.updateSection(group(home, 7, VideoGroup.ACTION_REPLACE))
        state.updateSection(group(home, 8, VideoGroup.ACTION_APPEND, "n", "a", "b", "c"))
        assertEquals(FocusKey("1", "b"), state.pendingFocus)
        state.endReload()

        state.pendingFocus = null
        state.beginQuietReload()
        state.updateSection(group(home, 7, VideoGroup.ACTION_REPLACE))
        state.updateSection(group(home, 8, VideoGroup.ACTION_APPEND, "x", "y"))
        assertEquals(FocusKey("1", "y"), state.pendingFocus)
    }

    @Test
    fun focusOnTheTabsStaysThere() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.onTabsFocused()
        state.beginQuietReload()
        state.updateSection(group(home, 7, VideoGroup.ACTION_REPLACE))
        state.updateSection(group(home, 8, VideoGroup.ACTION_APPEND, "n"))
        assertNull(state.pendingFocus)
        assertNull(state.focused)
    }

    @Test
    fun gridReloadKeepsOneRowAndAllowsNewVideosAtTheTop() {
        val state = state(history)
        state.updateSection(group(history, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.onCardFocused(FocusKey(VideoRow.GRID_ID, "b"), 0)
        state.beginQuietReload()
        state.updateSection(group(history, 2, VideoGroup.ACTION_REPLACE))
        assertEquals(listOf("a", "b"), state.ids())
        state.updateSection(group(history, 2, VideoGroup.ACTION_APPEND, "n", "a", "b"))
        assertEquals(listOf("n", "a", "b"), state.ids())
        assertEquals(1, state.rows.size)
        assertEquals(FocusKey(VideoRow.GRID_ID, "b"), state.pendingFocus)
    }

    @Test
    fun failureKeepsTheRowsAndTheRetryOfThePresenterStaysQuiet() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.beginQuietReload()
        assertTrue(state.failQuietReload())
        assertEquals(listOf("a"), state.ids())
        assertFalse(state.beginQuietReload())
        // Upstream retries 30 s later as an ordinary load: its REPLACE must not wipe the rows.
        time += 30_000
        assertTrue(state.quiet)
        state.updateSection(group(home, 7, VideoGroup.ACTION_REPLACE))
        assertEquals(listOf("a"), state.ids())
        // If that fails too, the message shows again and the rows stay.
        assertTrue(state.failQuietReload())
        assertEquals(listOf("a"), state.ids())
        time += 30_000
        state.updateSection(group(home, 7, VideoGroup.ACTION_REPLACE))
        state.updateSection(group(home, 8, VideoGroup.ACTION_APPEND, "n", "a"))
        assertEquals(listOf("n", "a"), state.ids())
        assertFalse(state.quiet)
    }

    @Test
    fun aRetryThatNeverComesStopsBeingQuietAfterAWhile() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.beginQuietReload()
        state.failQuietReload()
        time += 61_000
        assertFalse(state.quiet)
        assertTrue(state.beginQuietReload())
    }

    @Test
    fun anEmptyAnswerThatNeverSwapsDoesNotLeaveTheStateQuietForGood() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.beginQuietReload()
        time += 61_000
        assertFalse(state.quiet)
    }

    @Test
    fun clearingTheContentEndsTheQuietReload() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.beginQuietReload()
        state.clearContent()
        assertFalse(state.quiet)
    }

    @Test
    fun restartingTheTabEndsAQuietRetry() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.beginQuietReload()
        state.failQuietReload()
        state.restartTab()
        assertFalse(state.quiet)
    }

    @Test
    fun quietReloadRunsOnlyWithFocusOnTheTabsOrTheFirstPage() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.endReload()
        state.updateSection(group(home, 2, VideoGroup.ACTION_APPEND, "late").apply { setPosition(1) })
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "c"))

        state.onTabsFocused()
        assertTrue(state.canQuietReload())
        state.onCardFocused(FocusKey("1", "b"), 0)
        assertTrue(state.canQuietReload())
        state.onCardFocused(FocusKey("1", "c"), 0)
        assertFalse("a card of a later page", state.canQuietReload())
        state.onCardFocused(FocusKey("2", "late"), 1)
        assertFalse("a row that arrived after the first answer", state.canQuietReload())
        assertFalse(state.beginQuietReload())
        assertFalse(state.quiet)
        state.onTabsFocused()
        assertTrue(state.beginQuietReload())
    }

    @Test
    fun rowsOfTheFirstAnswerCountAsTheFirstPageWhenTheyArriveInOnePass() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.updateSection(group(home, 2, VideoGroup.ACTION_APPEND, "b").apply { setPosition(1) })
        state.onCardFocused(FocusKey("2", "b"), 1)
        assertTrue(state.canQuietReload())
    }

    @Test
    fun gridPagesAfterTheFirstAreDeep() {
        val state = state(history)
        state.updateSection(group(history, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.endReload()
        state.updateSection(group(history, 1, VideoGroup.ACTION_APPEND, "c"))
        state.onCardFocused(FocusKey(VideoRow.GRID_ID, "b"), 0)
        assertTrue(state.canQuietReload())
        state.onCardFocused(FocusKey(VideoRow.GRID_ID, "c"), 0)
        assertFalse(state.canQuietReload())
    }

    @Test
    fun rowsAfterASwapAreNotTheFirstPageEither() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.beginQuietReload()
        state.updateSection(group(home, 7, VideoGroup.ACTION_REPLACE))
        state.updateSection(group(home, 8, VideoGroup.ACTION_APPEND, "n"))
        state.endReload()
        state.updateSection(group(home, 9, VideoGroup.ACTION_APPEND, "late").apply { setPosition(1) })
        state.onCardFocused(FocusKey("9", "late"), 1)
        assertFalse(state.canQuietReload())
    }

    @Test
    fun aReloadThatNeverAnsweredStopsBlockingAfterAWhile() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.beginQuietReload()
        time += 61_000
        assertTrue(state.beginQuietReload())
    }

    @Test
    fun restartingTheTabEmptiesItAndForgetsTheAge() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.restartTab()
        assertTrue(state.rows.isEmpty())
        assertNull(state.loadedAt)
        assertFalse(state.quiet)
    }
}
