package io.github.ivanosh.stmaterial.logic

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup

object Sections {
    /** Not an upstream section: Search is a tab here, but never reaches SidebarService or BrowsePresenter. */
    const val SEARCH_SECTION_ID = -1000

    const val LIMIT = 8

    val DEFAULT_SHOWN: List<Int> = listOf(
        MediaGroup.TYPE_SUBSCRIPTIONS,
        MediaGroup.TYPE_HOME,
        MediaGroup.TYPE_HISTORY,
        MediaGroup.TYPE_USER_PLAYLISTS,
        SEARCH_SECTION_ID
    )

    /** Shorts is excluded from v1; Settings is an icon outside the tab count. */
    val HIDDEN_ADDABLE: List<Int> = listOf(
        MediaGroup.TYPE_TRENDING,
        MediaGroup.TYPE_LIVE,
        MediaGroup.TYPE_MY_VIDEOS,
        MediaGroup.TYPE_GAMING,
        MediaGroup.TYPE_NEWS,
        MediaGroup.TYPE_MUSIC,
        MediaGroup.TYPE_SPORTS,
        MediaGroup.TYPE_CHANNEL_UPLOADS,
        MediaGroup.TYPE_PLAYBACK_QUEUE,
        MediaGroup.TYPE_NOTIFICATIONS,
        MediaGroup.TYPE_BLOCKED_CHANNELS,
        MediaGroup.TYPE_KIDS_HOME
    )

    val KNOWN: Set<Int> = (DEFAULT_SHOWN + HIDDEN_ADDABLE).toSet()

    val START_DEFAULT: Int = MediaGroup.TYPE_SUBSCRIPTIONS
}

/** Keeps known ids in saved order, without duplicates or Settings; empty result falls back to defaults. */
fun resolveTabs(saved: List<Int>, known: Set<Int>): List<Int> {
    val tabs = saved.filter { it in known && it != MediaGroup.TYPE_SETTINGS }.distinct()
    return tabs.ifEmpty { Sections.DEFAULT_SHOWN }
}

/** The saved start if it is a tab, otherwise the first tab. Never Settings. */
fun resolveStart(saved: Int, tabs: List<Int>): Int {
    val candidates = tabs.filter { it != MediaGroup.TYPE_SETTINGS }
    return if (saved in candidates) saved else candidates.firstOrNull() ?: Sections.START_DEFAULT
}
