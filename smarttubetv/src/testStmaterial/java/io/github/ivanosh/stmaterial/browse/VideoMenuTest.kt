package io.github.ivanosh.stmaterial.browse

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import io.github.ivanosh.stmaterial.browse.VideoAction.BLOCK_CHANNEL
import io.github.ivanosh.stmaterial.browse.VideoAction.CHANNEL
import io.github.ivanosh.stmaterial.browse.VideoAction.NOT_INTERESTED
import io.github.ivanosh.stmaterial.browse.VideoAction.PLAYLIST
import io.github.ivanosh.stmaterial.browse.VideoAction.QUEUE
import io.github.ivanosh.stmaterial.browse.VideoAction.REMOVE_FROM_HISTORY
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMenuTest {
    private val all = VideoFacts(signedIn = true, hasVideo = true, hasChannel = true, hasAuthor = true, hasFeedback = true)

    @Test
    fun `history adds remove from history last`() {
        assertEquals(
            listOf(QUEUE, PLAYLIST, CHANNEL, NOT_INTERESTED, BLOCK_CHANNEL, REMOVE_FROM_HISTORY),
            videoMenuActions(MediaGroup.TYPE_HISTORY)
        )
    }

    @Test
    fun `other sections have the five`() {
        listOf(MediaGroup.TYPE_HOME, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_USER_PLAYLISTS, MediaGroup.TYPE_TRENDING).forEach {
            assertEquals(listOf(QUEUE, PLAYLIST, CHANNEL, NOT_INTERESTED, BLOCK_CHANNEL), videoMenuActions(it))
        }
    }

    @Test
    fun `signed in video with everything keeps the whole list`() {
        assertEquals(videoMenuActions(MediaGroup.TYPE_HISTORY), availableVideoActions(MediaGroup.TYPE_HISTORY, all))
    }

    @Test
    fun `signed out hides what needs an account`() {
        assertEquals(
            listOf(QUEUE, CHANNEL, BLOCK_CHANNEL),
            availableVideoActions(MediaGroup.TYPE_HISTORY, all.copy(signedIn = false))
        )
    }

    @Test
    fun `a card that cannot open a channel hides go to channel only`() {
        assertEquals(
            listOf(QUEUE, PLAYLIST, NOT_INTERESTED, BLOCK_CHANNEL, REMOVE_FROM_HISTORY),
            availableVideoActions(MediaGroup.TYPE_HISTORY, all.copy(hasChannel = false))
        )
    }

    /** A search result has no channel id, but an author: the channel can be opened and blocked by name. */
    @Test
    fun `no author hides block channel only`() {
        assertEquals(
            listOf(QUEUE, PLAYLIST, CHANNEL, NOT_INTERESTED, REMOVE_FROM_HISTORY),
            availableVideoActions(MediaGroup.TYPE_HISTORY, all.copy(hasAuthor = false))
        )
    }

    @Test
    fun `no feedback tokens hides not interested`() {
        assertEquals(
            listOf(QUEUE, PLAYLIST, CHANNEL, BLOCK_CHANNEL),
            availableVideoActions(MediaGroup.TYPE_HOME, all.copy(hasFeedback = false))
        )
    }

    @Test
    fun `a card that is not a video cannot be queued or saved`() {
        assertEquals(
            listOf(CHANNEL, NOT_INTERESTED, BLOCK_CHANNEL),
            availableVideoActions(MediaGroup.TYPE_HOME, all.copy(hasVideo = false))
        )
    }
}
