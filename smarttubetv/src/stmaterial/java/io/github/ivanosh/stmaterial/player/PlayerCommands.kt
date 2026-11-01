package io.github.ivanosh.stmaterial.player

import androidx.annotation.StringRes
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter
import com.liskovsoft.smartyoutubetv2.tv.R

enum class PanelAction(@StringRes val label: Int, val iconPath: String) {
    PlayPause(R.string.material_player_pause, "M8 5v14M16 5v14"),
    Next(R.string.material_player_next, "M5 5l10 7-10 7zM19 5v14"),
    Quality(R.string.material_player_quality, "M3 6h18v12H3zM7 9v6M11 9v6M7 12h4M14 9v6h2a3 3 0 0 0 0-6z"),
    Subtitles(R.string.material_player_subtitles, "M3 6h18v12H3zM7 13h4M13 13h4M7 16h7"),
    Speed(R.string.material_player_speed, "M4 18a9 9 0 1 1 16 0M12 15l4-5"),
    More(R.string.material_player_more, "M5 12h.01M12 12h.01M19 12h.01"),
}

const val PLAY_ICON_PATH = "M7 4l13 8-13 8z"

/** The panel of the first version: five buttons and More, the last. Task 11 makes this list customizable. */
val PANEL_BUTTONS = listOf(
    PanelAction.PlayPause,
    PanelAction.Next,
    PanelAction.Quality,
    PanelAction.Subtitles,
    PanelAction.Speed,
    PanelAction.More,
)

/** Runs the panel's commands through the handlers the Leanback buttons used. */
class PlayerCommands(private val presenter: PlaybackPresenter, private val player: () -> MaterialPlaybackFragment?) {
    fun togglePlay() {
        val view = player() ?: return
        val play = !view.playWhenReady
        // An ended video does not start again by itself.
        if (play && view.durationMs > 0 && view.positionMs >= view.durationMs) view.positionMs = 0
        view.playWhenReady = play
    }

    fun press(index: Int) {
        val view = player() ?: return
        when (PANEL_BUTTONS.getOrNull(index)) {
            PanelAction.PlayPause -> togglePlay()
            PanelAction.Next -> presenter.onNextClicked()
            PanelAction.Quality -> openQuality()
            // The short click of these two toggles a value; the long click is what opens their dialogs.
            PanelAction.Subtitles -> openLong(R.id.lb_control_closed_captioning)
            PanelAction.Speed -> openLong(R.id.action_video_speed)
            PanelAction.More -> openMore()
            null -> Unit
        }
    }

    /** A placeholder until Task 11: the old full settings dialog. */
    fun openMore() = openQuality()

    private fun openQuality() {
        val view = player() ?: return
        presenter.onButtonClicked(R.id.lb_control_high_quality, view.getButtonState(R.id.lb_control_high_quality))
    }

    private fun openLong(buttonId: Int) {
        val view = player() ?: return
        presenter.onButtonLongClicked(buttonId, view.getButtonState(buttonId))
    }
}
