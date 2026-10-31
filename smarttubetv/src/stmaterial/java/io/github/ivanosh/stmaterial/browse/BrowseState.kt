package io.github.ivanosh.stmaterial.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import io.github.ivanosh.stmaterial.logic.Sections

// Group parsing follows BrowseState in margantcovka/SmartTube-New (MIT).

data class FocusKey(val rowId: String, val videoId: String)

/** [action] is null when the presenter offers no action, and the screen then retries the section. */
data class ErrorData(val message: String, val actionText: String?, val action: (() -> Unit)?)

/** Id of a video inside a row. Video.equals is heavy, and lazy lists need unique keys. */
fun videoKey(video: Video): String =
    video.videoId ?: video.playlistId ?: video.channelId ?: video.title ?: System.identityHashCode(video).toString()

/** A row of cards. A grid section is one row with [isGrid] set, cut into lines by the screen. */
class VideoRow(val id: String, title: String?, val isGrid: Boolean) {
    var title by mutableStateOf(title)
    val videos = mutableStateListOf<Video>()

    /** A video refers to its group weakly, and needs it for the next page. Nothing else holds it here. */
    var group: VideoGroup? = null
        internal set
    private val keys = HashSet<String>()
    internal var requestedMoreAt = -1

    /** YouTube often repeats shown videos in a continuation. A page of repeats allows the next request. */
    internal fun add(items: List<Video>, atStart: Boolean = false) {
        val fresh = items.filter { keys.add(videoKey(it)) }
        if (fresh.isEmpty()) requestedMoreAt = -1
        if (atStart) videos.addAll(0, fresh) else videos.addAll(fresh)
    }

    internal fun removeWhere(predicate: (Video) -> Boolean) {
        videos.removeAll(predicate)
        keys.clear()
        videos.forEach { keys.add(videoKey(it)) }
    }

    internal fun sync(items: List<Video>) {
        items.forEach { fresh ->
            val key = videoKey(fresh)
            val index = videos.indexOfFirst { videoKey(it) == key }
            if (index >= 0) videos[index] = fresh
        }
    }

    companion object {
        const val GRID_ID = "grid"
    }
}

/** What the presenter has told the screen, plus where focus is. */
class BrowseState {
    /** All sections of the presenter, in its order: its selectSection() passes indexes into this list. */
    val sections = mutableStateListOf<BrowseSection>()
    var shown by mutableStateOf<List<Int>>(emptyList())
    var selected by mutableStateOf<Int?>(null)
        private set
    val rows = mutableStateListOf<VideoRow>()
    val settings = mutableStateListOf<SettingsItem>()
    var focused by mutableStateOf<FocusKey?>(null)
        private set
    var error by mutableStateOf<ErrorData?>(null)
        private set
    var loading by mutableStateOf(false)

    /** The card that should take focus once it is on screen. */
    var pendingFocus by mutableStateOf<FocusKey?>(null)

    private var focusedRow by mutableIntStateOf(NO_ROW)

    val tabsVisible: Boolean get() = focusedRow <= 0

    /** True when the screen is at its top, so the feed must not stay scrolled away under the tabs. */
    val focusOnTabs: Boolean get() = focusedRow == NO_ROW

    val isEmpty: Boolean get() = rows.all { it.videos.isEmpty() } && settings.isEmpty()

    val selectedSection: BrowseSection? get() = sections.firstOrNull { it.id == selected }

    /** Sections of type error (locked account) replace the content, so they are always reachable. */
    fun tabs(): List<Int> {
        val available = sections.mapTo(HashSet()) { it.id }
        val locked = sections.filter { it.type == BrowseSection.TYPE_ERROR }.map { it.id }
        return locked + shown.filter { it == Sections.SEARCH_SECTION_ID || it in available }
    }

    fun canSelect(id: Int): Boolean = id == MediaGroup.TYPE_SETTINGS || id in tabs()

    fun selectTab(id: Int) {
        selected = id
        clearContent()
    }

    fun clearContent() {
        rows.clear()
        settings.clear()
        error = null
        focused = null
    }

    fun removeSection(id: Int) {
        sections.removeAll { it.id == id }
    }

