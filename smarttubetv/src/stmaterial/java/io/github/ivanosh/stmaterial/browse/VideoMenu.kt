package io.github.ivanosh.stmaterial.browse

import android.app.Activity
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.sharedutils.rx.RxHelper
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SimpleMediaItem
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter.VideoMenuCallback
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil
import com.liskovsoft.smartyoutubetv2.common.utils.SimpleEditDialog
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import io.github.ivanosh.stmaterial.ui.MenuItem

/** What the menu of a card can do, in the order it shows them. */
enum class VideoAction { QUEUE, PLAYLIST, REMOVE_FROM_PLAYLIST, CREATE_PLAYLIST, CHANNEL, NOT_INTERESTED, BLOCK_CHANNEL, REMOVE_FROM_HISTORY }

/** What the menu needs to know about a card and about the session, to leave out what cannot work. */
data class VideoFacts(val signedIn: Boolean, val hasVideo: Boolean, val hasChannel: Boolean, val hasAuthor: Boolean, val hasFeedback: Boolean)

/** The actions of a section: five everywhere, and Remove from history on History only. */
fun videoMenuActions(section: Int): List<VideoAction> =
    if (section == MediaGroup.TYPE_HISTORY) VideoAction.values().toList()
    else VideoAction.values().filter { it != VideoAction.REMOVE_FROM_HISTORY }

/**
 * Those of [videoMenuActions] that can work for this card. Save to playlist, Not interested and
 * Remove from history need an account; Queue and Save need a video, not a channel or a playlist;
 * Go to channel needs a channel that can be opened (a channel id, or a video to look it up from) and Block
 * channel an author, which is what the old menu asked; Not interested needs the feedback tokens.
 */
fun availableVideoActions(section: Int, facts: VideoFacts): List<VideoAction> =
    videoMenuActions(section).filter {
        when (it) {
            VideoAction.QUEUE -> facts.hasVideo
            VideoAction.PLAYLIST -> facts.signedIn && facts.hasVideo
            VideoAction.REMOVE_FROM_PLAYLIST -> facts.signedIn && facts.hasVideo
            // A new list needs the account. With a video it gets that video; on the playlists tab it starts empty.
            VideoAction.CREATE_PLAYLIST -> facts.signedIn && (facts.hasVideo || section == MediaGroup.TYPE_USER_PLAYLISTS)
            VideoAction.CHANNEL -> facts.hasChannel
            VideoAction.BLOCK_CHANNEL -> facts.hasAuthor
            VideoAction.NOT_INTERESTED -> facts.signedIn && facts.hasFeedback
            VideoAction.REMOVE_FROM_HISTORY -> facts.signedIn
        }
    }

/**
 * The menu of a card for any screen that shows cards: each action goes to what the button of the same name
 * in [VideoMenuPresenter] runs. Removing a card and the confirmation line are the screen's own:
 * the presenter reports a removal through its callback, and [onGone] gets the cards to take out.
 */
