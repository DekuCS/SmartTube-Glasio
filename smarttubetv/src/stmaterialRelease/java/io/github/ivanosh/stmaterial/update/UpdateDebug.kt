package io.github.ivanosh.stmaterial.update

import android.content.Context

/** A release build has no way to point the updater anywhere else: this is the whole of it. */
class UpdateOverride(val base: String, val cert: String?)

object UpdateDebug {
    @Suppress("UNUSED_PARAMETER")
    fun override(context: Context): UpdateOverride? = null
}
