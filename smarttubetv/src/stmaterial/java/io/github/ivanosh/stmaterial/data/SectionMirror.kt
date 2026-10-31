package io.github.ivanosh.stmaterial.data

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import io.github.ivanosh.stmaterial.logic.Sections
import io.github.ivanosh.stmaterial.logic.resolveStart

/**
 * Mirrors the tab list to the sidebar. Search is never passed to it. Idempotent: it reads the sidebar
 * first and writes only what differs, so running it on a sidebar that is already right costs reads only.
 */
class SectionMirror(private val sidebar: SidebarPort) {
    /** Returns true if the sidebar had to be changed. */
    fun mirror(shown: List<Int>, savedStart: Int): Boolean {
        var changed = false
        val real = shown.filter { it != Sections.SEARCH_SECTION_ID }
        // The presenter cannot open Search, so a Search start boots the first real tab.
        // A Search-only list has no real tab: Home is enabled in the sidebar only, as the boot section.
        val start = resolveStart(savedStart, shown)
        val boot = if (start != Sections.SEARCH_SECTION_ID) start else real.firstOrNull() ?: MediaGroup.TYPE_HOME
        val wanted = if (boot in real) real else real + boot
        for (id in Sections.KNOWN) {
            if (id == Sections.SEARCH_SECTION_ID) continue
            val on = id in wanted
            if ((sidebar.index(id) >= 0) != on) {
                sidebar.enable(id, on)
                changed = true
            }
        }
        if (sidebar.index(MediaGroup.TYPE_SHORTS) >= 0) {
            sidebar.enable(MediaGroup.TYPE_SHORTS, false)
            changed = true
        }
        if (order(real)) changed = true
        if (sidebar.boot() != boot) {
            sidebar.setBoot(boot)
            changed = true
        }
        return changed
    }

    /**
     * Places ids from last to first: each is moved up until it is above its successor, so the
     * sorted tail never shifts. Other items (Settings, pinned ones) may sit between; they are skipped.
     */
    private fun order(ids: List<Int>): Boolean {
        var moved = false
        for (i in ids.size - 2 downTo 0) {
            var guard = MAX_STEPS
            while (sidebar.index(ids[i]) > sidebar.index(ids[i + 1]) && sidebar.canMoveUp(ids[i]) && guard-- > 0) {
                sidebar.moveUp(ids[i])
                moved = true
            }
        }
        return moved
    }

    private companion object {
        const val MAX_STEPS = 1000
    }
}
