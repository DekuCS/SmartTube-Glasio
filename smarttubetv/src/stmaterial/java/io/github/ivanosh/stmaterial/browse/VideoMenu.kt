package io.github.ivanosh.stmaterial.browse

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup

/** What the menu of a card can do, in the order it shows them. */
enum class VideoAction { QUEUE, PLAYLIST, CHANNEL, NOT_INTERESTED, BLOCK_CHANNEL, REMOVE_FROM_HISTORY }

/** What the menu needs to know about a card and about the session, to leave out what cannot work. */
data class VideoFacts(val signedIn: Boolean, val hasVideo: Boolean, val hasChannel: Boolean, val hasFeedback: Boolean)

/** The actions of a section: five everywhere, and Remove from history on History only. */
fun videoMenuActions(section: Int): List<VideoAction> =
    if (section == MediaGroup.TYPE_HISTORY) VideoAction.values().toList()
    else VideoAction.values().filter { it != VideoAction.REMOVE_FROM_HISTORY }

/**
 * Those of [videoMenuActions] that can work for this card. Save to playlist, Not interested and
 * Remove from history need an account; Queue and Save need a video, not a channel or a playlist;
 * Go to channel and Block channel need a channel id; Not interested needs the feedback tokens.
 */
fun availableVideoActions(section: Int, facts: VideoFacts): List<VideoAction> =
    videoMenuActions(section).filter {
        when (it) {
            VideoAction.QUEUE -> facts.hasVideo
            VideoAction.PLAYLIST -> facts.signedIn && facts.hasVideo
            VideoAction.CHANNEL, VideoAction.BLOCK_CHANNEL -> facts.hasChannel
            VideoAction.NOT_INTERESTED -> facts.signedIn && facts.hasFeedback
            VideoAction.REMOVE_FROM_HISTORY -> facts.signedIn
        }
    }
