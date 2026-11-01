package io.github.ivanosh.stmaterial.data

import android.content.Context
import io.github.ivanosh.stmaterial.logic.EditorState
import io.github.ivanosh.stmaterial.logic.PANEL_LIMIT
import io.github.ivanosh.stmaterial.logic.PlayerButton
import io.github.ivanosh.stmaterial.logic.buttonsEditorState
import io.github.ivanosh.stmaterial.logic.parseSavedButtons

/** The buttons on the player panel, as enum names in the app's own prefs. */
class ButtonStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun buttons(): List<PlayerButton> =
        // Never saved: the key is absent. Saved with no buttons: it holds the empty string.
        parseSavedButtons(prefs.getString(KEY_BUTTONS, null), marked = prefs.getInt(KEY_VERSION, 1) >= VERSION)

    fun load(): EditorState<PlayerButton> = buttonsEditorState(buttons())

    fun save(state: EditorState<PlayerButton>) {
        prefs.edit()
            .putString(KEY_BUTTONS, state.shown.take(PANEL_LIMIT).distinct().joinToString(",") { it.name })
            .putInt(KEY_VERSION, VERSION) // from here on a list equal to the old default is a choice
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "stmaterial_player_buttons"
        const val KEY_BUTTONS = "buttons"
        const val KEY_VERSION = "version"
        const val VERSION = 2
    }
}
