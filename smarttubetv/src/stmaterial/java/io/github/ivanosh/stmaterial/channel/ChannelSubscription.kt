package io.github.ivanosh.stmaterial.channel

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.UiOptionItem
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.SubscribeAction
import io.github.ivanosh.stmaterial.logic.SubscribeLabel
import io.github.ivanosh.stmaterial.logic.subscribeButton
import io.github.ivanosh.stmaterial.logic.subscribedAfter
import io.github.ivanosh.stmaterial.ui.PillState
import io.reactivex.disposables.Disposable

/**
 * The Subscribe button of a channel page. Whether the account is subscribed comes from the metadata
 * upstream loads for a video of the channel (the channel row itself does not carry it); subscribing and
 * unsubscribing are the calls of the card menu's Subscribe button. What the button shows and does is
 * decided in logic/ChannelSubscribe.kt.
 */
class ChannelSubscription(
    private val activity: Activity,
    private val channelId: () -> String?,
    private val channelName: () -> String?,
    private val onMessage: (String) -> Unit,
) {
    private var subscribed by mutableStateOf<Boolean?>(null)
    private var pending by mutableStateOf(false)
    private var signedIn by mutableStateOf(isSignedIn())
    private var loading = false
    private var call: Disposable? = null

    private fun isSignedIn() = YouTubeServiceManager.instance().signInService.isSigned

    fun pill(): PillState {
        val button = subscribeButton(signedIn, subscribed, pending)
        val label = when (button.label) {
            SubscribeLabel.SUBSCRIBE -> R.string.material_channel_subscribe
            SubscribeLabel.SUBSCRIBED -> R.string.material_channel_subscribed
            SubscribeLabel.SIGN_IN -> R.string.material_channel_sign_in_to_subscribe
        }
        return PillState(activity.getString(label), button.label == SubscribeLabel.SUBSCRIBED, button.enabled) { act(button.action) }
    }

    /** The screen is back (from sign-in, say): the account may have changed. */
    fun refresh() {
        val now = isSignedIn()
        if (now != signedIn) {
            signedIn = now
            subscribed = null
        }
    }

    /** Cards arrived: the first video of any of them tells whether the account follows this channel. */
    fun learnFrom(videos: List<Video>?) {
        val id = channelId()
        if (!signedIn || subscribed != null || loading || id == null) return
        val video = videos?.firstOrNull { it.videoId != null } ?: return
        loading = true
        MediaServiceManager.instance().loadMetadata(
            video,
            { metadata ->
                activity.runOnUiThread {
                    loading = false
                    // A playlist row may hold videos of other channels: only the channel's own answer counts.
                    if (channelId() == id && metadata.channelId == id && subscribed == null) subscribed = metadata.isSubscribed
                }
            },
            { activity.runOnUiThread { loading = false } },
            null,
        )
    }

    /** The page was left or shows another channel. */
    fun reset() {
        call?.dispose()
        call = null
        subscribed = null
        pending = false
        loading = false
    }

    private fun act(action: SubscribeAction) {
        when (action) {
            SubscribeAction.NONE -> Unit
            SubscribeAction.SIGN_IN -> YTSignInPresenter.instance(activity).start()
            SubscribeAction.SUBSCRIBE -> send(subscribe = true)
            SubscribeAction.ASK_UNSUBSCRIBE -> askUnsubscribe()
        }
    }

    private fun askUnsubscribe() {
        val name = channelName() ?: activity.getString(R.string.material_channel_this_channel)
        val title = activity.getString(R.string.material_channel_unsubscribe_ask, name)
        val dialog = AppDialogPresenter.instance(activity)
        // Cancel comes first, so it has focus when the dialog opens.
        val options = listOf<OptionItem>(
            UiOptionItem.from(activity.getString(R.string.cancel)) { dialog.goBack() },
            UiOptionItem.from(activity.getString(R.string.material_channel_unsubscribe)) {
                dialog.goBack()
                send(subscribe = false)
            },
        )
        dialog.appendStringsCategory(title, options)
        dialog.showDialog(title)
    }

    private fun send(subscribe: Boolean) {
        val id = channelId() ?: return
        if (pending) return
        pending = true
        val service = YouTubeServiceManager.instance().mediaItemService
        val calls = if (subscribe) service.subscribeObserve(id) else service.unsubscribeObserve(id)
        val before = !subscribe
        call = calls.subscribe(
            { },
            {
                activity.runOnUiThread {
                    pending = false
                    subscribed = subscribedAfter(before, succeeded = false)
                    onMessage(activity.getString(R.string.material_channel_subscribe_failed))
                }
            },
            {
                activity.runOnUiThread {
                    pending = false
                    subscribed = subscribedAfter(before, succeeded = true)
                    onMessage(activity.getString(if (subscribe) R.string.subscribed_to_channel else R.string.unsubscribed_from_channel))
                }
            },
        )
    }
}
