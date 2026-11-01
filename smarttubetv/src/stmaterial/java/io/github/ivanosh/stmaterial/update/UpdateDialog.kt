package io.github.ivanosh.stmaterial.update

import android.app.Activity
import io.github.ivanosh.stmaterial.dialog.ButtonRow
import io.github.ivanosh.stmaterial.dialog.DialogPage
import io.github.ivanosh.stmaterial.dialog.DialogRow
import io.github.ivanosh.stmaterial.dialog.DialogState
import io.github.ivanosh.stmaterial.dialog.TextRow
import io.github.ivanosh.stmaterial.logic.UpdateOffer
import io.github.ivanosh.stmaterial.logic.checkDownloaded
import com.liskovsoft.smartyoutubetv2.tv.R
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The update dialog in the app's own dialog component: the offer, the download, a refusal or the hand-off to the
 * system installer. Used by the screen that opens over browse and by Settings, so both look and behave the same.
 * [close] leaves the dialog (a page back in Settings, the activity in the prompt).
 */
class UpdateDialog(private val activity: Activity, private val state: DialogState, private val close: () -> Unit) {
    private val cancelled = AtomicBoolean(false)
    private var busy = false
    private val installer = UpdateInstaller(activity) { outcome ->
        busy = false
        when (outcome) {
            UpdateInstaller.Outcome.CONFIRMING -> show(R.string.material_update_confirming, closeOnly = true)
            UpdateInstaller.Outcome.CANCELLED -> show(R.string.material_update_cancelled, closeOnly = true)
            UpdateInstaller.Outcome.FAILED -> show(R.string.material_update_install_failed, closeOnly = true)
        }
    }

    fun activate(row: DialogRow) {
        (row as? ButtonRow)?.let { (it.ref as? Runnable)?.run() }
    }

    fun offerPage(offer: UpdateOffer): DialogPage {
        val title = activity.getString(R.string.material_update_title, offer.versionName) +
            if (offer.prerelease) " " + activity.getString(R.string.material_update_prerelease_tag) else ""
        val rows = mutableListOf<DialogRow>(
            ButtonRow(1, activity.getString(R.string.material_update_install), Runnable { start(offer) }),
            ButtonRow(2, activity.getString(R.string.material_update_later), Runnable { close() }),
        )
        offer.notes.forEachIndexed { i, line -> rows += TextRow(10 + i, line) }
        return DialogPage.of(title, rows)
    }

    private fun start(offer: UpdateOffer) {
        if (busy) return
        busy = true
        cancelled.set(false)
        progress(0)
        val app = activity.applicationContext
        UpdateService.runInBackground {
            val file = UpdateClient.apkFile(app)
            var verified = false
            try {
                val client = UpdateClient(app)
                val downloaded = client.download(offer, file, { p -> UpdateService.postMain { if (busy) progress(p) } }, { cancelled.get() })
                val refusal = checkDownloaded(
                    offer, downloaded.sha256, downloaded.size, client.archiveFacts(file),
                    app.packageName, UpdateService.installedVersionCode(app), client.pinnedCert(),
                )
                if (refusal != null) {
                    UpdateService.postMain { busy = false; refused(refusal.message) }
                    return@runInBackground
                }
                verified = true
                UpdateService.postMain {
                    if (activity.isDestroyed) {
                        file.delete()
                    } else {
                        show(R.string.material_update_waiting, closeOnly = false)
                        // install() deletes the file whatever happens.
                        if (!installer.install(file)) {
                            busy = false
                            refused(activity.getString(R.string.material_update_install_failed))
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                UpdateService.postMain { busy = false; refused(if (cancelled.get()) activity.getString(R.string.material_update_cancelled) else activity.getString(R.string.material_update_download_failed)) }
            } finally {
                // A verified file belongs to the install, which deletes it; any other file goes now.
                if (!verified) file.delete()
            }
        }
    }

    private fun progress(percent: Int) {
        state.replace(
            DialogPage.of(
                activity.getString(R.string.material_update_downloading, percent),
                listOf(ButtonRow(1, activity.getString(R.string.material_update_cancel), Runnable { cancelled.set(true) })),
            )
        )
    }

    private fun refused(message: String) {
        state.replace(
            DialogPage.of(
                activity.getString(R.string.material_update_not_installed),
                listOf(TextRow(1, message), ButtonRow(2, activity.getString(R.string.material_update_close), Runnable { close() })),
            )
        )
    }

    private fun show(text: Int, closeOnly: Boolean) {
        val rows = mutableListOf<DialogRow>(TextRow(1, activity.getString(text)))
        if (closeOnly) rows += ButtonRow(2, activity.getString(R.string.material_update_close), Runnable { close() })
        state.replace(DialogPage.of(activity.getString(R.string.material_update_heading), rows))
    }

    fun release() {
        installer.unregister()
        cancelled.set(true)
        // A download still running is stopped by the flag; a file it leaves is removed by the next check.
        if (!busy) UpdateClient.deleteLeftovers(activity)
    }
}
