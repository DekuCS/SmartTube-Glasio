package io.github.ivanosh.stmaterial.logic

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionsTest {
    private val known = Sections.KNOWN

    @Test
    fun defaultsAreInSpecifiedOrder() {
        assertEquals(
            listOf(
                MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HOME, MediaGroup.TYPE_HISTORY,
                MediaGroup.TYPE_USER_PLAYLISTS, Sections.SEARCH_SECTION_ID
            ),
            Sections.DEFAULT_SHOWN
        )
        assertEquals(8, Sections.LIMIT)
    }

    @Test
    fun shortsAndSettingsAreNotKnown() {
        assertFalse(MediaGroup.TYPE_SHORTS in known)
        assertFalse(MediaGroup.TYPE_SETTINGS in known)
        assertTrue(Sections.SEARCH_SECTION_ID in known)
        assertEquals(known.size, Sections.DEFAULT_SHOWN.size + Sections.HIDDEN_ADDABLE.size)
    }

    @Test
    fun resolveTabsSkipsUnknownKeepsOrder() {
        val saved = listOf(MediaGroup.TYPE_HISTORY, 4242, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_HOME)
        assertEquals(
            listOf(MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_HOME),
            resolveTabs(saved, known)
        )
    }

    @Test
    fun resolveTabsDropsSettingsShortsAndDuplicates() {
        val saved = listOf(
            MediaGroup.TYPE_SETTINGS, MediaGroup.TYPE_HOME, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_HOME
        )
        assertEquals(listOf(MediaGroup.TYPE_HOME), resolveTabs(saved, known))
    }

    @Test
    fun resolveTabsKeepsSearchPosition() {
        val saved = listOf(MediaGroup.TYPE_HOME, Sections.SEARCH_SECTION_ID, MediaGroup.TYPE_HISTORY)
        assertEquals(saved, resolveTabs(saved, known))
    }

    @Test
    fun resolveTabsFallsBackToDefaultsWhenEmpty() {
        assertEquals(Sections.DEFAULT_SHOWN, resolveTabs(emptyList(), known))
        assertEquals(Sections.DEFAULT_SHOWN, resolveTabs(listOf(MediaGroup.TYPE_SETTINGS, 999), known))
    }

    @Test
    fun resolveStartKeepsShownStart() {
        val tabs = listOf(MediaGroup.TYPE_HOME, MediaGroup.TYPE_HISTORY)
        assertEquals(MediaGroup.TYPE_HISTORY, resolveStart(MediaGroup.TYPE_HISTORY, tabs))
    }

    @Test
    fun resolveStartReplacesHiddenOrUnknownWithFirst() {
        val tabs = listOf(MediaGroup.TYPE_HOME, MediaGroup.TYPE_HISTORY)
        assertEquals(MediaGroup.TYPE_HOME, resolveStart(MediaGroup.TYPE_MUSIC, tabs))
        assertEquals(MediaGroup.TYPE_HOME, resolveStart(31337, tabs))
    }

    @Test
    fun resolveStartNeverReturnsSettings() {
        val tabs = listOf(MediaGroup.TYPE_SETTINGS, MediaGroup.TYPE_HISTORY)
        assertEquals(MediaGroup.TYPE_HISTORY, resolveStart(MediaGroup.TYPE_SETTINGS, tabs))
    }

    @Test
    fun resolveStartAllowsSearch() {
        val tabs = listOf(MediaGroup.TYPE_HOME, Sections.SEARCH_SECTION_ID)
        assertEquals(Sections.SEARCH_SECTION_ID, resolveStart(Sections.SEARCH_SECTION_ID, tabs))
    }
}
