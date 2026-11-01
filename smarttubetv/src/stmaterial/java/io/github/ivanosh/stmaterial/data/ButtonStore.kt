package io.github.ivanosh.stmaterial.data

import android.content.Context
import io.github.ivanosh.stmaterial.logic.DEFAULT_BUTTONS
import io.github.ivanosh.stmaterial.logic.EditorState
import io.github.ivanosh.stmaterial.logic.PANEL_LIMIT
import io.github.ivanosh.stmaterial.logic.PlayerButton
import io.github.ivanosh.stmaterial.logic.buttonsEditorState
import io.github.ivanosh.stmaterial.logic.resolveButtons

/** The buttons on the player panel, as enum names in the app's own prefs. */
class ButtonStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun buttons(): List<PlayerButton> {
        // Never saved: the key is absent. Saved with no buttons: it holds the empty string.
        val raw = prefs.getString(KEY_BUTTONS, null) ?: return DEFAULT_BUTTONS
        return resolveButtons(if (raw.isBlank()) emptyList() else raw.split(',').map { it.trim() })
    }

    fun load(): EditorState<PlayerButton> = buttonsEditorState(buttons())

    fun save(state: EditorState<PlayerButton>) {
        prefs.edit().putString(KEY_BUTTONS, state.shown.take(PANEL_LIMIT).distinct().joinToString(",") { it.name }).apply()
    }

    private companion object {
        const val PREFS_NAME = "stmaterial_player_buttons"
        const val KEY_BUTTONS = "buttons"
    }
}
