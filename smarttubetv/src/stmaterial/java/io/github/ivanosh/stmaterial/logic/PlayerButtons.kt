package io.github.ivanosh.stmaterial.logic

/** The buttons of the first version. The order is the order of the editor's hidden list. */
enum class PlayerButton {
    PlayPause, Next, Previous, Quality, Subtitles, Speed, Like, Dislike, Subscribe, Channel, AddToPlaylist, Queue,
    Repeat, Search, VideoInfo, Share, Zoom, Stats, SeekInterval, SponsorBlock, Dimming, Mute, Afr
}

enum class ButtonGroupKind { Playback, PictureAndSound, VideoAndChannel }

data class ButtonGroup(val kind: ButtonGroupKind, val buttons: List<PlayerButton>)

/** The panel holds this many buttons and More, which is always there and is not a [PlayerButton]. */
const val PANEL_LIMIT = 5

val DEFAULT_BUTTONS = listOf(PlayerButton.PlayPause, PlayerButton.Next, PlayerButton.Quality, PlayerButton.Subtitles, PlayerButton.Speed)

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

/** Saved names to buttons: unknown names and repeats are skipped, the limit holds, and nothing valid gives the default. */
fun resolveButtons(names: List<String>): List<PlayerButton> {
    val buttons = names.mapNotNull { name -> PlayerButton.values().firstOrNull { it.name == name } }.distinct().take(PANEL_LIMIT)
    return buttons.ifEmpty { DEFAULT_BUTTONS }
}

fun buttonsEditorState(shown: List<PlayerButton>): EditorState<PlayerButton> =
    EditorState(shown, PlayerButton.values().filter { it !in shown }, emptySet(), PANEL_LIMIT)

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