    fun updateSection(group: VideoGroup) {
        if (!acceptsContent(group.section)) return
        val grid = selectedSection?.let { it.type != BrowseSection.TYPE_ROW } ?: false
        val rowId = if (grid) VideoRow.GRID_ID else group.id.toString()
        val videos = group.videos.orEmpty()

        when (group.action) {
            VideoGroup.ACTION_REMOVE -> {
                val gone = videos.mapTo(HashSet(), ::videoKey)
                removeFromRows { videoKey(it) in gone }
                return
            }
            VideoGroup.ACTION_REMOVE_AUTHOR -> {
                val channels = videos.mapNotNullTo(HashSet()) { it.channelId }
                removeFromRows { it.channelId in channels }
                return
            }
            VideoGroup.ACTION_SYNC -> {
                rows.firstOrNull { it.id == rowId }?.sync(videos)
                return
            }
            VideoGroup.ACTION_REPLACE ->
                if (grid || group.position == -1) rows.clear() else rows.removeAll { it.id == rowId }
        }
        if (videos.isEmpty()) return

        error = null
        val row = rows.firstOrNull { it.id == rowId }
        row?.group = group
        when {
            row == null -> {
                val created = VideoRow(rowId, if (grid) null else group.title, grid)
                created.group = group
                created.add(videos)
                val at = group.position
                if (at < 0 || at > rows.size) rows.add(created) else rows.add(at, created)
            }
            group.action == VideoGroup.ACTION_PREPEND -> row.add(videos, atStart = true)
            else -> row.add(videos)
        }
    }

    fun updateSettings(section: BrowseSection?, items: List<SettingsItem>) {
        if (section?.id != selected) return
        settings.clear()
        settings.addAll(items)
    }

    /** Errors have no section. The Search tab loads nothing, so one arriving there is a late one. */
    fun showError(data: ErrorData) {
        if (selected == Sections.SEARCH_SECTION_ID) return
        error = data
    }

    fun onCardFocused(key: FocusKey, rowIndex: Int) {
        focused = key
        focusedRow = rowIndex
    }

    fun onContentFocused(rowIndex: Int) {
        focused = null
        focusedRow = rowIndex
    }

    fun onTabsFocused() {
        focused = null
        focusedRow = NO_ROW
    }

    /** The saved card if it still exists, otherwise the first one. Null when there are no cards. */
    fun restoreFocus(): FocusKey? {
        val saved = focused
        if (saved != null) {
            val row = rows.firstOrNull { it.id == saved.rowId }
            if (row != null && row.videos.any { videoKey(it) == saved.videoId }) return saved
        }
        val first = rows.firstOrNull { it.videos.isNotEmpty() } ?: return null
        return FocusKey(first.id, videoKey(first.videos[0]))
    }

    /** The video to continue from, at most once per size of the row. */
    fun takeMoreRequest(row: VideoRow): Video? {
        if (row.requestedMoreAt == row.videos.size) return null
        row.requestedMoreAt = row.videos.size
        return row.videos.lastOrNull()
    }

    private fun acceptsContent(section: BrowseSection?): Boolean =
        selected != Sections.SEARCH_SECTION_ID && (section == null || section.id == selected)

    private fun removeFromRows(predicate: (Video) -> Boolean) {
        rows.forEach { it.removeWhere(predicate) }
        rows.removeAll { it.videos.isEmpty() }
    }

    private companion object {
        const val NO_ROW = -1
    }
}

/**
 * Keeps a held D-pad from outrunning the screen: repeats pass at most every [MIN_STEP_MS], and those
 * that waited in the queue are dropped, so nothing keeps moving after the key is released.
 * Single presses always pass.
 */
class KeyRepeatGuard {
    private var lastPassedMs = Long.MIN_VALUE / 2

    fun shouldDrop(repeatCount: Int, eventTimeMs: Long, nowMs: Long): Boolean {
        if (repeatCount == 0) return false
        if (nowMs - eventTimeMs > MAX_QUEUE_MS || eventTimeMs - lastPassedMs < MIN_STEP_MS) return true
        lastPassedMs = eventTimeMs
        return false
    }

    private companion object {
        const val MIN_STEP_MS = 100L
        const val MAX_QUEUE_MS = 150L
    }
}
