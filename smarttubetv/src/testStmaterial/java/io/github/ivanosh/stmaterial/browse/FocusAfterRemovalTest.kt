package io.github.ivanosh.stmaterial.browse

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusAfterRemovalTest {
    private val rows = listOf(CardRow("r1", listOf("a", "b", "c")), CardRow("r2", listOf("d", "e")), CardRow("r3", listOf("f")))

    @Test
    fun nextCardOfTheSameRow() {
        assertEquals(FocusKey("r1", "c"), focusAfterRemoval(rows, FocusKey("r1", "b"), setOf("b")))
    }

    @Test
    fun previousCardAtTheEnd() {
        assertEquals(FocusKey("r1", "b"), focusAfterRemoval(rows, FocusKey("r1", "c"), setOf("c")))
    }

    @Test
    fun skipsOtherCardsThatGoToo() {
        assertEquals(FocusKey("r1", "c"), focusAfterRemoval(rows, FocusKey("r1", "a"), setOf("a", "b")))
        assertEquals(FocusKey("r1", "a"), focusAfterRemoval(rows, FocusKey("r1", "b"), setOf("b", "c")))
    }

    @Test
    fun rowAboveKeepsTheColumnAndClamps() {
        assertEquals(FocusKey("r1", "b"), focusAfterRemoval(rows, FocusKey("r2", "e"), setOf("d", "e")))
        assertEquals(FocusKey("r2", "d"), focusAfterRemoval(rows, FocusKey("r3", "f"), setOf("f")))
    }

    @Test
    fun rowBelowWhenNothingIsAbove() {
        assertEquals(FocusKey("r2", "d"), focusAfterRemoval(rows, FocusKey("r1", "a"), setOf("a", "b", "c")))
    }

    @Test
    fun nullWhenNothingIsLeftOrFocusStays() {
        assertNull(focusAfterRemoval(listOf(CardRow("r1", listOf("a"))), FocusKey("r1", "a"), setOf("a")))
        assertNull(focusAfterRemoval(rows, FocusKey("r1", "a"), setOf("z")))
    }

    // The state moves focus before the card goes, so that nothing is left to fall to the first card.
    private val home = BrowseSection(MediaGroup.TYPE_HOME, "Home", BrowseSection.TYPE_ROW, -1)

    private fun group(rowId: Int, action: Int, vararg ids: String): VideoGroup =
        VideoGroup.from(ids.map { Video().apply { videoId = it } }, home, 0).apply { setId(rowId); setAction(action) }

    @Test
    fun stateMovesFocusToTheNeighbourCard() {
        val state = BrowseState(clock = { 0L }) { it.author }.apply { sections.add(home); selectTab(home.id) }
        state.updateSection(group(1, VideoGroup.ACTION_APPEND, "a", "b", "c"))
        state.onCardFocused(FocusKey("1", "b"), 0)
        state.updateSection(group(1, VideoGroup.ACTION_REMOVE, "b"))
        assertEquals(FocusKey("1", "c"), state.pendingFocus)
        assertEquals(FocusKey("1", "c"), state.restoreFocus())
    }

    @Test
    fun stateIgnoresRemovalOfOtherCards() {
        val state = BrowseState(clock = { 0L }) { it.author }.apply { sections.add(home); selectTab(home.id) }
        state.updateSection(group(1, VideoGroup.ACTION_APPEND, "a", "b", "c"))
        state.onCardFocused(FocusKey("1", "b"), 0)
        state.updateSection(group(1, VideoGroup.ACTION_REMOVE, "c"))
        assertNull(state.pendingFocus)
    }

    @Test
    fun subscriptionsFeedKeepsFocusOnAnExistingNeighbour() {
        val subs = BrowseSection(MediaGroup.TYPE_SUBSCRIPTIONS, "Subs", BrowseSection.TYPE_GRID, -1)
        val state = BrowseState(clock = { 0L }) { it.author }.apply { sections.add(subs); selectTab(subs.id) }
        val ids = (1..12).map { "v$it" }.toTypedArray()
        state.updateSection(VideoGroup.from(ids.map { Video().apply { videoId = it } }, subs, 0).apply { setId(1); setAction(VideoGroup.ACTION_APPEND) })
        val row = state.rows.first { it.videos.size > 2 }
        val victim = row.videos[1].videoId
        state.onCardFocused(FocusKey(row.id, victim), 0)
        state.updateSection(VideoGroup.from(listOf(Video().apply { videoId = victim }), subs, 0).apply { setId(1); setAction(VideoGroup.ACTION_REMOVE) })
        val target = state.pendingFocus!!
        assertEquals(row.id, target.rowId)
        assertEquals(true, state.rows.any { r -> r.id == target.rowId && r.videos.any { videoKey(it) == target.videoId } })
        assertEquals(target, state.restoreFocus())
    }
}
