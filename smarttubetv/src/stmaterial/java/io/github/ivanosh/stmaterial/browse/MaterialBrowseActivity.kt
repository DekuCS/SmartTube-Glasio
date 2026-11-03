package io.github.ivanosh.stmaterial.browse

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.ComposeView
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import io.github.ivanosh.stmaterial.ui.MenuItem
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.SignInError
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager
import io.github.ivanosh.stmaterial.data.HistorySource
import io.github.ivanosh.stmaterial.data.SectionStore
import io.github.ivanosh.stmaterial.logic.EditorState
import io.github.ivanosh.stmaterial.logic.QUIET_RELOAD_AGE_MS
import io.github.ivanosh.stmaterial.logic.Sections
import io.github.ivanosh.stmaterial.logic.TabClick
import io.github.ivanosh.stmaterial.logic.shouldQuietReload
import io.github.ivanosh.stmaterial.logic.tabClick
import io.github.ivanosh.stmaterial.logic.SignedOutNotice
import io.github.ivanosh.stmaterial.logic.signedOutNotice
import io.github.ivanosh.stmaterial.logic.resolveStart
import io.github.ivanosh.stmaterial.settings.MaterialSettingsActivity
import io.github.ivanosh.stmaterial.update.UpdateScheduler
import com.liskovsoft.smartyoutubetv2.tv.R

/**
 * The tabbed home screen. Sections, loading, sign-in and the player stay in the SmartTube presenters;
 * this only implements [BrowseView] and draws its state.
 *
 * The presenter lifecycle follows NovaBrowseActivity in margantcovka/SmartTube-New (MIT).
 */
class MaterialBrowseActivity : LeanbackActivity(), BrowseView {
    private val state = BrowseState(onSubscriptionsLoad = ::loadHistory, clock = deviceClock(), rowTitle = { getString(it) })
    private val history = HistorySource { YouTubeServiceManager.instance().contentService.historyObserve }
    private val handler = Handler(Looper.getMainLooper())
    private val loadSelected = Runnable { load() }
    private val endReload = Runnable { state.endReload() }
    private lateinit var presenter: BrowsePresenter
    private lateinit var store: SectionStore
    private var initializing = false

    private val cardMenu = CardMenu(this, onGone = { state.updateSection(it) }, onMessage = { state.message = it }, onReload = { presenter.refresh(false) })

