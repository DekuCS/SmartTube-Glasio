package io.github.ivanosh.stmaterial.data

import android.content.Context
import com.liskovsoft.smartyoutubetv2.common.app.presenters.service.SidebarService
import io.github.ivanosh.stmaterial.logic.EditorState
import io.github.ivanosh.stmaterial.logic.Sections
import io.github.ivanosh.stmaterial.logic.resolveStart
import io.github.ivanosh.stmaterial.logic.resolveTabs
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Persists tab order, start tab and the Search position in its own prefs, and mirrors real sections
 * to [SidebarService], which BrowsePresenter builds its section list from. Search is never mirrored.
 */
class SectionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mirror = SectionMirror(SidebarServicePort(SidebarService.instance(context)))

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

    /** [start], if given, is stored with the order, so that the mirror runs once. */
    fun save(state: EditorState<Int>, start: Int? = null) {
        write(state, start)
        mirror()
    }

    /**
     * Prefs are written before this returns, so a screen that reads them next sees the new list. The
     * mirror runs on the one process-wide save thread, in call order, and always from the prefs as they
     * are when it runs, so a late job can never put an older list back. [onDone] runs on that thread.
     */
    fun saveAsync(state: EditorState<Int>, start: Int? = null, onDone: () -> Unit = {}) {
        write(state, start)
        EXECUTOR.execute {
            mirror()
            onDone()
        }
    }

    /**
     * Makes the sidebar match the prefs again, in case a save was cut short. Reads only when it already
     * does. [onDone] gets true if it had to change something, and runs on the save thread.
     */
    fun repairAsync(onDone: (changed: Boolean) -> Unit) {
        EXECUTOR.execute { onDone(mirror()) }
    }

    private fun write(state: EditorState<Int>, start: Int?) {
        val shown = resolveTabs(state.shown, Sections.KNOWN)
        prefs.edit().apply {
            putString(KEY_ORDER, shown.joinToString(","))
            if (start != null) putInt(KEY_START, start)
        }.apply()
    }

    fun startSection(): Int =
        resolveStart(prefs.getInt(KEY_START, Sections.START_DEFAULT), load().shown)

    fun setStart(id: Int) {
        prefs.edit().putInt(KEY_START, id).apply()
        mirror()
    }

    private fun defaults() =
        EditorState(
            Sections.DEFAULT_SHOWN, Sections.HIDDEN_ADDABLE, emptySet(), Sections.LIMIT
        )

    private fun mirror(): Boolean = synchronized(LOCK) {
        mirror.mirror(load().shown, prefs.getInt(KEY_START, Sections.START_DEFAULT))
    }

    private fun parseIds(raw: String?): List<Int> =
        raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

    private companion object {
        val LOCK = Any()
        val EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "section-save").apply { isDaemon = true } }
        const val PREFS_NAME = "stmaterial_sections"
        const val KEY_ORDER = "order"
        const val KEY_START = "start"
    }
}
