package io.github.ivanosh.stmaterial.data

import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import io.github.ivanosh.stmaterial.logic.Sections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSidebar(var items: MutableList<Int>) : SidebarPort {
    var booted = -1
    var moves = 0
    var writes = 0
    override fun index(id: Int) = items.indexOf(id)
    override fun canMoveUp(id: Int) = index(id) > 0
    override fun moveUp(id: Int) {
        // Bounded: a non-terminating reorder fails instead of hanging.
        writes++
        check(++moves < 10_000) { "reorder did not converge: $items" }
        val i = index(id)
        items.removeAt(i)
        items.add(i - 1, id)
    }
    override fun enable(id: Int, on: Boolean) {
        writes++
        if (on) { if (id !in items) items.add(id) } else items.remove(id)
    }
    override fun setBoot(id: Int) {
        writes++
        booted = id
    }
    override fun boot() = booted
}

class SectionMirrorTest {
    private val ids = listOf(
        MediaGroup.TYPE_HOME, MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_NEWS
    )
    private val extras = listOf(MediaGroup.TYPE_SETTINGS, 5001, 5002) // Settings and pinned items

    private fun <T> perms(l: List<T>): List<List<T>> =
        if (l.size <= 1) listOf(l) else l.flatMap { x -> perms(l - x).map { listOf(x) + it } }

    @Test
    fun reorderConvergesForAllTargetsAndLayouts() {
        val all = ids + extras
        for (target in perms(ids)) for (layout in perms(all)) {
            val fake = FakeSidebar(layout.toMutableList())
            SectionMirror(fake).mirror(target, target[0])
            val actual = fake.items.filter { it in ids }
            assertEquals("target=$target layout=$layout", target, actual)
            assertTrue(extras.all { it in fake.items })
        }
    }

    @Test
    fun firstRunDefaultsMirrorRealSectionsOnly() {
        val fake = FakeSidebar(mutableListOf(MediaGroup.TYPE_SETTINGS, MediaGroup.TYPE_SHORTS, MediaGroup.TYPE_KIDS_HOME))
        SectionMirror(fake).mirror(Sections.DEFAULT_SHOWN, Sections.START_DEFAULT)
        val real = Sections.DEFAULT_SHOWN.filter { it != Sections.SEARCH_SECTION_ID }
        assertEquals(real, fake.items.filter { it in real })
        assertFalse(Sections.SEARCH_SECTION_ID in fake.items)
        assertFalse(MediaGroup.TYPE_SHORTS in fake.items)
        assertFalse(MediaGroup.TYPE_KIDS_HOME in fake.items)
        assertTrue(MediaGroup.TYPE_SETTINGS in fake.items)
        assertEquals(MediaGroup.TYPE_SUBSCRIPTIONS, fake.booted)
    }

    @Test
    fun searchStartBootsFirstRealTab() {
        val fake = FakeSidebar(mutableListOf())
        val shown = listOf(Sections.SEARCH_SECTION_ID, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_HOME)
        SectionMirror(fake).mirror(shown, Sections.SEARCH_SECTION_ID)
        assertEquals(MediaGroup.TYPE_MUSIC, fake.booted)
        assertFalse(Sections.SEARCH_SECTION_ID in fake.items)
    }

    @Test
    fun disabledSectionsAreRemoved() {
        val fake = FakeSidebar(mutableListOf(MediaGroup.TYPE_HOME, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_SHORTS))
        SectionMirror(fake).mirror(listOf(MediaGroup.TYPE_HOME), MediaGroup.TYPE_HOME)
        assertEquals(listOf(MediaGroup.TYPE_HOME), fake.items)
    }

    @Test
    fun searchOnlyListEnablesHomeAsBoot() {
        val fake = FakeSidebar(mutableListOf(MediaGroup.TYPE_MUSIC))
        fake.booted = MediaGroup.TYPE_MUSIC
        SectionMirror(fake).mirror(listOf(Sections.SEARCH_SECTION_ID), Sections.SEARCH_SECTION_ID)
        assertEquals(MediaGroup.TYPE_HOME, fake.booted)
        assertTrue(MediaGroup.TYPE_HOME in fake.items)
        assertFalse(MediaGroup.TYPE_MUSIC in fake.items)
        assertFalse(Sections.SEARCH_SECTION_ID in fake.items)
    }

    @Test
    fun halfAppliedSidebarConvergesToThePrefs() {
        val target = listOf(MediaGroup.TYPE_HISTORY, MediaGroup.TYPE_HOME, MediaGroup.TYPE_MUSIC)
        // A save cut after enabling Music and Shorts but before ordering and booting.
        val fake = FakeSidebar(
            mutableListOf(MediaGroup.TYPE_HOME, MediaGroup.TYPE_SETTINGS, MediaGroup.TYPE_NEWS, MediaGroup.TYPE_MUSIC, MediaGroup.TYPE_SHORTS)
        )
        fake.booted = MediaGroup.TYPE_NEWS
        val changed = SectionMirror(fake).mirror(target, MediaGroup.TYPE_HISTORY)
        assertTrue(changed)
        assertEquals(target, fake.items.filter { it in target })
        assertFalse(MediaGroup.TYPE_NEWS in fake.items)
        assertFalse(MediaGroup.TYPE_SHORTS in fake.items)
        assertTrue(MediaGroup.TYPE_SETTINGS in fake.items)
        assertEquals(MediaGroup.TYPE_HISTORY, fake.booted)
    }

    @Test
    fun alreadyCorrectSidebarCausesNoMovesOrWrites() {
        val target = listOf(MediaGroup.TYPE_HISTORY, Sections.SEARCH_SECTION_ID, MediaGroup.TYPE_HOME)
        val fake = FakeSidebar(mutableListOf(MediaGroup.TYPE_SETTINGS, 5001))
        SectionMirror(fake).mirror(target, MediaGroup.TYPE_HOME)
        fake.moves = 0
        fake.writes = 0
        assertFalse(SectionMirror(fake).mirror(target, MediaGroup.TYPE_HOME))
        assertEquals(0, fake.writes)
        assertEquals(0, fake.moves)
    }

    @Test
    fun searchOnlyListIsStableOnRepeat() {
        val fake = FakeSidebar(mutableListOf())
        val shown = listOf(Sections.SEARCH_SECTION_ID)
        SectionMirror(fake).mirror(shown, Sections.SEARCH_SECTION_ID)
        fake.writes = 0
        assertFalse(SectionMirror(fake).mirror(shown, Sections.SEARCH_SECTION_ID))
        assertEquals(0, fake.writes)
        assertEquals(MediaGroup.TYPE_HOME, fake.booted)
    }
}
