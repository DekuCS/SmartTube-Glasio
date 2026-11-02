package io.github.ivanosh.stmaterial.update

import android.app.Activity
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.dialog.ButtonRow
import io.github.ivanosh.stmaterial.dialog.DialogPage
import io.github.ivanosh.stmaterial.dialog.DialogRow
import io.github.ivanosh.stmaterial.dialog.DialogState
import io.github.ivanosh.stmaterial.dialog.TextRow
import io.github.ivanosh.stmaterial.logic.Refusal
import io.github.ivanosh.stmaterial.logic.UpdateEnd
import io.github.ivanosh.stmaterial.logic.UpdateOffer
import io.github.ivanosh.stmaterial.logic.workEnd
import io.github.ivanosh.stmaterial.logic.checkDownloaded
import io.github.ivanosh.stmaterial.logic.offerAllowed
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The update dialog in the app's own dialog component: the offer, the download, a refusal or the hand-off to the
 * system installer. Used by the screen that opens over browse and by Settings, so both look and behave the same.
 * [close] leaves the dialog (a page back in Settings, the activity in the prompt).
 *
 * The dialog only ever replaces its own page: once the user has left it, late progress or results change nothing.
 * [onBack] is for the owner's Back handler: while a download or a hand-off runs, Back cancels it and stays.
 */
class UpdateDialog(private val activity: Activity, private val state: DialogState, private val close: () -> Unit) {
    private enum class Phase { IDLE, DOWNLOADING, INSTALLING }

    private val cancelled = AtomicBoolean(false)
    private var phase = Phase.IDLE
    private var ownPage = -1
    private val installer = UpdateInstaller(activity) { outcome ->
        phase = Phase.IDLE
        if (!cancelled.get()) when (outcome) {
            UpdateInstaller.Outcome.CONFIRMING -> show(R.string.material_update_confirming)
            UpdateInstaller.Outcome.CANCELLED -> show(R.string.material_update_cancelled)
            UpdateInstaller.Outcome.FAILED -> show(R.string.material_update_install_failed)
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

    /** True when Back was used up by cancelling something that was running. */
    fun onBack(): Boolean {
        when (phase) {
            Phase.IDLE -> return false
            Phase.DOWNLOADING -> {
                // The worker sees the flag at its next step and always ends the phase, whichever step that is.
                cancelled.set(true)
                installer.abandon()
            }
            Phase.INSTALLING -> {
                cancelled.set(true)
                installer.abandon()
                phase = Phase.IDLE
                refused(activity.getString(R.string.material_update_cancelled))
            }
        }
        return true
    }

    private fun start(offer: UpdateOffer) {
        if (phase != Phase.IDLE) return
        // The channel may have changed since the check: Off downloads nothing, Stable never a prerelease.
        if (!offerAllowed(offer, UpdateStore(activity).channel)) {
            ownPage = state.page?.id ?: -1
            refused(activity.getString(R.string.material_update_channel_changed))
            return
        }
        phase = Phase.DOWNLOADING
        cancelled.set(false)
        ownPage = state.page?.id ?: -1
        progress(0)
        val app = activity.applicationContext
        UpdateService.runInBackground {
            val file = UpdateClient.apkFile(app)
            var verified = false
            var committed = false
            var refusal: Refusal? = null
            var downloadFailed = false
            try {
                val client = UpdateClient(app)
                val downloaded = client.download(offer, file, { p -> UpdateService.postMain { if (phase == Phase.DOWNLOADING) progress(p) } }, { cancelled.get() })
                if (cancelled.get()) throw java.io.IOException("Cancelled")
                refusal = checkDownloaded(
                    offer, downloaded.sha256, downloaded.size, client.archiveFacts(file),
                    app.packageName, UpdateService.installedVersionCode(app), client.pinnedCert(),
                )
                if (refusal == null && !cancelled.get()) {
                    verified = true
                    UpdateService.postMain { if (phase == Phase.DOWNLOADING && !cancelled.get()) { phase = Phase.INSTALLING; showText(activity.getString(R.string.material_update_preparing, 0)) } }
                    // The copy into the system's session, off the main thread. install() deletes the file.
                    committed = installer.install(
                        file, offer.asset.sha256.orEmpty(), { cancelled.get() },
                        // Whole percents only, from the copy loop; a late one after Back or a result changes nothing.
                        onPercent = { p -> UpdateService.postMain { if (phase == Phase.INSTALLING && !cancelled.get()) showText(activity.getString(R.string.material_update_preparing, p)) } },
                        onFinishing = { UpdateService.postMain { if (phase == Phase.INSTALLING && !cancelled.get()) showText(activity.getString(R.string.material_update_finishing)) } },
                    )
                }
            } catch (e: java.io.IOException) {
                downloadFailed = true
            } catch (e: RuntimeException) {
                downloadFailed = true
            } finally {
                // A verified file belongs to the install, which deletes it; any other file goes now.
                if (!verified) file.delete()
            }
            // Every end but a commit is final, whatever was pressed meanwhile: the dialog is never left busy.
            val end = workEnd(committed, cancelled.get(), refusal, downloadFailed)
            UpdateService.postMain {
                if (end != UpdateEnd.WAIT_FOR_SYSTEM) {
                    phase = Phase.IDLE
                    refused(when (end) {
                        UpdateEnd.CANCELLED -> activity.getString(R.string.material_update_cancelled)
                        UpdateEnd.REFUSED -> refusal?.message.orEmpty()
                        UpdateEnd.DOWNLOAD_FAILED -> activity.getString(R.string.material_update_download_failed)
                        else -> activity.getString(R.string.material_update_install_failed)
                    })
                }
            }
        }
    }

    private fun onOwnPage() = state.page?.id == ownPage

    private fun progress(percent: Int) {
        if (!onOwnPage()) return
        state.replace(
            DialogPage.of(
                activity.getString(R.string.material_update_downloading, percent),
                listOf(ButtonRow(1, activity.getString(R.string.material_update_cancel), Runnable { cancelled.set(true) })),
            )
        )
    }

    private fun refused(message: String) {
        if (!onOwnPage()) return
        state.replace(
            DialogPage.of(
                activity.getString(R.string.material_update_not_installed),
                listOf(TextRow(1, message), ButtonRow(2, activity.getString(R.string.material_update_close), Runnable { close() })),
            )
        )
    }

    /** A page with one line and no button: Back cancels. */
    private fun showText(text: String) {
        if (!onOwnPage()) return
        state.replace(DialogPage.of(activity.getString(R.string.material_update_heading), listOf(TextRow(1, text))))
    }

    private fun show(text: Int, withClose: Boolean = true) {
        if (!onOwnPage()) return
        val rows = mutableListOf<DialogRow>(TextRow(1, activity.getString(text)))
        if (withClose) rows += ButtonRow(2, activity.getString(R.string.material_update_close), Runnable { close() })
        state.replace(DialogPage.of(activity.getString(R.string.material_update_heading), rows))
    }

    fun release() {
        cancelled.set(true)
        if (phase == Phase.INSTALLING) installer.abandon() else installer.unregister()
        // A download still running is stopped by the flag and removes its own file.
        if (phase == Phase.IDLE) UpdateClient.deleteLeftovers(activity)
    }
}
