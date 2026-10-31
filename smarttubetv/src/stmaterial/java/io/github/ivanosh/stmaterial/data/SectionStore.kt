package io.github.ivanosh.stmaterial.data

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService
import io.github.ivanosh.stmaterial.logic.EditorState
import io.github.ivanosh.stmaterial.logic.Sections
import io.github.ivanosh.stmaterial.logic.resolveStart
import io.github.ivanosh.stmaterial.logic.resolveTabs

/**
 * Persists tab order, start tab and the Search position in its own prefs, and mirrors real sections
 * to [SidebarService], which BrowsePresenter builds its section list from. Search is never mirrored.
 */
class SectionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val sidebar: SidebarService = SidebarService.instance(context)

    init {
        if (!prefs.contains(KEY_ORDER)) {
            save(defaults())
        }
    }

    fun load(): EditorState<Int> {
        val shown = resolveTabs(parseIds(prefs.getString(KEY_ORDER, null)), Sections.KNOWN)
        val hidden = (Sections.DEFAULT_SHOWN + Sections.HIDDEN_ADDABLE).filter { it !in shown }
        return EditorState(shown, hidden, emptySet(), Sections.LIMIT)
    }

    fun save(state: EditorState<Int>) {
        val shown = resolveTabs(state.shown, Sections.KNOWN)
        prefs.edit().putString(KEY_ORDER, shown.joinToString(",")).apply()
        mirror(shown)
    }

    fun startSection(): Int =
        resolveStart(prefs.getInt(KEY_START, Sections.START_DEFAULT), load().shown)

    fun setStart(id: Int) {
        prefs.edit().putInt(KEY_START, id).apply()
        mirror(load().shown)
    }

    private fun defaults() =
        EditorState(
            Sections.DEFAULT_SHOWN, Sections.HIDDEN_ADDABLE, emptySet(), Sections.LIMIT
        )

    private fun mirror(shown: List<Int>) {
        val real = shown.filter { it != Sections.SEARCH_SECTION_ID }
        for (id in Sections.KNOWN) {
            if (id != Sections.SEARCH_SECTION_ID) sidebar.enableSection(id, id in real)
        }
        sidebar.enableSection(MediaGroup.TYPE_SHORTS, false)
        order(real)
        // The presenter cannot open Search, so a Search start boots the first real section.
        val start = resolveStart(prefs.getInt(KEY_START, Sections.START_DEFAULT), shown)
        val boot = if (start == Sections.SEARCH_SECTION_ID) real.firstOrNull() else start
        if (boot != null) sidebar.setBootSectionId(boot)
    }

    /** SidebarService has no bulk ordering: bubble each section above its predecessor until sorted. */
    private fun order(ids: List<Int>) {
        var moved = true
        while (moved) {
            moved = false
            for (i in 1 until ids.size) {
                if (sidebar.getSectionIndex(ids[i]) < sidebar.getSectionIndex(ids[i - 1])) {
                    sidebar.moveSectionUp(ids[i - 1])
                    moved = true
                }
            }
        }
    }

    private fun parseIds(raw: String?): List<Int> =
        raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

    private companion object {
        const val PREFS_NAME = "stmaterial_sections"
        const val KEY_ORDER = "order"
        const val KEY_START = "start"
    }
}
