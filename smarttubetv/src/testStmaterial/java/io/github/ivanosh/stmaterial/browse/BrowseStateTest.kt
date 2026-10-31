package io.github.ivanosh.stmaterial.browse

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import io.github.ivanosh.stmaterial.logic.Sections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseStateTest {
    private val home = BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, -1)
    private val subs = BrowseSection(MediaGroup.TYPE_SUBSCRIPTIONS, "Subs", BrowseSection.TYPE_GRID, -1)
    private val history = BrowseSection(MediaGroup.TYPE_HISTORY, "History", BrowseSection.TYPE_GRID, -1)
    private val settingsSection = BrowseSection(MediaGroup.TYPE_SETTINGS, "Settings", BrowseSection.TYPE_SETTINGS_GRID, -1)

    private fun video(id: String) = Video().apply { videoId = id }

    private fun group(section: BrowseSection?, rowId: Int, action: Int, vararg ids: String): VideoGroup =
        VideoGroup.from(ids.map(::video), section, 0).apply {
            setId(rowId)
            setAction(action)
        }

    /** Video.getAuthor() needs TextUtils, which is not on the JVM test path. */
    private fun stateOnHome() = BrowseState { it.author }.apply {
        sections.addAll(listOf(subs, home, settingsSection))
        selectTab(home.id)
    }

    private fun BrowseState.ids(rowIndex: Int = 0) = rows[rowIndex].videos.map { it.videoId }

    @Test
    fun appendAddsToTheSameRow() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "c"))
        assertEquals(1, state.rows.size)
        assertEquals(listOf("a", "b", "c"), state.ids())
    }

    @Test
    fun appendSkipsVideosTheRowAlreadyHas() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "b", "c"))
        assertEquals(listOf("a", "b", "c"), state.ids())
    }

    @Test
    fun differentGroupsMakeDifferentRows() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.updateSection(group(home, 2, VideoGroup.ACTION_APPEND, "b").apply { setPosition(1) })
        assertEquals(listOf("1", "2"), state.rows.map { it.id })
    }

    @Test
    fun gridSectionKeepsEverythingInOneRow() {
        val state = stateOnHome()
        state.sections.add(history)
        state.selectTab(history.id)
        state.updateSection(group(history, 1, VideoGroup.ACTION_APPEND, "a"))
        state.updateSection(group(history, 2, VideoGroup.ACTION_APPEND, "b"))
        assertEquals(1, state.rows.size)
        assertTrue(state.rows[0].isGrid)
        assertEquals(listOf("a", "b"), state.ids())
    }

    @Test
    fun replaceReplacesTheRow() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.updateSection(group(home, 1, VideoGroup.ACTION_REPLACE, "c"))
        assertEquals(listOf("c"), state.ids())
    }

    @Test
    fun emptyReplaceWithoutPositionClearsTheSection() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.updateSection(group(home, 2, VideoGroup.ACTION_APPEND, "b"))
        state.updateSection(VideoGroup.from(home).apply { setAction(VideoGroup.ACTION_REPLACE) })
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun removeTakesTheVideoOut() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.updateSection(group(home, 1, VideoGroup.ACTION_REMOVE, "a"))
        assertEquals(listOf("b"), state.ids())
    }

    @Test
    fun cumulativePageDoesNotRestoreARemovedVideo() {
        val state = stateOnHome()
        val page = group(home, 1, VideoGroup.ACTION_APPEND, "a", "b")
        state.updateSection(page)
        state.updateSection(group(home, 1, VideoGroup.ACTION_REMOVE, "a"))
        state.updateSection(page)
        assertEquals(listOf("b"), state.ids())
    }

    @Test
    fun removeAuthorRemovesTheAuthorsVideosFromEveryRow() {
        val state = stateOnHome()
        val mine = group(home, 1, VideoGroup.ACTION_APPEND, "a", "b").apply { videos[0].author = "Ann"; videos[1].author = "Bob" }
        val other = group(home, 2, VideoGroup.ACTION_APPEND, "c", "d").apply {
            setPosition(1)
            videos[0].author = "Ann"
            videos[1].author = "Bob"
        }
        state.updateSection(mine)
        state.updateSection(other)
        val gone = group(null, 1, VideoGroup.ACTION_REMOVE_AUTHOR, "x").apply { videos[0].author = "Ann" }
        state.updateSection(gone)
        assertEquals(listOf("b"), state.ids(0))
        assertEquals(listOf("d"), state.ids(1))
    }

    @Test
    fun removeAuthorKeepsItsVideosOutOfACumulativePage() {
        val state = stateOnHome()
        val page = group(home, 1, VideoGroup.ACTION_APPEND, "a", "b").apply { videos[0].author = "Ann"; videos[1].author = "Bob" }
        state.updateSection(page)
        state.updateSection(group(null, 1, VideoGroup.ACTION_REMOVE_AUTHOR, "x").apply { videos[0].author = "Ann" })
        state.updateSection(page)
        assertEquals(listOf("b"), state.ids())
    }

    @Test
    fun prependPutsVideosFirst() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.updateSection(group(home, 1, VideoGroup.ACTION_PREPEND, "b"))
        assertEquals(listOf("b", "a"), state.ids())
    }

    @Test
    fun syncUpdatesTheShownVideoAndTellsTheCard() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        val row = state.rows[0]
        val shown = row.videos[0]
        val before = row.revision
        state.updateSection(group(null, 9, VideoGroup.ACTION_SYNC, "a").apply { videos[0].percentWatched = 40f })
        assertSame(shown, row.videos[0])
        assertEquals(40f, shown.percentWatched, 0f)
        assertNotEquals(before, row.revision)
    }

    @Test
    fun syncReachesEveryRowWithTheVideo() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.updateSection(group(home, 2, VideoGroup.ACTION_APPEND, "a").apply { setPosition(1) })
        state.updateSection(group(null, 1, VideoGroup.ACTION_SYNC, "a").apply { videos[0].percentWatched = 40f })
        assertEquals(40f, state.rows[1].videos[0].percentWatched, 0f)
    }

    @Test
    fun syncOfAVideoChangedInPlaceStillTellsTheCard() {
        val state = stateOnHome()
        val page = group(home, 1, VideoGroup.ACTION_APPEND, "a")
        state.updateSection(page)
        val row = state.rows[0]
        val before = row.revision
        val sync = VideoGroup.from(listOf(row.videos[0])).apply { setAction(VideoGroup.ACTION_SYNC) }
        row.videos[0].deArrowTitle = "Better title"
        state.updateSection(sync)
        assertNotEquals(before, row.revision)
    }

    @Test
    fun syncOfAnotherVideoLeavesTheRowAlone() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        val row = state.rows[0]
        val before = row.revision
        state.updateSection(group(null, 1, VideoGroup.ACTION_SYNC, "z"))
        assertEquals(before, row.revision)
    }

    @Test
    fun groupOfAnotherSectionIsIgnored() {
        val state = stateOnHome()
        state.updateSection(group(subs, 1, VideoGroup.ACTION_APPEND, "a"))
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun nothingLoadsIntoTheSearchTab() {
        val state = stateOnHome()
        state.selectTab(Sections.SEARCH_SECTION_ID)
        state.updateSection(group(null, 1, VideoGroup.ACTION_APPEND, "a"))
        state.showError(ErrorData("late error", null, null))
        assertTrue(state.rows.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun switchingTabsClearsContent() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.onCardFocused(FocusKey("1", "a"), 0)
        state.showError(ErrorData("boom", null, null))
        state.selectTab(subs.id)
        assertEquals(subs.id, state.selected)
        assertTrue(state.rows.isEmpty())
        assertNull(state.error)
        assertNull(state.focused)
    }

    @Test
    fun errorKeepsTheSelectedTab() {
        val state = stateOnHome()
        state.showError(ErrorData("no network", null, null))
        assertEquals(home.id, state.selected)
        assertEquals("no network", state.error?.message)
    }

    @Test
    fun emptyReplaceKeepsTheErrorAndContentClearsIt() {
        val state = stateOnHome()
        state.showError(ErrorData("no network", null, null))
        state.updateSection(VideoGroup.from(home).apply { setAction(VideoGroup.ACTION_REPLACE) })
        assertEquals("no network", state.error?.message)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        assertNull(state.error)
    }

    @Test
    fun restoreFocusReturnsTheSavedCard() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.updateSection(group(home, 2, VideoGroup.ACTION_APPEND, "c"))
        state.onCardFocused(FocusKey("2", "c"), 1)
        assertEquals(FocusKey("2", "c"), state.restoreFocus())
    }

    @Test
    fun restoreFocusFallsBackToTheFirstCardWhenTheVideoIsGone() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        state.onCardFocused(FocusKey("1", "b"), 0)
        state.updateSection(group(home, 1, VideoGroup.ACTION_REMOVE, "b"))
        assertEquals(FocusKey("1", "a"), state.restoreFocus())
    }

    @Test
    fun restoreFocusIsNullWithoutCards() {
        val state = stateOnHome()
        assertNull(state.restoreFocus())
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        state.onCardFocused(FocusKey("1", "a"), 0)
        state.updateSection(group(home, 1, VideoGroup.ACTION_REMOVE, "a"))
        assertNull(state.restoreFocus())
    }

    @Test
    fun tabsFollowTheShownOrderAndSkipWhatThePresenterLacks() {
        val state = stateOnHome()
        state.shown = listOf(MediaGroup.TYPE_HOME, Sections.SEARCH_SECTION_ID, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_SUBSCRIPTIONS)
        assertEquals(
            listOf(MediaGroup.TYPE_HOME, Sections.SEARCH_SECTION_ID, MediaGroup.TYPE_SUBSCRIPTIONS),
            state.tabs()
        )
    }

    @Test
    fun presenterSectionOutsideShownIsNotATab() {
        val state = stateOnHome()
        state.shown = listOf(MediaGroup.TYPE_SUBSCRIPTIONS)
        assertEquals(listOf(MediaGroup.TYPE_SUBSCRIPTIONS), state.tabs())
    }

    @Test
    fun presenterErrorSectionIsAlwaysATab() {
        val password = BrowseSection(42, "Locked", BrowseSection.TYPE_ERROR, -1)
        val state = BrowseState().apply {
            sections.add(password)
            shown = listOf(MediaGroup.TYPE_HOME)
        }
        assertEquals(listOf(42), state.tabs())
    }

    @Test
    fun onlyTabsAndSettingsCanBeSelected() {
        val state = stateOnHome()
        state.shown = listOf(MediaGroup.TYPE_HOME)
        assertTrue(state.canSelect(MediaGroup.TYPE_HOME))
        assertTrue(state.canSelect(MediaGroup.TYPE_SETTINGS))
        assertFalse(state.canSelect(MediaGroup.TYPE_SUBSCRIPTIONS))
    }

    @Test
    fun tabsHideOnlyBelowTheFirstRow() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        assertTrue(state.tabsVisible)
        state.onCardFocused(FocusKey("1", "a"), 0)
        assertTrue(state.tabsVisible)
        state.onCardFocused(FocusKey("1", "a"), 1)
        assertFalse(state.tabsVisible)
        state.onCardFocused(FocusKey("1", "a"), 0)
        assertTrue(state.tabsVisible)
        state.onCardFocused(FocusKey("1", "a"), 2)
        state.onTabsFocused()
        assertTrue(state.tabsVisible)
    }

    @Test
    fun moreIsRequestedOncePerRowSize() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a", "b"))
        val row = state.rows[0]
        assertEquals("b", state.takeMoreRequest(row)?.videoId)
        assertNull(state.takeMoreRequest(row))
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "c"))
        assertEquals("c", state.takeMoreRequest(row)?.videoId)
    }

    @Test
    fun rowHoldsItsGroupBecauseVideosOnlyReferToItWeakly() {
        val state = stateOnHome()
        val first = group(home, 1, VideoGroup.ACTION_APPEND, "a")
        state.updateSection(first)
        assertSame(first, state.rows[0].group)
        val next = group(home, 1, VideoGroup.ACTION_APPEND, "b")
        state.updateSection(next)
        assertSame(next, state.rows[0].group)
    }

    @Test
    fun pageOfRepeatsAllowsTheNextRequest() {
        val state = stateOnHome()
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        val row = state.rows[0]
        assertEquals("a", state.takeMoreRequest(row)?.videoId)
        state.updateSection(group(home, 1, VideoGroup.ACTION_APPEND, "a"))
        assertEquals("a", state.takeMoreRequest(row)?.videoId)
    }

    @Test
    fun repeatGuardNeverDropsSinglePresses() {
        val guard = KeyRepeatGuard()
        repeat(5) { assertFalse(guard.shouldDrop(repeatCount = 0, eventTimeMs = 1000L, nowMs = 1000L)) }
    }

    @Test
    fun repeatGuardLimitsTheHoldRate() {
        val guard = KeyRepeatGuard()
        assertFalse(guard.shouldDrop(repeatCount = 1, eventTimeMs = 1000L, nowMs = 1000L))
        assertTrue(guard.shouldDrop(repeatCount = 2, eventTimeMs = 1050L, nowMs = 1050L))
        assertFalse(guard.shouldDrop(repeatCount = 3, eventTimeMs = 1100L, nowMs = 1100L))
    }

    @Test
    fun repeatGuardDropsRepeatsThatWaitedInTheQueue() {
        val guard = KeyRepeatGuard()
        assertTrue(guard.shouldDrop(repeatCount = 1, eventTimeMs = 1000L, nowMs = 1400L))
    }
}
