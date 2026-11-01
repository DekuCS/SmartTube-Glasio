package io.github.ivanosh.stmaterial.update

import android.content.Context
import io.github.ivanosh.stmaterial.logic.UpdateChannel

/** The channel and the time of the last check, in prefs of their own. Stable until the owner picks another. */
class UpdateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("material_update", Context.MODE_PRIVATE)

    var channel: UpdateChannel
        get() = UpdateChannel.fromKey(prefs.getString(KEY_CHANNEL, null))
        set(value) = prefs.edit().putString(KEY_CHANNEL, value.key).apply()

    var lastCheckMs: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    private companion object {
        const val KEY_CHANNEL = "channel"
        const val KEY_LAST_CHECK = "last_check_ms"
    }
}
