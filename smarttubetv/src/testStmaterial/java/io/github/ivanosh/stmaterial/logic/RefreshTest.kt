package io.github.ivanosh.stmaterial.logic

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshTest {
    private val subs = MediaGroup.TYPE_SUBSCRIPTIONS
    private val home = MediaGroup.TYPE_HOME
    private val min = 60_000L

    @Test
    fun okOnTheSelectedTabReloadsIt() {
        assertEquals(TabClick.RELOAD, tabClick(subs, subs))
    }

    @Test
    fun okOnAnotherTabDoesNothing() {
        assertEquals(TabClick.NONE, tabClick(subs, home))
        assertEquals(TabClick.NONE, tabClick(null, home))
    }

    @Test
    fun searchAndSettingsKeepTheirOwnAction() {
        assertEquals(TabClick.OPEN_SEARCH, tabClick(Sections.SEARCH_SECTION_ID, Sections.SEARCH_SECTION_ID))
        assertEquals(TabClick.OPEN_SEARCH, tabClick(subs, Sections.SEARCH_SECTION_ID))
        assertEquals(TabClick.OPEN_SETTINGS, tabClick(subs, MediaGroup.TYPE_SETTINGS))
        assertEquals(TabClick.OPEN_SETTINGS, tabClick(MediaGroup.TYPE_SETTINGS, MediaGroup.TYPE_SETTINGS))
    }

    @Test
    fun oldContentOfTheSelectedTabReloads() {
        assertTrue(shouldQuietReload(subs, loadedAtMs = 0, nowMs = 16 * min, busy = false))
    }

    @Test
    fun freshContentStays() {
        assertFalse(shouldQuietReload(subs, 0, 15 * min, busy = false))
        assertFalse(shouldQuietReload(subs, 0, 5 * min, busy = false))
    }

    @Test
    fun shortenedThresholdIsHonoured() {
        assertTrue(shouldQuietReload(subs, 0, 11_000, busy = false, thresholdMs = 10_000))
    }

    @Test
    fun noReloadWhileOneRunsOrWithoutContentOrOnSearchAndSettings() {
        assertFalse(shouldQuietReload(subs, 0, 60 * min, busy = true))
        assertFalse(shouldQuietReload(subs, null, 60 * min, busy = false))
        assertFalse(shouldQuietReload(null, 0, 60 * min, busy = false))
        assertFalse(shouldQuietReload(Sections.SEARCH_SECTION_ID, 0, 60 * min, busy = false))
        assertFalse(shouldQuietReload(MediaGroup.TYPE_SETTINGS, 0, 60 * min, busy = false))
    }

    @Test
    fun aClockThatWentBackDoesNotReload() {
        assertFalse(shouldQuietReload(subs, 100 * min, 50 * min, busy = false))
    }
}
