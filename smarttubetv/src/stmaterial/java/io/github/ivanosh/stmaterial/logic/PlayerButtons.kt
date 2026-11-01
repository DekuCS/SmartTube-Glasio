package io.github.ivanosh.stmaterial.logic

/** The buttons of the first version. The order is the order of the editor's hidden list. */
enum class PlayerButton {
    PlayPause, Next, Previous, Quality, Subtitles, Speed, Like, Dislike, Subscribe, Channel, AddToPlaylist, Queue,
    Repeat, Search, VideoInfo, Share, Zoom, Stats, SeekInterval, SponsorBlock, Dimming, Mute, Afr
}

enum class ButtonGroupKind { Playback, PictureAndSound, VideoAndChannel }

data class ButtonGroup(val kind: ButtonGroupKind, val buttons: List<PlayerButton>)

/**
 * The panel holds up to this many buttons and More, which is always there and is not a [PlayerButton].
 * The owner raised the limit from five to eight on 2026-11-01; any button can be switched off, down to none.
 */
const val PANEL_LIMIT = 8

/** The first version's default. A saved list equal to it was never chosen by the owner, so it becomes [DEFAULT_BUTTONS]. */
val LEGACY_DEFAULT_BUTTONS = listOf(PlayerButton.PlayPause, PlayerButton.Next, PlayerButton.Quality, PlayerButton.Subtitles, PlayerButton.Speed)

val DEFAULT_BUTTONS = listOf(
    PlayerButton.PlayPause, PlayerButton.Next, PlayerButton.Quality, PlayerButton.Subtitles, PlayerButton.Speed,
    PlayerButton.Like, PlayerButton.Dislike, PlayerButton.Subscribe
)

fun groupOf(button: PlayerButton): ButtonGroupKind = when (button) {
    PlayerButton.PlayPause, PlayerButton.Previous, PlayerButton.Next, PlayerButton.Speed, PlayerButton.Repeat,
    PlayerButton.SeekInterval, PlayerButton.SponsorBlock, PlayerButton.Queue -> ButtonGroupKind.Playback
    PlayerButton.Quality, PlayerButton.Subtitles, PlayerButton.Zoom, PlayerButton.Mute, PlayerButton.Dimming,
    PlayerButton.Afr, PlayerButton.Stats -> ButtonGroupKind.PictureAndSound
    PlayerButton.Like, PlayerButton.Dislike, PlayerButton.Subscribe, PlayerButton.Channel, PlayerButton.AddToPlaylist,
    PlayerButton.Search, PlayerButton.VideoInfo, PlayerButton.Share -> ButtonGroupKind.VideoAndChannel
}

/** What More lists: every button that is not on the panel, by group. A group with nothing left is left out. */
fun moreGroups(shown: List<PlayerButton>): List<ButtonGroup> =
    ButtonGroupKind.values().map { kind ->
        ButtonGroup(kind, PlayerButton.values().filter { groupOf(it) == kind && it !in shown })
    }.filter { it.buttons.isNotEmpty() }

/**
 * Saved names to buttons: unknown names and repeats are skipped and the limit holds.
 * [names] null means nothing was ever saved, which gives the default; a saved empty list stays empty, since
 * the owner may switch every button off. A list with names but none of them known is damaged and gives the default.
 * The first version's default is migrated to the new one.
 */
fun resolveButtons(names: List<String>?): List<PlayerButton> {
    if (names == null) return DEFAULT_BUTTONS
    if (names.isEmpty()) return emptyList()
    val buttons = names.mapNotNull { name -> PlayerButton.values().firstOrNull { it.name == name } }.distinct().take(PANEL_LIMIT)
    return when {
        buttons.isEmpty() -> DEFAULT_BUTTONS
        buttons == LEGACY_DEFAULT_BUTTONS -> DEFAULT_BUTTONS
        else -> buttons
    }
}

fun buttonsEditorState(shown: List<PlayerButton>): EditorState<PlayerButton> =
    EditorState(shown, PlayerButton.values().filter { it !in shown }, emptySet(), PANEL_LIMIT, minShown = 0)

sealed interface SheetCommand {
    object Close : SheetCommand
    data class Activate(val index: Int) : SheetCommand
}

/** One remote key in More, whose [count] rows end with Customize buttons. Gives the new cursor and what to do. */
fun moreSheetKey(cursor: Int, count: Int, key: PlayerKey): Pair<Int, SheetCommand?> = when (key) {
    PlayerKey.Up -> (cursor - 1).coerceAtLeast(0) to null
    PlayerKey.Down -> (cursor + 1).coerceAtMost(count - 1) to null
    PlayerKey.Ok -> cursor to SheetCommand.Activate(cursor)
    PlayerKey.Back -> cursor to SheetCommand.Close
    PlayerKey.Left, PlayerKey.Right -> cursor to null
}

private const val STATE_ON = 1
private const val PLAYBACK_MODE_ONE = 3

/** Whether a button with state shows as switched on. [state] is what the presenter last set for it, if anything. */
fun buttonIsOn(button: PlayerButton, state: Int?): Boolean =
    if (button == PlayerButton.Repeat) state == PLAYBACK_MODE_ONE else state == STATE_ON
