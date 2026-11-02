package io.github.ivanosh.stmaterial.logic

enum class MenuAction { OpenChannel, Move, Remove, Customize, AddToPanel }

/**
 * The small menu of a hold on a panel button. [anchor] is the button's place on the panel, or its entry in More when
 * [inMore]; [button] is null for More itself.
 */
data class ButtonMenu(
    val actions: List<MenuAction>,
    val cursor: Int = 0,
    val anchor: Int = 0,
    val button: PlayerButton? = null,
    val inMore: Boolean = false,
)

sealed interface MenuStep {
    object None : MenuStep
    object Close : MenuStep
    data class Choose(val action: MenuAction) : MenuStep
}

/** The items for a button on the panel, or for More itself ([button] null). Only Subscribe has a special action. */
fun panelMenuActions(button: PlayerButton?): List<MenuAction> = when (button) {
    null -> listOf(MenuAction.Customize)
    PlayerButton.Subscribe -> listOf(MenuAction.OpenChannel, MenuAction.Move, MenuAction.Remove, MenuAction.Customize)
    else -> listOf(MenuAction.Move, MenuAction.Remove, MenuAction.Customize)
}

/** The items for a button listed in More. */
fun moreMenuActions(): List<MenuAction> = listOf(MenuAction.AddToPanel)

/** One remote key in the menu: Up and Down move, OK chooses, Back closes. */
fun ButtonMenu.key(key: PlayerKey): Pair<ButtonMenu, MenuStep> = when (key) {
    PlayerKey.Up -> copy(cursor = (cursor - 1).coerceAtLeast(0)) to MenuStep.None
    PlayerKey.Down -> copy(cursor = (cursor + 1).coerceAtMost(actions.size - 1)) to MenuStep.None
    PlayerKey.Ok -> this to (actions.getOrNull(cursor)?.let { MenuStep.Choose(it) } ?: MenuStep.None)
    PlayerKey.Back -> this to MenuStep.Close
    PlayerKey.Left, PlayerKey.Right -> this to MenuStep.None
}

/** The result of a change to the panel's list: the new list, where focus goes, and the button pushed out to More, if any. */
data class PanelEdit(val buttons: List<PlayerButton>, val focusIndex: Int, val displaced: PlayerButton? = null)

/** The editor's own logic, on the panel's list; [picked] is the button the change is about. */
private fun editorOf(buttons: List<PlayerButton>, picked: PlayerButton) =
    buttonsEditorState(buttons).copy(picked = picked)

/** Moves the button at [index] by [delta] places, as the editor does; the focus index follows it. */
fun moveButton(buttons: List<PlayerButton>, index: Int, delta: Int): PanelEdit {
    val button = buttons.getOrNull(index) ?: return PanelEdit(buttons, index)
    val moved = editorOf(buttons, button).moveBy(delta).shown
    return PanelEdit(moved, moved.indexOf(button))
}

/** Takes the button at [index] off the panel. Focus goes to its next neighbour, else the previous one, else More. */
fun removeButton(buttons: List<PlayerButton>, index: Int): PanelEdit {
    val button = buttons.getOrNull(index) ?: return PanelEdit(buttons, index)
    val rest = editorOf(buttons, button).toggleList().state.shown
    return PanelEdit(rest, index.coerceAtMost(rest.size - 1).coerceAtLeast(0))
}

/** Puts [button] last on the panel. At [PANEL_LIMIT] the last button goes to More and is [PanelEdit.displaced]. */
fun addButton(buttons: List<PlayerButton>, button: PlayerButton): PanelEdit {
    if (button in buttons) return PanelEdit(buttons, buttons.indexOf(button))
    val result = editorOf(buttons, button).toggleList()
    val shown = result.state.shown
    return PanelEdit(shown, shown.indexOf(button), result.displaced)
}

/** The panel stays open while a menu or Move is. */
fun menuKeepsPanel(menuOpen: Boolean, moving: Boolean) = menuOpen || moving
