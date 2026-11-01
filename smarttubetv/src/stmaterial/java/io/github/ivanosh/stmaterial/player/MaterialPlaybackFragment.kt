package io.github.ivanosh.stmaterial.player

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackFragment
import java.util.concurrent.ConcurrentHashMap

/**
 * The engine and the presenter contract of [PlaybackFragment] without its visible controls. Leanback's own
 * overlay stays hidden and its rows keep the focus they need for media keys. Whatever the presenter asks of
 * "the overlay" goes to the Compose panel of [MaterialPlaybackActivity].
 */
class MaterialPlaybackFragment : PlaybackFragment() {
    private val host get() = activity as? MaterialPlaybackActivity
    private var controlsShownAnswer = false
    private var videoId: String? = null
    private var lastTitle: String? = null

    /** The text the presenter puts in place of the title, such as why a video is unplayable. */
    private var message: String? = null

    /**
     * What the panel shows as the title. Every load of the video clears its metadata title
     * (`Video.sync(formatInfo)`), and a video opened by a link has no other title until the metadata is back.
     */
    val panelTitle: String
        get() {
            rememberTitle(video)
            return message?.ifBlank { null } ?: lastTitle.orEmpty()
        }

    /**
     * The old round progress bar is taken out and [LoadingSpinnerView] takes its place in Leanback's manager.
     * Every route that showed it goes on through the manager: the presenter's loading (`showProgressBar`) and the
     * engine's buffering callback. So the conditions are the same, and so is the delay of the manager: it shows the
     * view 1000 ms after the first request, a rebuffer shorter than that never shows, and it hides at once.
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        if (root is ViewGroup) {
            val old = root.getChildAt(root.childCount - 1)
            if (old is ProgressBar) root.removeView(old)
            val spinner = LoadingSpinnerView(root.context)
            val side = (SpinnerPlateSize.value * root.resources.displayMetrics.density).toInt()
            root.addView(spinner, FrameLayout.LayoutParams(side, side, Gravity.CENTER))
            progressBarManager.setProgressBarView(spinner)
        }
        return root
    }

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
            lastTitle = null
            host?.onVideoChanged()
        }
        rememberTitle(video)
    }

    private fun rememberTitle(video: Video?) {
        video?.titleFull?.ifBlank { null }?.let { lastTitle = it }
    }

    /**
     * What the presenter sets for a button goes to the panel and to More, which show it. The hidden
     * Leanback actions get it too, since the presenter reads it back with [getButtonState]. The presenter
     * writes from its own thread and the UI reads, so the map is concurrent and the overlay is written on the UI thread.
     */
    val buttonStates = ConcurrentHashMap<Int, Int>()

    override fun setButtonState(buttonId: Int, buttonState: Int) {
        super.setButtonState(buttonId, buttonState)
        buttonStates[buttonId] = buttonState
        activity?.runOnUiThread { host?.onButtonState(buttonId, buttonState) }
    }

    override fun getButtonState(buttonId: Int): Int = buttonStates[buttonId] ?: super.getButtonState(buttonId)

    /** The glue is made again with the engine, and its buttons start over, so the states kept here would be stale. */
    private fun forgetButtonStates() {
        buttonStates.clear()
        activity?.runOnUiThread { host?.clearButtonStates() }
    }

    override fun restartEngine() {
        forgetButtonStates()
        super.restartEngine()
    }

    override fun maybeReleasePlayer() {
        super.maybeReleasePlayer()
        forgetButtonStates()
    }

    /** Nothing draws the storyboard, and it costs work per load. */
    override fun loadStoryboard() = Unit

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
