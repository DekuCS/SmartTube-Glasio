package io.github.ivanosh.stmaterial.player

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.liskovsoft.sharedutils.helpers.KeyHelpers
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity
import io.github.ivanosh.stmaterial.browse.KeyRepeatGuard
import io.github.ivanosh.stmaterial.logic.PanelFocus
import io.github.ivanosh.stmaterial.logic.PlayerCommand
import io.github.ivanosh.stmaterial.logic.PlayerKey
import io.github.ivanosh.stmaterial.logic.PlayerUi
import io.github.ivanosh.stmaterial.logic.SEEK_DEBOUNCE_MS
import io.github.ivanosh.stmaterial.logic.SeekDebounce
import io.github.ivanosh.stmaterial.logic.playerKey
import io.github.ivanosh.stmaterial.logic.shouldAutoHide
import io.github.ivanosh.stmaterial.ui.MaterialTvTheme

/**
 * The player with the Compose panel. The engine stays in [MaterialPlaybackFragment]. D-pad, OK and Back
 * are decided by [playerKey] here; every other key, media and number keys among them, goes on to the
 * fragment as it does upstream. So does Back when it leaves the player, which keeps PIP and the exit
 * settings of [PlaybackActivity] working.
 */
class MaterialPlaybackActivity : PlaybackActivity() {
    private val panel = PlayerOverlayState()
    private val handler = Handler(Looper.getMainLooper())
    private val repeatGuard = KeyRepeatGuard()
    private val player get() = playbackView as? MaterialPlaybackFragment
    private val presenter get() = PlaybackPresenter.instance(this)
    private val commands by lazy { PlayerCommands(presenter) { player } }
    private var ui = PlayerUi(PanelFocus.HIDDEN, 0, PANEL_BUTTONS.size)
    private var seek = SeekDebounce()
    private var lastKeyMs = 0L
    private var seekPreviewUntilMs = 0L
    private var backPassedOn = false
    private var ticking = false

    val isPanelOpen get() = ui.focus != PanelFocus.HIDDEN

    private val applySeek = Runnable {
        val (rest, due) = seek.takeDue(SystemClock.uptimeMillis())
        seek = rest
        if (due != null) player?.seekTo(due)
    }