    private val actions = object : BrowseActions {
        override fun onTabFocused(id: Int) {
            if (id != MediaGroup.TYPE_SETTINGS && id != state.selected) select(id, immediately = false)
        }

        override fun onTabClicked(id: Int) {
            when (tabClick(state.selected, id)) {
                TabClick.OPEN_SEARCH -> SearchPresenter.instance(this@MaterialBrowseActivity).startSearch(null)
                TabClick.OPEN_SETTINGS -> startActivity(Intent(this@MaterialBrowseActivity, MaterialSettingsActivity::class.java))
                TabClick.RELOAD -> reloadSelected()
                TabClick.NONE -> Unit
            }
        }

        override fun onOpenFirst(id: Int) {
            state.start = id
            saveAsync(state.shown, id)
        }

        override fun onHide(id: Int) {
            val shown = state.shown - id
            if (shown.isEmpty()) return
            state.shown = shown
            val start = resolveStart(state.start, shown)
            if (id == state.start) {
                state.start = start
                state.message = getString(R.string.material_start_moved, titleOf(start))
            }
            saveAsync(shown, start)
            if (id == state.selected) state.tabs().firstOrNull()?.let { select(it, immediately = true) }
        }

        override fun onCustomize() {
            startActivity(Intent(this@MaterialBrowseActivity, MaterialSectionsEditorActivity::class.java))
        }

        override fun onErrorAction(error: ErrorData) {
            val action = error.action
            if (action != null) {
                action()
            } else {
                // CategoryEmptyError has no button of its own, and its onAction is sign-in.
                state.clearContent()
                state.loading = true
                presenter.refresh(false)
            }
        }

        override fun onVideoFocused(video: Video) = presenter.onVideoItemSelected(video)
        override fun onVideoClicked(video: Video) = presenter.onVideoItemClicked(video)
        // The menu is the screen's own: BrowsePresenter.onVideoItemLongClicked opens the old dialog.
        override fun onVideoLongClicked(video: Video) = Unit
        override fun videoMenuItems(video: Video): List<MenuItem> = cardMenu.items(video, state.selected ?: MediaGroup.TYPE_UNDEFINED)
        override fun onNeedMore(row: VideoRow) {
            state.takeMoreRequest(row)?.let(presenter::onScrollEnd)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // First, because on the first run it writes the section list the presenter reads.
        store = SectionStore(this)
        state.shown = store.load().shown
        val startSection = store.startSection()
        state.start = startSection

        presenter = BrowsePresenter.instance(this)
        presenter.setView(this)
        // A presenter that outlived the activity knows the section the user was in.
        val current = presenter.currentSection?.id

        initializing = true
        presenter.onViewInitialized()
        initializing = false

        val target = current?.takeIf(state::canSelect)
            ?: startSection.takeIf(state::canSelect)
            ?: state.tabs().firstOrNull()
        target?.let { select(it, immediately = true) }

        // A save cut short leaves the sidebar half done while the prefs, which the tabs follow, are new.
        store.repairAsync { changed -> if (changed && !isDestroyed) runOnUiThread { presenter.updateSections() } }

        setContentView(ComposeView(this).apply { setContent { BrowseScreen(state, actions) } })
        // The screen is opaque, a window background would only be one more fill per frame.
        window.setBackgroundDrawable(null)
    }

    override fun onResume() {
        super.onResume()
        presenter.onViewResumed()
        reloadSections()
        reloadIfStale()
        UpdateScheduler.onBrowseResumed(this)
    }

    /**
     * Coming back to the screen: the tab shown is reloaded without a trace if its content is old. The rows
     * stay until the new ones arrive, focus stays on its card, and a failure only shows a short message.
     * A load the presenter has started itself (its 3 h rule for Home) is not asked for twice.
     */
    private fun reloadIfStale() {
        val busy = state.loading || isLoadPending || presenter.hasPendingActions()
        if (!shouldQuietReload(state.selected, state.loadedAt, state.now(), busy, QUIET_AGE_MS)) return
        if (state.beginQuietReload()) load()
    }

    /** OK on the selected tab: the tab empties, shows its loading state and loads again. Focus stays on the tab. */
    private fun reloadSelected() {
        if (state.selectedSection == null) return
        handler.removeCallbacks(loadSelected)
        history.dispose()
        state.restartTab()
        state.loading = true
        load()
    }

    /** The editor has stored a new list while this screen was stopped. */
    private fun reloadSections() {
        val saved = store.load().shown
        state.start = store.startSection()
        if (saved == state.shown) return
        state.shown = saved
        presenter.updateSections()
    }

    /**
     * Prefs are written at once; mirroring to SidebarService writes its whole state on every change, so it
     * runs on the store's save thread, after every earlier save.
     */
    private fun saveAsync(shown: List<Int>, start: Int) {
        val edited = EditorState(shown, emptyList(), emptySet(), Sections.LIMIT)
        store.saveAsync(edited, start)
    }

    private fun titleOf(id: Int): String =
        if (id == Sections.SEARCH_SECTION_ID) getString(R.string.material_search)
        else state.sections.firstOrNull { it.id == id }?.title.orEmpty()

    override fun onPause() {
        super.onPause()
        presenter.onViewPaused()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(loadSelected)
        handler.removeCallbacks(endReload)
        history.dispose()
        presenter.onViewDestroyed()
    }

    /** The selection shows at once. The load waits, so holding the key does not load every tab it passes. */
    private fun select(id: Int, immediately: Boolean) {
        handler.removeCallbacks(loadSelected)
        if (id != state.selected) {
            history.dispose()
            state.selectTab(id)
        }
        when {
            id == Sections.SEARCH_SECTION_ID -> state.loading = false
            immediately -> load()
            else -> {
                state.loading = true
                handler.postDelayed(loadSelected, LOAD_DEBOUNCE_MS)
            }
        }
    }

    private fun load() {
        val section = state.selectedSection ?: return
        if (section.type == BrowseSection.TYPE_ERROR) {
            (section.data as? ErrorFragmentData)?.let(::showError)
        }
        presenter.onSectionFocused(section.id)
    }

    /**
     * "Continue watching" on Subscriptions: one request for the first page of the history per load of the
     * feed, which the state reports, so a retry or a refresh asks again. Signed out, there is nothing to
     * ask for, and the presenter shows its sign-in prompt. A failure only leaves the row out.
     */
    private fun loadHistory(epoch: Int) {
        history.dispose()
        if (!YouTubeServiceManager.instance().signInService.isSigned) return
        history.load { state.updateHistory(VideoGroup.from(it), epoch) }
    }

    private val isLoadPending: Boolean get() = handler.hasCallbacks(loadSelected)

    // BrowseView

    override fun addSection(index: Int, section: BrowseSection) {
        state.sections.removeAll { it.id == section.id }
        if (index < 0 || index > state.sections.size) state.sections.add(section) else state.sections.add(index, section)
    }

    override fun removeSection(category: BrowseSection) = state.removeSection(category.id)

    override fun removeAllSections() {
        state.sections.clear()
        state.clearContent()
    }

    /**
     * Focus stays on the tabs whatever [focusOnContent] says. During start-up the choice is ours, and
     * the presenter's boot section is not the saved one. The Search tab is not a section it knows about.
     */
    override fun selectSection(index: Int, focusOnContent: Boolean) {
        if (initializing || state.selected == Sections.SEARCH_SECTION_ID) return
        val id = state.sections.getOrNull(index)?.id ?: return
        val target = id.takeIf(state::canSelect)
            ?: state.selected?.takeIf(state::canSelect)
            ?: state.tabs().firstOrNull()
            ?: return
        select(target, immediately = true)
    }

    override fun updateSection(group: VideoGroup) {
        state.updateSection(group)
        // After the rest of the answer, which is handled in the same pass of the main loop.
        if (state.reloading || state.firstPass) {
            handler.removeCallbacks(endReload)
            handler.post(endReload)
        }
    }

    /** The presenter's Settings grid is replaced by the settings screen. */
    override fun updateSection(group: SettingsGroup) = Unit

    override fun clearSection(section: BrowseSection) {
        if (section.id == state.selected) state.clearContent()
    }

    override fun selectSectionItem(index: Int) = Unit

    override fun selectSectionItem(item: Video?) = Unit

    /** A late error of the section being left must not land on the one being selected. */
    override fun showError(data: ErrorFragmentData) {
        if (isLoadPending) return
        if (state.failQuietReload()) {
            state.message = getString(R.string.material_refresh_failed)
            return
        }
        val section = state.selectedSection
        val notice = signedOutNotice(data is SignInError, section?.id, section?.isAuthOnly == true)
        if (notice == SignedOutNotice.PUBLIC || notice == SignedOutNotice.HOME) {
            // Retry has no action of its own: onErrorAction refreshes the section.
            var message = getString(R.string.material_signed_out_empty)
            if (notice == SignedOutNotice.HOME) message += "\n" + getString(R.string.material_signed_out_home)
            state.showError(
                ErrorData(
                    message = message,
                    actionText = getString(R.string.material_retry),
                    action = null,
                    secondaryText = getString(R.string.material_account_sign_in),
                    secondaryAction = data::onAction,
                )
            )
            return
        }
        val actionText = data.actionText
        state.showError(
            ErrorData(
                message = data.message.orEmpty().lineSequence().first(),
                actionText = actionText,
                action = if (actionText != null) data::onAction else null,
            )
        )
    }

    override fun showProgressBar(show: Boolean) {
        if (!isLoadPending && !state.quiet) state.loading = show
    }

    override fun isProgressBarShowing(): Boolean = state.loading

    /** The presenter calls this from its background refresh, which must not take focus from the tabs. */
    override fun focusOnContent() = Unit

    override fun isEmpty(): Boolean = state.isEmpty

    override fun updateBadge() = Unit

    private companion object {
        const val LOAD_DEBOUNCE_MS = 250L
        const val QUIET_AGE_MS = QUIET_RELOAD_AGE_MS
    }
}
