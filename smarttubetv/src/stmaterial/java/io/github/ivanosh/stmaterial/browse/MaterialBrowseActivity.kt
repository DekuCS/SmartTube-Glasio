package io.github.ivanosh.stmaterial.browse

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.ComposeView
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity
import io.github.ivanosh.stmaterial.data.SectionStore
import io.github.ivanosh.stmaterial.logic.EditorState
import io.github.ivanosh.stmaterial.logic.Sections
import io.github.ivanosh.stmaterial.logic.resolveStart
import com.liskovsoft.smartyoutubetv2.tv.R

/**
 * The tabbed home screen. Sections, loading, sign-in and the player stay in the SmartTube presenters;
 * this only implements [BrowseView] and draws its state.
 *
 * The presenter lifecycle follows NovaBrowseActivity in margantcovka/SmartTube-New (MIT).
 */
class MaterialBrowseActivity : LeanbackActivity(), BrowseView {
    private val state = BrowseState()
    private val handler = Handler(Looper.getMainLooper())
    private val loadSelected = Runnable { load() }
    private lateinit var presenter: BrowsePresenter
    private lateinit var store: SectionStore
    private var initializing = false

    private val actions = object : BrowseActions {
        override fun onTabFocused(id: Int) {
            if (id != MediaGroup.TYPE_SETTINGS && id != state.selected) select(id, immediately = false)
        }

        override fun onTabClicked(id: Int) {
            when (id) {
                Sections.SEARCH_SECTION_ID -> SearchPresenter.instance(this@MaterialBrowseActivity).startSearch(null)
                MediaGroup.TYPE_SETTINGS -> if (id != state.selected) select(id, immediately = true)
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
        override fun onVideoLongClicked(video: Video) = presenter.onVideoItemLongClicked(video)
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
        presenter.onViewDestroyed()
    }

    /** The selection shows at once. The load waits, so holding the key does not load every tab it passes. */
    private fun select(id: Int, immediately: Boolean) {
        handler.removeCallbacks(loadSelected)
        if (id != state.selected) state.selectTab(id)
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

    override fun updateSection(group: VideoGroup) = state.updateSection(group)

    override fun updateSection(group: SettingsGroup) = state.updateSettings(group.category, group.items.orEmpty())

    override fun clearSection(section: BrowseSection) {
        if (section.id == state.selected) state.clearContent()
    }

    override fun selectSectionItem(index: Int) = Unit

    override fun selectSectionItem(item: Video?) = Unit

    /** A late error of the section being left must not land on the one being selected. */
    override fun showError(data: ErrorFragmentData) {
        if (isLoadPending) return
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
        if (!isLoadPending) state.loading = show
    }

    override fun isProgressBarShowing(): Boolean = state.loading

    /** The presenter calls this from its background refresh, which must not take focus from the tabs. */
    override fun focusOnContent() = Unit

    override fun isEmpty(): Boolean = state.isEmpty

    override fun updateBadge() = Unit

    private companion object {
        const val LOAD_DEBOUNCE_MS = 250L
    }
}
