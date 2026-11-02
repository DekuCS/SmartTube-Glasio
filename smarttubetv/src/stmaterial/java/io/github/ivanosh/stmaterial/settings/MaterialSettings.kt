package io.github.ivanosh.stmaterial.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.YTSignInPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AutoFrameRateSettingsPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.DeArrowSettingsPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.LanguageSettingsPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.PlayerSettingsPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SponsorBlockSettingsPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.SubtitleSettingsPresenter
import com.liskovsoft.smartyoutubetv2.common.prefs.BlockedChannelData
import com.liskovsoft.smartyoutubetv2.common.utils.AppDialogUtil
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import io.github.ivanosh.stmaterial.browse.MaterialSectionsEditorActivity
import io.github.ivanosh.stmaterial.data.LearnedSkipStore
import io.github.ivanosh.stmaterial.dialog.ButtonRow
import io.github.ivanosh.stmaterial.dialog.ChoiceRow
import io.github.ivanosh.stmaterial.dialog.DialogPage
import io.github.ivanosh.stmaterial.dialog.DialogRow
import io.github.ivanosh.stmaterial.dialog.DialogScreen
import io.github.ivanosh.stmaterial.dialog.DialogState
import io.github.ivanosh.stmaterial.dialog.EntryRow
import io.github.ivanosh.stmaterial.dialog.TextRow
import io.github.ivanosh.stmaterial.player.MaterialButtonsEditorActivity
import io.github.ivanosh.stmaterial.logic.UpdateChannel
import io.github.ivanosh.stmaterial.logic.offerAllowed
import io.github.ivanosh.stmaterial.ui.MaterialTvTheme
import io.github.ivanosh.stmaterial.update.CheckResult
import io.github.ivanosh.stmaterial.update.Failure
import io.github.ivanosh.stmaterial.update.UpdateDialog
import io.github.ivanosh.stmaterial.update.UpdateService
import io.github.ivanosh.stmaterial.update.UpdateStore
import io.github.ivanosh.stmaterial.ui.Palette

/** The paragraphs of a text that is wrapped by lines: a blank line ends one, the lines of one are joined by spaces. */
fun paragraphs(text: String): List<String> =
    text.replace("\r\n", "\n").split(Regex("\n\\s*\n")).map { it.lines().joinToString(" ") { line -> line.trim() }.trim() }.filter { it.isNotEmpty() }

/**
 * The settings of the app: a short list in the look of the dialogs, with a page for the account, the
 * blocked channels, Advanced and About. Every other item opens the presenter that the old settings used
 * for it, as a dialog. Back leaves a page, then the screen.
 */