    /** Runs only while something is on screen, so a hidden panel costs nothing. */
    private val tick = object : Runnable {
        override fun run() {
            ticking = false
            val now = SystemClock.uptimeMillis()
            // A dialog over the panel holds the timer; it starts again when the dialog closes.
            if (!hasWindowFocus()) lastKeyMs = now
            val view = player
            if (view != null && shouldAutoHide(ui, view.playWhenReady, now - lastKeyMs)) {
                setUi(ui.copy(focus = PanelFocus.HIDDEN))
            }
            if (now >= seekPreviewUntilMs) panel.seekPreview = false
            refresh()
            startTicking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addContentView(
            ComposeView(this).apply {
                setContent { MaterialTvTheme { PlayerOverlay(panel) } }
                // Draws only: Leanback's rows keep the focus, and the keys are handled above the views.
                isFocusable = false
                isClickable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
    }

    override fun onStop() {
        // Before the fragment releases the engine.
        sendSeekNow()
        setUi(ui.copy(focus = PanelFocus.HIDDEN))
        panel.seekPreview = false
        handler.removeCallbacks(tick)
        ticking = false
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            setUi(ui.copy(focus = PanelFocus.HIDDEN))
            panel.seekPreview = false
        }
    }

    override fun interceptKeyEvent(event: KeyEvent): Boolean {
        if (isInPipMode) return false
        val key = playerKeyOf(event.keyCode) ?: return false
        if (event.action != KeyEvent.ACTION_DOWN) {
            if (key == PlayerKey.Back && backPassedOn) {
                backPassedOn = false
                return false
            }
            return true
        }
        val moves = key == PlayerKey.Left || key == PlayerKey.Right
        if (event.repeatCount > 0) {
            // Only a held left or right repeats, and what waited in the queue is dropped, so nothing moves after release.
            if (!moves || repeatGuard.shouldDrop(event.repeatCount, event.eventTime, SystemClock.uptimeMillis())) return true
        } else {
            presenter.onKeyDown(NO_KEY) // what a touch does: the sleep timer and the like start over
        }
        lastKeyMs = SystemClock.uptimeMillis()
        val (next, keyCommands) = playerKey(ui, key, PlayerData.instance(this).seekIncrementMs.toLong())
        setUi(next)
        var passBackOn = false
        for (command in keyCommands) {
            when (command) {
                PlayerCommand.TogglePlay -> commands.togglePlay()
                is PlayerCommand.Seek -> seekBy(command.deltaMs)
                is PlayerCommand.Press -> commands.press(command.index)
                PlayerCommand.OpenMore -> commands.openMore()
                PlayerCommand.Exit -> {
                    sendSeekNow()
                    passBackOn = true
                }
            }
        }
        refresh()
        startTicking()
        backPassedOn = passBackOn
        return !passBackOn
    }

    // Calls from MaterialPlaybackFragment, which answers for the presenter.

    fun showPanel(show: Boolean) {
        if (show && isInPipMode) return
        when {
            !show -> setUi(ui.copy(focus = PanelFocus.HIDDEN))
            !isPanelOpen -> setUi(ui.copy(focus = PanelFocus.BUTTONS, buttonIndex = 0))
        }
        if (show) {
            lastKeyMs = SystemClock.uptimeMillis()
            refresh()
            startTicking()
        }
    }

    /** As on the upstream bar, chapters and SponsorBlock add up, and null clears them. */
    fun setMarks(segments: List<SeekBarSegment>?) {
        panel.marks = if (segments == null) emptyList() else (panel.marks + segments.mapNotNull(::markOf)).distinct()
    }

    fun setChapter(title: String?) {
        panel.chapter = title
    }

    fun onVideoChanged() {
        handler.removeCallbacks(applySeek)
        seek = SeekDebounce()
    }

    private fun setUi(next: PlayerUi) {
        val wasOpen = isPanelOpen
        ui = next
        panel.focus = next.focus
        panel.buttonIndex = next.buttonIndex
        when {
            wasOpen == isPanelOpen -> Unit
            // Upstream shows the chapter title when the controls open, as long as they count as shown.
            isPanelOpen -> player?.answeringAsShownControls { presenter.onControlsShown(true) }
            else -> presenter.onControlsShown(false)
        }
    }

    private fun seekBy(deltaMs: Long) {
        val view = player ?: return
        val now = SystemClock.uptimeMillis()
        seek = seek.onKey(view.positionMs, deltaMs, view.durationMs, now)
        handler.removeCallbacks(applySeek)
        handler.postDelayed(applySeek, SEEK_DEBOUNCE_MS)
        if (!isPanelOpen) {
            panel.seekPreview = true
            seekPreviewUntilMs = now + SEEK_PREVIEW_MS
        }
    }

    private fun sendSeekNow() {
        handler.removeCallbacks(applySeek)
        val due = seek.targetMs
        seek = SeekDebounce()
        if (due != null) player?.seekTo(due)
    }

    private fun refresh() {
        if (!isPanelOpen && !panel.seekPreview) return
        val view = player ?: return
        val video = view.video
        panel.title = view.message ?: video?.titleFull.orEmpty()
        panel.author = video?.author.orEmpty()
        panel.positionMs = seek.targetMs ?: view.positionMs
        panel.durationMs = view.durationMs
        panel.playing = view.playWhenReady
    }

    private fun startTicking() {
        if (!ticking && (isPanelOpen || panel.seekPreview)) {
            ticking = true
            handler.postDelayed(tick, TICK_MS)
        }
    }

    private fun markOf(segment: SeekBarSegment): ProgressMark? {
        if (segment.startProgress < 0 || segment.endProgress < 0 || segment.startProgress >= 1) return null
        return ProgressMark(segment.startProgress, segment.endProgress.coerceAtMost(1f), segment.color == Color.BLACK)
    }

    private fun playerKeyOf(keyCode: Int): PlayerKey? = when {
        keyCode == KeyEvent.KEYCODE_DPAD_UP -> PlayerKey.Up
        keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> PlayerKey.Down
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> PlayerKey.Left
        keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerKey.Right
        KeyHelpers.isConfirmKey(keyCode) -> PlayerKey.Ok
        KeyHelpers.isBackKey(keyCode) -> PlayerKey.Back
        else -> null
    }

    private companion object {
        const val TICK_MS = 500L
        const val SEEK_PREVIEW_MS = 2_000L
        const val NO_KEY = -1
    }
}
