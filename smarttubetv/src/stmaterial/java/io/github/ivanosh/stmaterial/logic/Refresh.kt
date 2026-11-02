package io.github.ivanosh.stmaterial.logic

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup

/** What OK on a tab does. */
enum class TabClick { RELOAD, OPEN_SEARCH, OPEN_SETTINGS, NONE }

/** OK on the tab that is already selected reloads it; Search and Settings keep their own action. */
fun tabClick(selected: Int?, clicked: Int): TabClick = when {
    clicked == Sections.SEARCH_SECTION_ID -> TabClick.OPEN_SEARCH
    clicked == MediaGroup.TYPE_SETTINGS -> TabClick.OPEN_SETTINGS
    clicked == selected -> TabClick.RELOAD
    else -> TabClick.NONE
}

/** How old the shown tab may be when the screen comes back before it reloads on its own. */
const val QUIET_RELOAD_AGE_MS = 15 * 60 * 1000L

/**
 * Whether the screen coming back reloads the selected tab quietly. [loadedAtMs] is when its content
 * arrived, null when it has none. A tab that is [busy] loading is left alone.
 */
fun shouldQuietReload(
    selected: Int?,
    loadedAtMs: Long?,
    nowMs: Long,
    busy: Boolean,
    thresholdMs: Long = QUIET_RELOAD_AGE_MS,
): Boolean =
    !busy && loadedAtMs != null && selected != null &&
        selected != Sections.SEARCH_SECTION_ID && selected != MediaGroup.TYPE_SETTINGS &&
        nowMs - loadedAtMs > thresholdMs