class MaterialSettingsActivity : ComponentActivity() {
    private val state = DialogState()
    /** The sign-in screen is a screen of its own; when it is closed the account page has to show the result. */
    private var accountPending = false
    private lateinit var updates: UpdateDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updates = UpdateDialog(this, state) { state.pop() }
        state.push(root())
        setContentView(ComposeView(this).apply {
            setContent {
                MaterialTvTheme {
                    Box(Modifier.fillMaxSize().background(Palette.Background)) { DialogScreen(state, ::activate) }
                }
            }
        })
        window.setBackgroundDrawable(null)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (updates.onBack()) return
                if (!state.pop()) finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (accountPending) {
            accountPending = false
            state.replace(accountPage())
        }
    }

    private fun activate(row: DialogRow) {
        when (row) {
            is EntryRow -> state.open(row)
            is ButtonRow -> (row.ref as? Runnable)?.run()
            is ChoiceRow -> (row.ref as? UpdateChannel)?.let {
                state.choose(row.key)
                UpdateStore(this).channel = it
            }
            else -> Unit
        }
    }

    private fun button(key: Int, title: Int, action: Runnable) = ButtonRow(key, getString(title), action)

    private fun root(): DialogPage = DialogPage.of(
        getString(R.string.material_settings),
        listOf(
            EntryRow(1, getString(R.string.material_set_account), 1, ::accountPage),
            button(2, R.string.material_set_sections) { startActivity(Intent(this, MaterialSectionsEditorActivity::class.java)) },
            button(3, R.string.material_set_buttons) { startActivity(Intent(this, MaterialButtonsEditorActivity::class.java)) },
            button(4, R.string.material_set_sponsor) { SponsorBlockSettingsPresenter.instance(this).show() },
            button(5, R.string.material_set_quality, ::showQualityDialog),
            button(6, R.string.material_set_language) { LanguageSettingsPresenter.instance(this).show() },
            EntryRow(7, getString(R.string.material_set_blocked), 1, ::blockedPage),
            EntryRow(10, getString(R.string.material_set_updates), 1) { updatesPage() },
            EntryRow(8, getString(R.string.material_set_advanced), 1, ::advancedPage),
            EntryRow(9, getString(R.string.material_set_about), 1, ::aboutPage),
        ),
    )

    // Account

    private fun accountPage(justSignedOut: Boolean = false): DialogPage {
        val service = YouTubeServiceManager.instance().signInService
        val account = service.selectedAccount
        val rows = mutableListOf<DialogRow>()
        if (account != null) {
            rows += TextRow(1, getString(R.string.material_account_signed_in, account.name ?: account.email.orEmpty()))
            rows += button(2, R.string.material_account_sign_out) { confirmSignOut() }
        } else {
            rows += TextRow(1, getString(if (justSignedOut) R.string.material_account_signed_out else R.string.material_account_none))
            rows += button(2, R.string.material_account_sign_in) {
                accountPending = true
                YTSignInPresenter.instance(this).start()
            }
        }
        // Signing out only forgets the account on this device: Google still holds the access until the device is removed.
        rows += TextRow(3, getString(R.string.material_account_revoke))
        rows += TextRow(4, getString(R.string.material_account_token_stays))
        return DialogPage.of(getString(R.string.material_set_account), rows)
    }

    private fun confirmSignOut() {
        AppDialogUtil.showConfirmationDialog(this, getString(R.string.material_account_sign_out_ask)) {
            YouTubeServiceManager.instance().signInService.selectedAccount?.let {
                AccountSettingsPresenter.instance(this).removeAccount(it)
            }
            state.replace(accountPage(justSignedOut = true))
        }
    }

    // Quality

    private fun showQualityDialog() {
        val category = AppDialogUtil.createVideoPresetsCategory(this)
        val dialog = AppDialogPresenter.instance(this)
        dialog.appendRadioCategory(category.title, category.options)
        dialog.showDialog(category.title)
    }

    // Blocked channels

    private fun blockedPage(unblocked: String? = null): DialogPage {
        val channels = BlockedChannelData.instance(this).channelIdsWithNames
        val rows = mutableListOf<DialogRow>()
        rows += TextRow(0, when {
            unblocked != null -> getString(R.string.material_blocked_unblocked, unblocked)
            channels.isEmpty() -> getString(R.string.material_blocked_none)
            else -> getString(R.string.material_blocked_hint)
        })
        channels.forEachIndexed { index, channel ->
            val name = channel.second ?: channel.first.orEmpty()
            rows += ButtonRow(index + 1, name, Runnable { unblock(channel.first, channel.second, name) })
        }
        return DialogPage.of(getString(R.string.material_set_blocked), rows)
    }

    private fun unblock(id: String?, name: String?, label: String) {
        val data = BlockedChannelData.instance(this)
        data.removeChannel(id, name)
        // The list is stored a few seconds after a change; a kill before that would bring the channel back.
        data.persistNow()
        state.replace(blockedPage(unblocked = label))
    }

    // Advanced

    private fun advancedPage(): DialogPage = DialogPage.of(
        getString(R.string.material_set_advanced),
        listOf(
            button(1, R.string.material_adv_player) { PlayerSettingsPresenter.instance(this).show() },
            button(2, R.string.material_adv_subtitles) { SubtitleSettingsPresenter.instance(this).show() },
            button(3, R.string.material_adv_dearrow) { DeArrowSettingsPresenter.instance(this).show() },
            button(4, R.string.material_adv_afr) { AutoFrameRateSettingsPresenter.instance(this).show() },
            learnedRow(),
            learnedResetRow(),
        ),
    )

    // Learned skip: whether auto-skip follows what the owner undoes on each channel, and a way to forget it.

    private fun learnedRow(): DialogRow {
        val store = LearnedSkipStore(this)
        val label = getString(if (store.enabled) R.string.material_learn_state_on else R.string.material_learn_state_off)
        return ButtonRow(5, getString(R.string.material_learn_row, label), Runnable {
            store.enabled = !store.enabled
            state.replace(advancedPage())
        })
    }

    private fun learnedResetRow(): DialogRow {
        val store = LearnedSkipStore(this)
        return ButtonRow(6, getString(R.string.material_learn_reset, store.load().channelCount()), Runnable {
            store.reset()
            state.replace(advancedPage())
        })
    }

    // Updates

    private fun updatesPage(status: String? = null): DialogPage {
        val channel = UpdateStore(this).channel
        fun choice(key: Int, value: UpdateChannel, title: Int, description: Int) =
            ChoiceRow(key, getString(title), getString(description), channel == value, false, value)
        val rows = mutableListOf<DialogRow>(
            choice(1, UpdateChannel.OFF, R.string.material_upd_off, R.string.material_upd_off_desc),
            choice(2, UpdateChannel.STABLE, R.string.material_upd_stable, R.string.material_upd_stable_desc),
            choice(3, UpdateChannel.PRERELEASE, R.string.material_upd_prerelease, R.string.material_upd_prerelease_desc),
            button(4, R.string.material_upd_check_now, ::checkNow),
            TextRow(5, getString(R.string.material_upd_installed, UpdateService.installedVersionName(this))),
        )
        if (status != null) rows += TextRow(6, status)
        return DialogPage.of(getString(R.string.material_set_updates), rows)
    }

    private fun checkNow() {
        val store = UpdateStore(this)
        val channel = store.channel
        if (channel == UpdateChannel.OFF) {
            state.replace(updatesPage(getString(R.string.material_upd_is_off)))
            return
        }
        store.lastCheckMs = System.currentTimeMillis()
        state.replace(updatesPage(getString(R.string.material_upd_checking)))
        val shownPage = state.page?.id
        UpdateService.check(this, channel) { result ->
            if (isDestroyed || state.page?.id != shownPage) return@check
            when (result) {
                is CheckResult.Offer -> {
                    state.replace(updatesPage())
                    // The channel may have changed while the check ran.
                    if (offerAllowed(result.offer, UpdateStore(this).channel)) state.push(updates.offerPage(result.offer))
                }
                CheckResult.UpToDate -> state.replace(updatesPage(getString(R.string.material_upd_latest)))
                is CheckResult.Failed -> state.replace(updatesPage(getString(when (result.reason) {
                    Failure.NETWORK -> R.string.material_upd_net
                    Failure.RATE_LIMITED -> R.string.material_upd_rate
                    Failure.BAD_FEED -> R.string.material_upd_bad_feed
                })))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updates.release()
    }

    // About

    private fun aboutPage(): DialogPage {
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val notice = resources.openRawResource(R.raw.smarttube_license).bufferedReader().use { paragraphs(it.readText()) }
        val rows = mutableListOf<DialogRow>(
            TextRow(0, getString(R.string.app_name)),
            TextRow(1, getString(R.string.material_about_version, version)),
            TextRow(2, getString(R.string.material_about_based_on)),
        )
        notice.forEachIndexed { index, paragraph -> rows += TextRow(3 + index, paragraph) }
        rows += TextRow(3 + notice.size, getString(R.string.material_about_qr))
        return DialogPage.of(getString(R.string.material_set_about), rows)
    }
}
