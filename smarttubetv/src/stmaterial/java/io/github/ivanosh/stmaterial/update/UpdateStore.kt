package io.github.ivanosh.stmaterial.update

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ivanosh.stmaterial.logic.UpdateChannel
import io.github.ivanosh.stmaterial.logic.UpdateOffer
import io.github.ivanosh.stmaterial.logic.badgeOffer
import io.github.ivanosh.stmaterial.logic.offerFromJson
import io.github.ivanosh.stmaterial.logic.offerToJson
import io.github.ivanosh.stmaterial.logic.storedOfferAfterCheck

/**
 * The channel, the time of the last check and the newest offer a check found, in prefs of their own. Stable until the
 * owner picks another.
 */
class UpdateStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("material_update", Context.MODE_PRIVATE)

    var channel: UpdateChannel
        get() = UpdateChannel.fromKey(prefs.getString(KEY_CHANNEL, null))
        set(value) = prefs.edit().putString(KEY_CHANNEL, value.key).apply()

    var lastCheckMs: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    /**
     * The stored offer if it is still to be shown (see [badgeOffer]); one that no longer is, or that is not a valid
     * offer, is removed. Reading also publishes it to [shown], which only changes when the offer does.
     */
    fun currentOffer(context: Context): UpdateOffer? {
        val raw = prefs.getString(KEY_OFFER, null)
        // Nothing stored is the common case: skip the binder call of the installed version.
        if (raw == null) {
            shown = null
            return null
        }
        val offer = badgeOffer(offerFromJson(raw), channel, UpdateService.installedVersionName(context))
        if (offer == null) prefs.edit().remove(KEY_OFFER).apply()
        shown = offer
        return offer
    }

    /** Keeps what a finished check says: the offer it found, nothing if it found none, the old one if it failed. */
    fun recordCheck(context: Context, result: CheckResult) {
        val stored = offerFromJson(prefs.getString(KEY_OFFER, null))
        val next = storedOfferAfterCheck(
            stored,
            (result as? CheckResult.Offer)?.offer,
            result !is CheckResult.Failed,
        )
        prefs.edit().apply { if (next == null) remove(KEY_OFFER) else putString(KEY_OFFER, offerToJson(next)) }.apply()
        currentOffer(context)
    }

    companion object {
        private const val KEY_CHANNEL = "channel"
        private const val KEY_LAST_CHECK = "last_check_ms"
        private const val KEY_OFFER = "offer"

        /** The offer on show, for the screens: a state that is written only when the offer changes. */
        var shown by mutableStateOf<UpdateOffer?>(null)
            private set
    }
}
