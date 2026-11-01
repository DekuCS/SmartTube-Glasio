package io.github.ivanosh.stmaterial.update

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager
import io.github.ivanosh.stmaterial.logic.offerAllowed
import io.github.ivanosh.stmaterial.logic.UpdateOffer
import io.github.ivanosh.stmaterial.logic.shouldCheck
import java.lang.ref.WeakReference

/**
 * The check on app start: at most once a day, started when the browse screen is shown, and the prompt only ever
 * opens over that screen. A result that arrives while the player (or anything else) is in front waits for the
 * browse screen to be shown again. Nothing runs in the background and nothing repeats.
 */
object UpdateScheduler {
    private const val SETTLE_MS = 400L
    private val handler = Handler(Looper.getMainLooper())
    private var pending: UpdateOffer? = null
    private var checking = false
    private var active: WeakReference<Activity>? = null

    /** The offer of the dialog being opened; read once by [UpdateActivity]. */
    @Volatile
    var offerForDialog: UpdateOffer? = null
        private set

    fun takeOfferForDialog(): UpdateOffer? = offerForDialog.also { offerForDialog = null }

    fun onBrowseResumed(activity: Activity, now: Long = System.currentTimeMillis()) {
        active = WeakReference(activity)
        if (pending != null) {
            showPending(activity)
            return
        }
        val store = UpdateStore(activity)
        val channel = store.channel
        // A file left by a killed process goes whether or not a check is due.
        if (!checking) UpdateClient.deleteLeftovers(activity)
        if (checking || !shouldCheck(channel, store.lastCheckMs, now)) return
        store.lastCheckMs = now
        checking = true
        UpdateService.check(activity, channel) { result ->
            checking = false
            if (result is CheckResult.Offer) {
                pending = result.offer
                // Only if the browse screen is still the one in front a moment later (it may be starting the
                // player); otherwise onBrowseResumed shows it next time.
                handler.postDelayed({ active?.get()?.let(::showPending) }, SETTLE_MS)
            }
        }
    }

    fun onBrowsePaused(activity: Activity) {
        if (active?.get() === activity) active = null
    }

    private fun showPending(activity: Activity) {
        val offer = pending ?: return
        // The player is starting: the offer waits for the next time browse is in front.
        if (ViewManager.instance(activity).isViewPending(PlaybackView::class.java)) return
        pending = null
        // The channel may have changed since the check.
        if (!offerAllowed(offer, UpdateStore(activity).channel)) return
        offerForDialog = offer
        activity.startActivity(Intent(activity, UpdateActivity::class.java))
    }
}
