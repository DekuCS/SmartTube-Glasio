package io.github.ivanosh.stmaterial.logic

enum class PanelFocus { HIDDEN, PROGRESS, BUTTONS }

enum class PlayerKey { Up, Down, Left, Right, Ok, Back }

/** [buttonCount] counts the buttons on the panel and More, which is always the last one. */
data class PlayerUi(val focus: PanelFocus, val buttonIndex: Int, val buttonCount: Int)

sealed interface PlayerCommand {
    object TogglePlay : PlayerCommand
    data class Seek(val deltaMs: Long) : PlayerCommand
    data class Press(val index: Int) : PlayerCommand
    object Exit : PlayerCommand
    object OpenMore : PlayerCommand
}

/** The key table of the player. Every key event gives its own result, so a held key never queues anything here. */
fun playerKey(ui: PlayerUi, key: PlayerKey, seekStepMs: Long): Pair<PlayerUi, List<PlayerCommand>> =
    when (ui.focus) {
        PanelFocus.HIDDEN -> when (key) {
            PlayerKey.Ok -> ui.copy(focus = PanelFocus.BUTTONS, buttonIndex = 0) to listOf(PlayerCommand.TogglePlay)
            PlayerKey.Up, PlayerKey.Down -> ui.copy(focus = PanelFocus.BUTTONS, buttonIndex = 0) to emptyList()
            PlayerKey.Left -> ui to listOf(PlayerCommand.Seek(-seekStepMs))
            PlayerKey.Right -> ui to listOf(PlayerCommand.Seek(seekStepMs))
            PlayerKey.Back -> ui to listOf(PlayerCommand.Exit)
        }
        PanelFocus.PROGRESS -> when (key) {
            PlayerKey.Ok -> ui to listOf(PlayerCommand.TogglePlay)
            PlayerKey.Up -> ui to emptyList()
            PlayerKey.Down -> ui.copy(focus = PanelFocus.BUTTONS) to emptyList()
            PlayerKey.Left -> ui to listOf(PlayerCommand.Seek(-seekStepMs))
            PlayerKey.Right -> ui to listOf(PlayerCommand.Seek(seekStepMs))
            PlayerKey.Back -> ui.copy(focus = PanelFocus.HIDDEN) to emptyList()
        }
        PanelFocus.BUTTONS -> when (key) {
            PlayerKey.Ok -> ui to listOf(
                if (ui.buttonIndex == ui.buttonCount - 1) PlayerCommand.OpenMore else PlayerCommand.Press(ui.buttonIndex)
            )
            PlayerKey.Up -> ui.copy(focus = PanelFocus.PROGRESS) to emptyList()
            PlayerKey.Down -> ui to emptyList()
            PlayerKey.Left -> ui.copy(buttonIndex = (ui.buttonIndex - 1).coerceAtLeast(0)) to emptyList()
            PlayerKey.Right -> ui.copy(buttonIndex = (ui.buttonIndex + 1).coerceAtMost(ui.buttonCount - 1)) to emptyList()
            PlayerKey.Back -> ui.copy(focus = PanelFocus.HIDDEN) to emptyList()
        }
    }

const val SEEK_DEBOUNCE_MS = 300L
const val PANEL_AUTO_HIDE_MS = 5_000L

/**
 * A seek on its way to the engine. Every key moves [targetMs], which the overlay shows at once; the engine
 * gets one seek when [SEEK_DEBOUNCE_MS] have passed since the last key, so a held key costs it nothing.
 */
data class SeekDebounce(val targetMs: Long? = null, val lastKeyMs: Long = 0) {
    fun onKey(positionMs: Long, deltaMs: Long, durationMs: Long, nowMs: Long): SeekDebounce {
        val moved = (targetMs ?: positionMs.coerceAtLeast(0)) + deltaMs
        // The duration of a live stream or of a video still loading can be unknown.
        val target = if (durationMs > 0) moved.coerceIn(0, durationMs) else moved.coerceAtLeast(0)
        return SeekDebounce(target, nowMs)
    }

    /** The seek to send now, if it is due, and what remains. */
    fun takeDue(nowMs: Long): Pair<SeekDebounce, Long?> =
        if (targetMs != null && nowMs - lastKeyMs >= SEEK_DEBOUNCE_MS) SeekDebounce() to targetMs else this to null
}

/** A paused video keeps the panel open. */
fun shouldAutoHide(ui: PlayerUi, playing: Boolean, idleMs: Long): Boolean =
    ui.focus != PanelFocus.HIDDEN && playing && idleMs >= PANEL_AUTO_HIDE_MS

const val SEEK_KEEPS_FOCUS_MS = 2_000L

/**
 * Where the panel opens when the presenter asks for it, such as after an engine reload of a paused video.
 * A seek key held or just pressed has to go on seeking, so such an open must not put focus on the buttons.
 */
fun presenterOpenFocus(lastSeekKeyMs: Long?, nowMs: Long): PanelFocus =
    if (lastSeekKeyMs != null && nowMs - lastSeekKeyMs < SEEK_KEEPS_FOCUS_MS) PanelFocus.PROGRESS else PanelFocus.BUTTONS

fun formatPlayerTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1_000
    val hours = seconds / 3_600
    val minutes = seconds % 3_600 / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

enum class OkAction {
    /** Not a held-capable press: [playerKey] decides, as it always did. */
    Normal,
    /** The key does nothing here: a repeat, or the release of a press that has been dealt with. */
    Swallow,
    /** A short press of a button with a hold action, now that the key is released. Presses the tracked button. */
    Press,
    /** OK is held on a button with a hold action. */
    Hold,
}

/**
 * OK on a button that has a hold action, such as Subscribe (short press subscribes, hold opens the channel).
 * Such a press cannot act when the key goes down, since it is not known yet which one it is. So it is
 * followed from its first down event: a repeat is the hold, which fires once; a release before any repeat is the short press.
 * A press that began anywhere else, such as the OK that opened the panel, is never tracked and so never acts on release.
 * Every fresh down event (repeat count 0) starts over, so a release that was never seen cannot leave the state stuck.
 */
data class OkHold(val index: Int? = null, val fired: Boolean = false) {
    fun down(ui: PlayerUi, holdable: Boolean, repeating: Boolean): Pair<OkHold, OkAction> = when {
        !repeating ->
            if (ui.focus == PanelFocus.BUTTONS && ui.buttonIndex < ui.buttonCount - 1 && holdable) OkHold(ui.buttonIndex) to OkAction.Swallow
            else OkHold() to OkAction.Normal
        index != null && !fired -> OkHold(index, fired = true) to OkAction.Hold
        else -> this to OkAction.Swallow
    }

    fun up(): Pair<OkHold, OkAction> =
        OkHold() to if (index != null && !fired) OkAction.Press else OkAction.Swallow
}
