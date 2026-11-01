package io.github.ivanosh.stmaterial.dialog

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.liskovsoft.sharedutils.helpers.KeyHelpers
import com.liskovsoft.sharedutils.helpers.MessageHelpers
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionCategory
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.OptionItem
import com.liskovsoft.smartyoutubetv2.common.app.presenters.AppDialogPresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager
import com.liskovsoft.smartyoutubetv2.common.misc.GlobalKeyTranslator
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity
import com.liskovsoft.smartyoutubetv2.common.misc.PlayerKeyTranslator
import com.liskovsoft.smartyoutubetv2.common.utils.Utils
import com.liskovsoft.smartyoutubetv2.tv.R
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity
import io.github.ivanosh.stmaterial.ui.MaterialTvTheme

/**
 * The one window of every [AppDialogPresenter] dialog, in place of the upstream preference fragments.
 * It keeps the contract of upstream AppDialogActivity and AppDialogFragment: pages stack, Back goes to the
 * previous page and closes the last one, the presenter is told when the view is ready and when it is gone,
 * and a choice runs the item's own callback, which decides whether the dialog stays.
 */
class MaterialDialogActivity : MotherActivity(), AppDialogView {
    private val state = DialogState()
    private val confirmKeyGuard = ConfirmKeyGuard()
    private lateinit var presenter: AppDialogPresenter
    private lateinit var keyTranslator: GlobalKeyTranslator
    private var paused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        keyTranslator = PlayerKeyTranslator(this).also { it.apply() }
        setContentView(ComposeView(this).apply {
            setContent { MaterialTvTheme { DialogScreen(state, ::activate) } }
        })
        presenter = AppDialogPresenter.instance(this)
        presenter.setView(this)
        try {
            presenter.onViewInitialized()
        } catch (e: IllegalStateException) {
            // The presenter was torn down meanwhile, as upstream ignores it.
        }
    }

    /** The translucent theme of the manifest stays: the colour scheme themes are opaque. */
    override fun initTheme() = Unit

    override fun onPause() {
        super.onPause()
        paused = true
    }

    override fun onResume() {
        super.onResume()
        paused = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (presenter.view === this) presenter.onViewDestroyed()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // null: the translator has run the key's own action
        val translated = keyTranslator.translate(event) ?: return true
        if (KeyHelpers.isConfirmKey(translated.keyCode) &&
            confirmKeyGuard.shouldDrop(translated.action == KeyEvent.ACTION_DOWN, translated.repeatCount)
        ) {
            return true
        }
        return handleNavigation(translated) || super.dispatchKeyEvent(translated)
    }

    private fun handleNavigation(event: KeyEvent?): Boolean {
        if (event == null) return false
        val code = event.keyCode
        // Toggle dialog
        if (!state.isOverlay && (KeyHelpers.isLeftRightKey(code) || KeyHelpers.isMenuKey(code))) {
            if (event.action == KeyEvent.ACTION_DOWN) finish()
            return true
        }
        // Notification dialog type: any movement closes it and goes on to the player.
        if (state.isOverlay && (KeyHelpers.isNavigationKey(code) || KeyHelpers.isMenuKey(code))) {
            if (event.action == KeyEvent.ACTION_DOWN) finish()
            val view = PlaybackPresenter.instance(this).view
            ((view as? Fragment)?.activity)?.dispatchKeyEvent(event)
            return true
        }
        return false
    }

    override fun finish() {
        // Back on a nested page returns to the previous one; anything else closes the dialog.
        if (isBackPressed() && canGoBack()) {
            goBack()
            resetBackState()
            return
        }
        if (::presenter.isInitialized) presenter.onFinish()
        finishReally()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Respect PIP mode
        if (ViewManager.instance(this).topView == PlaybackView::class.java) {
            (PlaybackPresenter.instance(this).context as? PlaybackActivity)?.onUserLeaveHint()
        }
        finish()
    }

    // AppDialogView

    override fun show(
        categories: List<OptionCategory>?,
        title: CharSequence?,
        isExpandable: Boolean,
        isTransparent: Boolean,
        isOverlay: Boolean,
        id: Int,
    ) {
        if (!Utils.checkActivity(this)) return
        // Only the root page makes the pages above it transparent.
        if (state.depth <= 1) state.isTransparent = isTransparent
        state.isOverlay = isOverlay
        state.viewId = id

        val single = categories?.singleOrNull()
        if (isExpandable && !single?.options.isNullOrEmpty()) {
            showOnly(single!!)
        } else {
            state.push(DialogPage.of(title?.toString(), categories.orEmpty().filter { !it.options.isNullOrEmpty() }.mapIndexed(::rowOf)))
        }
    }

    /** One category shown as the whole dialog. */
    private fun showOnly(category: OptionCategory) {
        when (category.type) {
            // As upstream, a single button runs at once; the dialog is for its callback to continue.
            OptionCategory.TYPE_SINGLE_BUTTON -> category.options.firstOrNull()?.onSelect(true)
            OptionCategory.TYPE_SINGLE_SWITCH -> state.push(DialogPage.of(null, listOf(rowOf(0, category))))
            else -> state.push(pageOf(category))
        }
    }

    override fun goBack() {
        if (!state.pop()) finish()
    }

    override fun clearBackstack() = state.clear()

    override fun canGoBack() = state.canGoBack

    override fun isShown() = !isDestroyed && !paused

    override fun isTransparent() = state.isTransparent

    override fun isOverlay() = state.isOverlay

    override fun isPaused() = paused

    override fun getViewId() = state.viewId

    // Rows <-> options

    /** A category of the root page: an inline switch or button, or a line that opens the category. */
    private fun rowOf(index: Int, category: OptionCategory): DialogRow {
        val item = category.options.first()
        return when (category.type) {
            OptionCategory.TYPE_SINGLE_SWITCH -> SwitchRow(index, item.title.text(), item.isSelected, item)
            OptionCategory.TYPE_SINGLE_BUTTON -> ButtonRow(index, item.title.text(), item)
            else -> EntryRow(index, category.title.text(), category.options.size) { pageOf(category) }
        }
    }

    private fun pageOf(category: OptionCategory): DialogPage {
        val options = category.options
        val rows: List<DialogRow> = when (category.type) {
            OptionCategory.TYPE_RADIO_LIST -> {
                // Only one line may be selected, however many items claim it: upstream shows the last.
                val selected = options.indexOfLast { it.isSelected }
                options.mapIndexed { i, item -> choice(i, item, selected == i, multi = false) }
            }
            OptionCategory.TYPE_CHECKBOX_LIST -> options.mapIndexed { i, item -> choice(i, item, item.isSelected, multi = true) }
            OptionCategory.TYPE_STRING_LIST -> options.mapIndexed { i, item -> ButtonRow(i, item.title.text(), item) }
            // A long text is read paragraph by paragraph, each line being a stop for the remote.
            OptionCategory.TYPE_LONG_TEXT -> options.firstOrNull()?.title.text().lines().filter { it.isNotBlank() }.mapIndexed(::TextRow)
            // Chat and comments are not part of this app.
            else -> listOf(TextRow(0, category.title.text()))
        }
        return DialogPage.of(category.title?.toString(), rows)
    }

    private fun choice(key: Int, item: OptionItem, selected: Boolean, multi: Boolean) =
        ChoiceRow(key, item.title.text(), item.description?.toString(), selected, multi, item)

    private fun CharSequence?.text() = this?.toString().orEmpty()

    private fun itemOf(row: DialogRow): OptionItem? = when (row) {
        is ChoiceRow -> row.ref
        is SwitchRow -> row.ref
        is ButtonRow -> row.ref
        else -> null
    } as? OptionItem

    private fun activate(row: DialogRow) {
        val item = itemOf(row)
        when (row) {
            is EntryRow -> state.open(row)
            is ButtonRow -> item?.onSelect(true)
            is TextRow -> Unit
            is SwitchRow -> change(row, item, !row.selected)
            is ChoiceRow -> if (row.multi) change(row, item, !row.selected) else {
                state.choose(row.key)
                item?.onSelect(true)
            }
        }
    }

    /** A checkbox or a switch: the item's own rules first (what it requires, what it excludes), then its callback. */
    private fun change(row: DialogRow, item: OptionItem?, selected: Boolean) {
        if (item == null) return
        val page = state.page?.id
        state.toggle(row.key)
        if (selected) {
            item.required?.filter { !it.isSelected }?.forEach {
                MessageHelpers.showMessage(this, getString(R.string.require_checked, it.title))
            }
            item.radio?.forEach { it.onSelect(false) }
        }
        item.onSelect(selected)
        // The callback may have opened another page; the lines on screen are then not these.
        if (state.page?.id == page) state.sync { itemOf(it)?.isSelected ?: false }
    }
}