class CardMenu(
    private val activity: Activity,
    private val onGone: (VideoGroup) -> Unit,
    private val onMessage: (String) -> Unit,
    private val onReload: () -> Unit = {},
) {
    fun items(video: Video, section: Int): List<MenuItem> {
        val token = video.mediaItem
        val facts = VideoFacts(
            signedIn = YouTubeServiceManager.instance().signInService.isSigned,
            hasVideo = video.hasVideo(),
            hasChannel = ChannelPresenter.canOpenChannel(video),
            hasAuthor = video.getAuthor() != null,
            hasFeedback = token != null && (token.feedbackToken != null || token.feedbackEndpoint != null),
        )
        val menu = VideoMenuPresenter.instance(activity)
        return availableVideoActions(section, facts).map { action ->
            when (action) {
                VideoAction.QUEUE -> MenuItem(activity.getString(R.string.material_video_queue)) {
                    menu.addToQueue(video, null)
                    onMessage(activity.getString(R.string.material_video_queued))
                }
                VideoAction.PLAYLIST -> MenuItem(activity.getString(R.string.material_video_playlist)) {
                    AppDialogUtil.showAddToPlaylistDialog(activity, video, removalCallback(null, false))
                }
                VideoAction.REMOVE_FROM_PLAYLIST -> MenuItem(activity.getString(R.string.material_video_playlist_remove)) {
                    removeFromPlaylist(video)
                }
                VideoAction.CREATE_PLAYLIST -> MenuItem(activity.getString(R.string.material_playlist_create)) {
                    createPlaylist(video)
                }
                VideoAction.CHANNEL -> MenuItem(activity.getString(R.string.material_video_channel)) {
                    MediaServiceManager.chooseChannelPresenter(activity, video)
                }
                VideoAction.NOT_INTERESTED -> MenuItem(activity.getString(R.string.material_video_not_interested)) {
                    menu.notInterested(video, removalCallback(R.string.material_video_hidden, false))
                }
                VideoAction.BLOCK_CHANNEL -> MenuItem(activity.getString(R.string.material_video_block_channel)) {
                    menu.blockChannel(video, removalCallback(R.string.material_video_blocked, true))
                    // The list is stored ten seconds after a change; a kill before that would bring the channel back.
                    BlockedChannelData.instance(activity).persistNow()
                }
                VideoAction.REMOVE_FROM_HISTORY -> MenuItem(activity.getString(R.string.material_video_remove_history)) {
                    menu.removeFromHistory(video, removalCallback(R.string.material_video_removed_history, false))
                }
            }
        }
    }

    /** A new list of the account: with the card's video, or empty on the playlists tab, which then reloads. */
    private fun createPlaylist(video: Video) {
        SimpleEditDialog.show(
            activity,
            activity.getString(R.string.material_playlist_create),
            activity.getString(R.string.material_playlist_create_note),
            null,
        ) { name ->
            val item = if (video.hasVideo()) video.mediaItem ?: SimpleMediaItem.from(video) else null
            RxHelper.execute(
                YouTubeMediaItemService.instance().createPlaylistObserve(name, item),
                null,
                { error: Throwable -> onMessage(error.localizedMessage.orEmpty()) },
                {
                    if (video.hasVideo()) onMessage(activity.getString(R.string.material_playlist_added)) else onReload()
                },
            )
            true
        }
    }

    /** The lists the video is in: choosing one takes it out. */
    private fun removeFromPlaylist(video: Video) {
        val service = YouTubeMediaItemService.instance()
        RxHelper.execute(
            service.getPlaylistsInfoObserve(video.videoId),
            { infos ->
                val lists = infos.filter { it.isSelected }
                if (lists.isEmpty()) {
                    onMessage(activity.getString(R.string.material_playlist_not_in_any))
                    return@execute
                }
                val title = activity.getString(R.string.material_video_playlist_remove)
                val dialog = AppDialogPresenter.instance(activity)
                dialog.appendRadioCategory(title, lists.map { info ->
                    UiOptionItem.from(info.title) {
                        dialog.goBack()
                        RxHelper.execute(
                            service.removeFromPlaylistObserve(info.playlistId, video.videoId),
                            null,
                            { error: Throwable -> onMessage(error.localizedMessage.orEmpty()) },
                            {
                                onMessage(activity.getString(R.string.material_playlist_removed, info.title))
                                onReload()
                            },
                        )
                    }
                })
                dialog.showDialog(title)
            },
            { error: Throwable -> onMessage(error.localizedMessage.orEmpty()) },
        )
    }

    /** The presenter says a card is gone, maybe from another thread; blocking a channel takes its other cards too. */
    private fun removalCallback(message: Int?, wholeChannel: Boolean) = VideoMenuCallback { video, action ->
        if (action != VideoMenuCallback.ACTION_REMOVE && action != VideoMenuCallback.ACTION_REMOVE_FROM_PLAYLIST) return@VideoMenuCallback
        activity.runOnUiThread {
            val gone = VideoGroup.from(listOf(video))
            gone.action = if (wholeChannel) VideoGroup.ACTION_REMOVE_AUTHOR else VideoGroup.ACTION_REMOVE
            onGone(gone)
            message?.let { onMessage(activity.getString(it)) }
        }
    }
}
