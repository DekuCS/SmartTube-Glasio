package io.github.ivanosh.stmaterial.update

import android.app.Activity
import io.github.ivanosh.stmaterial.logic.shouldCheck

/**
 * The check on app start: at most once a day, started when the browse screen is shown. A check that finds an update
 * stores it ([UpdateStore.recordCheck]); the mark on the Settings icon and the row in Settings show it. Nothing opens
 * by itself, nothing runs in the background and nothing repeats.
 */
object UpdateScheduler {
    private var checking = false

    fun onBrowseResumed(activity: Activity, now: Long = System.currentTimeMillis()) {
        val store = UpdateStore(activity)
        // The offer may have been installed, or the channel changed, since the screen was last shown.
        store.currentOffer(activity)
        val channel = store.channel
        // A file left by a killed process goes whether or not a check is due.
        if (!checking) UpdateClient.deleteLeftovers(activity)
        if (checking || !shouldCheck(channel, store.lastCheckMs, now)) return
        store.lastCheckMs = now
        checking = true
        val app = activity.applicationContext
        UpdateService.check(activity, channel) { result ->
            checking = false
            UpdateStore(app).recordCheck(app, result)
        }
    }
}
