package io.github.ivanosh.stmaterial.data

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import io.github.ivanosh.stmaterial.logic.Sections
import io.github.ivanosh.stmaterial.logic.resolveStart

/** Mirrors the tab list to the sidebar. Search is never passed to it. */
class SectionMirror(private val sidebar: SidebarPort) {
    fun mirror(shown: List<Int>, savedStart: Int) {
        val real = shown.filter { it != Sections.SEARCH_SECTION_ID }
        for (id in Sections.KNOWN) {
            if (id != Sections.SEARCH_SECTION_ID) sidebar.enable(id, id in real)
        }
        sidebar.enable(MediaGroup.TYPE_SHORTS, false)
        order(real)
        // The presenter cannot open Search, so a Search start boots the first real tab.
        // A Search-only list has no real tab: enable Home in the sidebar only, as the boot section.
        val start = resolveStart(savedStart, shown)
        val boot = if (start != Sections.SEARCH_SECTION_ID) start else real.firstOrNull() ?: MediaGroup.TYPE_HOME
        if (boot == MediaGroup.TYPE_HOME && boot !in real) sidebar.enable(boot, true)
        sidebar.setBoot(boot)
    }

    /**
     * Places ids from last to first: each is moved up until it is above its successor, so the
     * sorted tail never shifts. Other items (Settings, pinned ones) may sit between; they are skipped.
     */
    private fun order(ids: List<Int>) {
        for (i in ids.size - 2 downTo 0) {
            var guard = MAX_STEPS
            while (sidebar.index(ids[i]) > sidebar.index(ids[i + 1]) && sidebar.canMoveUp(ids[i]) && guard-- > 0) {
                sidebar.moveUp(ids[i])
            }
        }
    }

    private companion object {
        const val MAX_STEPS = 1000
    }
}
