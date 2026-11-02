package io.github.ivanosh.stmaterial.player

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import com.liskovsoft.sharedutils.helpers.KeyHelpers
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.SeekBarSegment
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData
import com.liskovsoft.smartyoutubetv2.common.prefs.SponsorBlockData
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity
import io.github.ivanosh.stmaterial.browse.KeyRepeatGuard
import io.github.ivanosh.stmaterial.data.ButtonStore
import io.github.ivanosh.stmaterial.logic.OkAction
import io.github.ivanosh.stmaterial.logic.OkHold
import io.github.ivanosh.stmaterial.logic.PanelFocus
import io.github.ivanosh.stmaterial.logic.PlayerButton
import io.github.ivanosh.stmaterial.logic.PlayerCommand
import io.github.ivanosh.stmaterial.logic.PlayerKey
import io.github.ivanosh.stmaterial.logic.PlayerUi
import io.github.ivanosh.stmaterial.logic.SKIP_CHIP_MS
import io.github.ivanosh.stmaterial.logic.SkipChip
import io.github.ivanosh.stmaterial.logic.SkipLedger
import io.github.ivanosh.stmaterial.logic.SkipSpan
import io.github.ivanosh.stmaterial.logic.SEEK_DEBOUNCE_MS
import io.github.ivanosh.stmaterial.logic.SeekDebounce
import io.github.ivanosh.stmaterial.logic.SheetCommand
import io.github.ivanosh.stmaterial.logic.moreGroups
import io.github.ivanosh.stmaterial.logic.moreSheetKey
import io.github.ivanosh.stmaterial.logic.playerKey
import io.github.ivanosh.stmaterial.logic.presenterOpenFocus
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
    private val store by lazy { ButtonStore(this) }
    private var ui = PlayerUi(PanelFocus.HIDDEN, 0, panel.buttons.size + 1)
    private var seek = SeekDebounce()
    private var okHold = OkHold()
    private var lastKeyMs = 0L
    private var seekPreviewUntilMs = 0L
    private var backPassedOn = false
    private var ticking = false
    private var started = false
    private var lastSeekKeyMs: Long? = null
    private val ledger = SkipLedger()
    /** LEFT went to the undo, so the repeats of the same press must not seek back. */
    private var undoKeyHeld = false
    private val chipExpired = Runnable { panel.chip = null }

    val isPanelOpen get() = ui.focus != PanelFocus.HIDDEN
    private val isSheetOpen get() = panel.sheetGroups != null

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
            if (view != null && !isSheetOpen && shouldAutoHide(ui, view.playWhenReady, now - lastKeyMs)) {
                setUi(ui.copy(focus = PanelFocus.HIDDEN))
            }
            if (now >= seekPreviewUntilMs) panel.seekPreview = false
            refresh()
            startTicking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyButtons(store.buttons())
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

    override fun onStart() {
        // Before the fragment starts: that is when the presenter may ask for the panel.
        started = true
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        // The editor may have changed them.
        applyButtons(store.buttons())
    }

    override fun onPause() {
        // A press that began before this has no release for us to act on.
        okHold = okHold.reset()
        super.onPause()
    }

    override fun onStop() {
        started = false
        okHold = okHold.reset()
        // Before the fragment releases the engine.
        sendSeekNow()
        hideChip()
        undoKeyHeld = false
        // Only the state: the presenter would take this for a user closing the controls, with a chapter message to follow.
        writeUi(ui.copy(focus = PanelFocus.HIDDEN))
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
            if (key == PlayerKey.Left) undoKeyHeld = false
            if (key == PlayerKey.Back && backPassedOn) {
                backPassedOn = false
                return false
            }
            if (key == PlayerKey.Ok) {
                val pressed = okHold.index
                val (next, action) = okHold.up(ui, event.isCanceled)
                okHold = next
                // A short press of Subscribe (or any button with a hold action) acts now that it is known not to be a hold.
                if (action == OkAction.Press && pressed != null) {
                    lastKeyMs = SystemClock.uptimeMillis()
                    panel.buttons.getOrNull(pressed)?.let(commands::run)
                    refresh()
                    startTicking()
                }
            }
            return true
        }
        val sheet = isSheetOpen
        if (key == PlayerKey.Left && ui.focus == PanelFocus.HIDDEN && !sheet) {
            // The undo chip: LEFT goes back to the start of the skipped segment, and not a step more.
            if (event.repeatCount > 0 && undoKeyHeld) return true
            if (event.repeatCount == 0 && undoSkip()) return true
        }
        if (key == PlayerKey.Ok && !sheet) {
            val onHoldButton = ui.focus == PanelFocus.BUTTONS && panel.buttons.getOrNull(ui.buttonIndex) == PlayerButton.Subscribe
            val (next, action) = okHold.down(ui, onHoldButton, event.repeatCount > 0 || event.isLongPress)
            okHold = next
            if (action != OkAction.Normal) {
                if (event.repeatCount == 0) presenter.onKeyDown(NO_KEY)
                lastKeyMs = SystemClock.uptimeMillis()
                // Hold OK on Subscribe does what the Channel button does and does not subscribe.
                if (action == OkAction.Hold) commands.run(PlayerButton.Channel)
                return true
            }
        }
        val moves = if (sheet) key == PlayerKey.Up || key == PlayerKey.Down else key == PlayerKey.Left || key == PlayerKey.Right
        if (event.repeatCount > 0) {
            // Only a held seek key (or, in More, up or down) repeats, and what waited in the queue is dropped, so nothing moves after release.
            if (!moves || repeatGuard.shouldDrop(event.repeatCount, event.eventTime, SystemClock.uptimeMillis())) return true
        } else {
            presenter.onKeyDown(NO_KEY) // what a touch does: the sleep timer and the like start over
        }
        lastKeyMs = SystemClock.uptimeMillis()
        if (sheet) {
            sheetKey(key)
            return true
        }
        val (next, keyCommands) = playerKey(ui, key, PlayerData.instance(this).seekIncrementMs.toLong())
        setUi(next)
        var passBackOn = false
        for (command in keyCommands) {
            when (command) {
                PlayerCommand.TogglePlay -> commands.togglePlay()
                is PlayerCommand.Seek -> seekBy(command.deltaMs)
                is PlayerCommand.Press -> panel.buttons.getOrNull(command.index)?.let(commands::run)
                PlayerCommand.OpenMore -> openSheet()
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

    /** What the presenter set for a button, which the panel and More show. */
    fun onButtonState(buttonId: Int, state: Int) {
        panel.buttonStates[buttonId] = state
    }

    fun clearButtonStates() {
        panel.buttonStates.clear()
    }

    fun showPanel(show: Boolean) {
        if (!show) {
            setUi(ui.copy(focus = PanelFocus.HIDDEN))
            return
        }
        if (!started || isInPipMode) return
        val now = SystemClock.uptimeMillis()
        if (!isPanelOpen) setUi(ui.copy(focus = presenterOpenFocus(lastSeekKeyMs, now), buttonIndex = 0))
        lastKeyMs = now
        refresh()
        startTicking()
    }

    /** As on the upstream bar, chapters and SponsorBlock add up, and null clears them. */
    fun setMarks(segments: List<SeekBarSegment>?) {
        panel.marks = if (segments == null) emptyList() else (panel.marks + segments.mapNotNull(::markOf)).distinct()
        // Once per change of the marks, not per frame: the legend and whether it needs a line of its own.
        val legend = legendEntries(panel.marks, ::categoryName)
        panel.legend = legend
    }

    /** The name of the SponsorBlock category that draws in this colour, in English whatever the device language. */
    private fun categoryName(argb: Int): String? {
        val data = SponsorBlockData.instance(this)
        val category = data.allCategories.firstOrNull { c ->
            data.getColorRes(c)?.let { ContextCompat.getColor(this, it) } == argb
        } ?: return null
        return SPONSOR_CATEGORY_NAMES[category]?.let(::getString)
    }

    fun setChapter(title: String?) {
        panel.chapter = title
    }

    fun onVideoChanged() {
        handler.removeCallbacks(applySeek)
        seek = SeekDebounce()
        ledger.clear()
        hideChip()
    }

    // Calls from SponsorBlockController through the fragment.

    fun mayAutoSkip(span: SkipSpan): Boolean = ledger.maySkip(span, SystemClock.uptimeMillis())

    /** True when the chip is shown, in place of the toast. The ledger is told whatever the view is doing. */
    fun onAutoSkip(spans: List<SkipSpan>): Boolean {
        val chip = ledger.onAutoSkip(spans, SystemClock.uptimeMillis())
        if (chip == null || !started || isInPipMode) return false
        showUndoChip(chip)
        return true
    }

    private fun showUndoChip(chip: SkipChip) {
        val category = chip.last.category
        val data = SponsorBlockData.instance(this)
        val argb = data.getColorRes(category)?.let { ContextCompat.getColor(this, it) } ?: Color.WHITE
        val name = SPONSOR_CATEGORY_NAMES[category]?.let(::getString) ?: category
        panel.chip = SkipChipUi(chip.shownAtMs, true, name, argb)
        handler.removeCallbacks(chipExpired)
        handler.postDelayed(chipExpired, SKIP_CHIP_MS)
    }

    private fun hideChip() {
        handler.removeCallbacks(chipExpired)
        panel.chip = null
    }

    /** True when the key was the undo. */
    private fun undoSkip(): Boolean {
        val to = ledger.undo(SystemClock.uptimeMillis()) ?: return false
        presenter.onKeyDown(NO_KEY)
        // A seek still waiting would go on after the undo and take the position away.
        handler.removeCallbacks(applySeek)
        seek = SeekDebounce()
        hideChip()
        player?.seekTo(to)
        undoKeyHeld = true
        return true
    }

    /** The panel's buttons from the store. The count of keys moves with them, and an open More follows. */
    private fun applyButtons(buttons: List<PlayerButton>) {
        if (buttons == panel.buttons && ui.buttonCount == buttons.size + 1) return
        panel.buttons = buttons
        writeUi(ui.copy(buttonCount = buttons.size + 1, buttonIndex = ui.buttonIndex.coerceAtMost(buttons.size)))
        if (isSheetOpen) {
            panel.sheetGroups = moreGroups(buttons)
            panel.sheetCursor = panel.sheetCursor.coerceAtMost(sheetEntries().size)
        }
    }

    /** The buttons More lists, in the order of its lines. Customize buttons comes after them. */
    private fun sheetEntries(): List<PlayerButton> = panel.sheetGroups.orEmpty().flatMap { it.buttons }

    private fun openSheet() {
        panel.sheetCursor = 0
        panel.sheetGroups = moreGroups(panel.buttons)
    }

    private fun sheetKey(key: PlayerKey) {
        val entries = sheetEntries()
        val (cursor, command) = moreSheetKey(panel.sheetCursor, entries.size + 1, key)
        panel.sheetCursor = cursor
        when (command) {
            SheetCommand.Close -> panel.sheetGroups = null
            is SheetCommand.Activate -> {
                val button = entries.getOrNull(command.index)
                if (button == null) {
                    panel.sheetGroups = null
                    startActivity(Intent(this, MaterialButtonsEditorActivity::class.java))
                } else {
                    commands.run(button)
                    // A switch shows its new state in place; the rest opens something over the player.
                    if (button !in STATEFUL_BUTTONS || button == PlayerButton.Subtitles) panel.sheetGroups = null
                }
            }
            null -> Unit
        }
    }

    private fun writeUi(next: PlayerUi) {
        ui = next
        if (next.focus == PanelFocus.HIDDEN) {
            panel.sheetGroups = null
            okHold = okHold.reset()
        }
        panel.focus = next.focus
        panel.buttonIndex = next.buttonIndex
    }

    private fun setUi(next: PlayerUi) {
        val wasOpen = isPanelOpen
        writeUi(next)
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
        lastSeekKeyMs = now
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
        panel.title = view.panelTitle
        panel.author = view.video?.author.orEmpty()
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
        val chapter = segment.color == Color.BLACK
        return ProgressMark(segment.startProgress, segment.endProgress.coerceAtMost(1f), chapter, if (chapter) 0 else segment.color)
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
