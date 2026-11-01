package io.github.ivanosh.stmaterial.player

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment

/**
 * The engine and the presenter contract of [PlaybackFragment] without its visible controls. Leanback's own
 * overlay stays hidden and its rows keep the focus they need for media keys. Whatever the presenter asks of
 * "the overlay" goes to the Compose panel of [MaterialPlaybackActivity].
 */
class MaterialPlaybackFragment : PlaybackFragment() {
    private val host get() = activity as? MaterialPlaybackActivity
    private var controlsShownAnswer = false
    private var videoId: String? = null

    /** The text the presenter puts in place of the title, such as why a video is unplayable. */
    var message: String? = null
        private set

    /** Every way Leanback has of showing the controls ends here: tickle, seek mode, the glue host. */
    override fun showControlsOverlay(runAnimation: Boolean) = Unit

    override fun showOverlay(show: Boolean) {
        host?.showPanel(show)
    }

    override fun showControls(show: Boolean) = showOverlay(show)

    /** Suggestions have no UI here; a video that stopped at its end still gets the panel. */
    override fun showSuggestions(show: Boolean) = showOverlay(show)

    override fun isOverlayShown(): Boolean = host?.isPanelOpen == true

    override fun isSuggestionsShown(): Boolean = false

    /**
     * Stays false for the presenter's own auto-hide timer, whose timeout is not the panel's. True only
     * inside [answeringAsShownControls]: the presenter gives the chapter title only to shown controls.
     */
    override fun isControlsShown(): Boolean = controlsShownAnswer

    override fun setSeekBarSegments(segments: List<SeekBarSegment>?) {
        host?.setMarks(segments)
    }

    override fun setSeekPreviewTitle(title: String?) {
        host?.setChapter(title)
    }

    override fun setTitle(title: String?) {
        super.setTitle(title)
        message = title
    }

    override fun setVideo(video: Video?) {
        super.setVideo(video)
        message = null
        // The presenter also calls this with the same video, to clear a message.
        if (video?.videoId != videoId) {
            videoId = video?.videoId
            host?.onVideoChanged()
        }
    }

    /** Moves the engine and tells the presenter as the upstream seek bar does, which also brings the chapter title. */
    fun seekTo(positionMs: Long) {
        setPositionMs(positionMs)
        answeringAsShownControls { onSeekPositionChanged(positionMs) }
    }

    fun answeringAsShownControls(call: () -> Unit) {
        controlsShownAnswer = true
        try {
            call()
        } finally {
            controlsShownAnswer = false
        }
    }
}
