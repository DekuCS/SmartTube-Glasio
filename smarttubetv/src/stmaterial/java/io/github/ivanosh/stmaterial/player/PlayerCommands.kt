package io.github.ivanosh.stmaterial.player

import androidx.annotation.StringRes
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.ButtonGroupKind
import io.github.ivanosh.stmaterial.logic.PlayerButton

const val PLAY_ICON_PATH = "M7 4l13 8-13 8z"
const val MORE_ICON_PATH = "M5 12h.01M12 12h.01M19 12h.01"
const val CUSTOMIZE_ICON_PATH = "M4 7h9M17 7h3M13 5v4h4V5zM4 17h3M11 17h9M7 15v4h4v-4z"

@StringRes
fun groupTitle(kind: ButtonGroupKind): Int = when (kind) {
    ButtonGroupKind.Playback -> R.string.material_group_playback
    ButtonGroupKind.PictureAndSound -> R.string.material_group_picture
    ButtonGroupKind.VideoAndChannel -> R.string.material_group_video
}

@StringRes
fun buttonLabel(button: PlayerButton): Int = when (button) {
    PlayerButton.PlayPause -> R.string.material_btn_play_pause
    PlayerButton.Next -> R.string.material_player_next
    PlayerButton.Previous -> R.string.material_btn_previous
    PlayerButton.Quality -> R.string.material_player_quality
    PlayerButton.Subtitles -> R.string.material_player_subtitles
    PlayerButton.Speed -> R.string.material_player_speed
    PlayerButton.Like -> R.string.material_btn_like
    PlayerButton.Dislike -> R.string.material_btn_dislike
    PlayerButton.Subscribe -> R.string.material_btn_subscribe
    PlayerButton.Channel -> R.string.material_btn_channel
    PlayerButton.AddToPlaylist -> R.string.material_btn_playlist
    PlayerButton.Queue -> R.string.material_btn_queue
    PlayerButton.Repeat -> R.string.material_btn_repeat
    PlayerButton.Search -> R.string.material_btn_search
    PlayerButton.VideoInfo -> R.string.material_btn_info
    PlayerButton.Share -> R.string.material_btn_share
    PlayerButton.Zoom -> R.string.material_btn_zoom
    PlayerButton.Stats -> R.string.material_btn_stats
    PlayerButton.SeekInterval -> R.string.material_btn_seek_interval
    PlayerButton.SponsorBlock -> R.string.material_btn_sponsorblock
    PlayerButton.Dimming -> R.string.material_btn_dimming
    PlayerButton.Mute -> R.string.material_btn_mute
    PlayerButton.Afr -> R.string.material_btn_afr
}

/** The 24x24 line icons of the canvas. */
fun buttonIcon(button: PlayerButton): String = when (button) {
    PlayerButton.PlayPause -> "M8 5v14M16 5v14"
    PlayerButton.Next -> "M5 5l10 7-10 7zM19 5v14"
    PlayerButton.Previous -> "M19 5L9 12l10 7zM5 5v14"
    PlayerButton.Quality -> "M3 6h18v12H3zM7 9v6M11 9v6M7 12h4M14 9v6h2a3 3 0 0 0 0-6z"
    PlayerButton.Subtitles -> "M3 6h18v12H3zM7 13h4M13 13h4M7 16h7"
    PlayerButton.Speed -> "M4 18a9 9 0 1 1 16 0M12 15l4-5"
    PlayerButton.Like -> "M7 11v9H4v-9zM7 11l4-8c1.5 0 2.5 1 2.5 2.5V9H19a2 2 0 0 1 2 2.4l-1.3 7A2 2 0 0 1 17.7 20H7"
    PlayerButton.Dislike -> "M17 13V4h3v9zM17 13l-4 8c-1.5 0-2.5-1-2.5-2.5V15H5a2 2 0 0 1-2-2.4l1.3-7A2 2 0 0 1 6.3 4H17"
    PlayerButton.Subscribe -> "M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 21h4"
    PlayerButton.Channel -> "M12 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM4 21c1-4 4-6 8-6s7 2 8 6"
    PlayerButton.AddToPlaylist -> "M4 6h12M4 11h12M4 16h7M17 14v6M14 17h6"
    PlayerButton.Queue -> "M4 6h12M4 11h12M4 16h7M15 15l6 3.5-6 3.5z"
    PlayerButton.Repeat -> "M17 3l3 3-3 3M4 11V9a3 3 0 0 1 3-3h13M7 21l-3-3 3-3M20 13v2a3 3 0 0 1-3 3H4"
    PlayerButton.Search -> "M10.5 3a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15zM16 16l5 5"
    PlayerButton.VideoInfo -> "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM12 11v6M12 7.5v.5"
    PlayerButton.Share -> "M12 15V4M8 8l4-4 4 4M5 13v7h14v-7"
    PlayerButton.Zoom -> "M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"
    PlayerButton.Stats -> "M5 20v-8M12 20V5M19 20v-5"
    PlayerButton.SeekInterval -> "M12 5a8 8 0 1 1-8 8M12 5V2L8 5l4 3z"
    PlayerButton.SponsorBlock -> "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6zM9 12l2 2 4-4"
    PlayerButton.Dimming -> "M20 14A8 8 0 1 1 10 4a7 7 0 0 0 10 10z"
    PlayerButton.Mute -> "M4 9v6h4l5 4V5L8 9zM17 9l4 6M21 9l-4 6"
    PlayerButton.Afr -> "M3 12h4l2-6 4 12 2-6h6"
}

