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
    fun failureKeepsTheRowsAndAllowsTheNextReload() {
        val state = state(home)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.beginQuietReload()
        assertTrue(state.failQuietReload())
        assertEquals(listOf("a"), state.ids())
        assertFalse(state.failQuietReload())
        assertTrue(state.beginQuietReload())
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