/** The id the Leanback button had, which is what the presenter and its controllers know the button by. */
fun viewId(button: PlayerButton): Int? = when (button) {
    // These three never went through onButtonClicked.
    PlayerButton.PlayPause, PlayerButton.Next, PlayerButton.Previous -> null
    PlayerButton.Quality -> R.id.lb_control_high_quality
    PlayerButton.Subtitles -> R.id.lb_control_closed_captioning
    PlayerButton.Speed -> R.id.action_video_speed
    PlayerButton.Like -> R.id.action_thumbs_up
    PlayerButton.Dislike -> R.id.action_thumbs_down
    PlayerButton.Subscribe -> R.id.action_subscribe
    PlayerButton.Channel -> R.id.action_channel
    PlayerButton.AddToPlaylist -> R.id.action_playlist_add
    PlayerButton.Queue -> R.id.action_playback_queue
    PlayerButton.Repeat -> R.id.action_repeat
    PlayerButton.Search -> R.id.action_search
    PlayerButton.VideoInfo -> R.id.action_info
    PlayerButton.Share -> R.id.action_share
    PlayerButton.Zoom -> R.id.action_video_zoom
    PlayerButton.Stats -> R.id.action_video_stats
    PlayerButton.SeekInterval -> R.id.action_seek_interval
    PlayerButton.SponsorBlock -> R.id.action_content_block
    PlayerButton.Dimming -> R.id.action_screen_dimming
    PlayerButton.Mute -> R.id.action_sound_off
    PlayerButton.Afr -> R.id.action_afr
}

/** The buttons whose on/off state the old UI kept, and which show it. */
val STATEFUL_BUTTONS = setOf(
    PlayerButton.Subtitles, PlayerButton.Like, PlayerButton.Dislike, PlayerButton.Subscribe, PlayerButton.Repeat,
    PlayerButton.Mute, PlayerButton.SponsorBlock, PlayerButton.Dimming, PlayerButton.Afr, PlayerButton.Stats,
)

/** Runs the buttons' commands through the handlers the Leanback buttons used. */
class PlayerCommands(private val presenter: PlaybackPresenter, private val player: () -> MaterialPlaybackFragment?) {
    fun togglePlay() {
        val view = player() ?: return
        val play = !view.playWhenReady
        // An ended video does not start again by itself.
        if (play && view.durationMs > 0 && view.positionMs >= view.durationMs) view.positionMs = 0
        view.playWhenReady = play
    }

    fun run(button: PlayerButton) {
        val view = player() ?: return
        val id = viewId(button)
        when {
            button == PlayerButton.PlayPause -> togglePlay()
            button == PlayerButton.Next -> presenter.onNextClicked()
            button == PlayerButton.Previous -> presenter.onPreviousClicked()
            id == null -> Unit
            // The short click of these two toggles a value; the long click is what opens their dialogs.
            button == PlayerButton.Subtitles || button == PlayerButton.Speed -> presenter.onButtonLongClicked(id, view.getButtonState(id))
            // Signed-out handling (a message, or none) is in the handlers, as for the old buttons.
            else -> presenter.onButtonClicked(id, view.getButtonState(id))
        }
    }
}
