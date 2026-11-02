package io.github.ivanosh.stmaterial.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ivanosh.stmaterial.logic.ButtonGroup
import io.github.ivanosh.stmaterial.logic.ButtonMenu
import io.github.ivanosh.stmaterial.logic.DEFAULT_BUTTONS
import io.github.ivanosh.stmaterial.logic.PanelFocus
import io.github.ivanosh.stmaterial.logic.PickMenu
import io.github.ivanosh.stmaterial.logic.PlayerButton
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video

/**
 * A stretch of the progress bar, in fractions of the video. A chapter start is drawn as a gap.
 * [argb] is the colour of a SponsorBlock category, 0 when unknown.
 */
data class ProgressMark(val start: Float, val end: Float, val isChapter: Boolean, val argb: Int = 0)

/**
 * The chip above the panel. [undo] is the one that follows an automatic skip, with a bar that runs down for
 * [io.github.ivanosh.stmaterial.logic.SKIP_CHIP_MS]; [id] tells one showing from the next.
 */
data class SkipChipUi(val id: Long, val undo: Boolean, val name: String, val argb: Int, val notice: Boolean = false)

/** A chapter of the video. [thumbUrl] is the engine's, null when it has none. */
data class ChapterUi(val title: String, val startMs: Long, val thumbUrl: String?)

/** The menu of a hold on an Up next card: the lines and the cursor. Drawn by the overlay, driven by the Activity. */
data class CardMenuUi(val labels: List<String>, val menu: PickMenu)

/** What the overlay draws. Only [MaterialPlaybackActivity] writes it, and only while something is on screen. */
class PlayerOverlayState {
    var focus by mutableStateOf(PanelFocus.HIDDEN)
    var buttonIndex by mutableIntStateOf(0)
    /** The progress bar alone, after a seek with the panel hidden. */
    var seekPreview by mutableStateOf(false)
    var title by mutableStateOf("")
    var author by mutableStateOf("")
    var chapter by mutableStateOf<String?>(null)
    var positionMs by mutableLongStateOf(0)
    var durationMs by mutableLongStateOf(0)
    var playing by mutableStateOf(true)
    /** Empty when the video has no chapters; then the panel is as it is without them. */
    var chapters by mutableStateOf(emptyList<ChapterUi>())
    /** The panel has been open for this video, so the chapter row may load its thumbnails. */
    var chaptersRevealed by mutableStateOf(false)
    /** The card in focus while focus is in the chapter row. */
    var chapterIndex by mutableIntStateOf(0)
    /** The first page of the suggestions' first group; empty when there is none. */
    var upNext by mutableStateOf(emptyList<Video>())
    /** The panel has been open for this video, so the Up next row may load its thumbnails. */
    var upNextRevealed by mutableStateOf(false)
    /** The card in focus while focus is in the Up next row. */
    var upNextIndex by mutableIntStateOf(0)
    /** Null when no card menu is open, and then nothing about it is composed. */
    var cardMenu by mutableStateOf<CardMenuUi?>(null)
    var marks by mutableStateOf(emptyList<ProgressMark>())
    /** One entry per SponsorBlock category on the bar, set with [marks]. */
    var legend by mutableStateOf(emptyList<LegendEntry>())
    /** The buttons on the panel, before More, which is always the last. */
    var buttons by mutableStateOf<List<PlayerButton>>(DEFAULT_BUTTONS)
    /** The state the presenter set for each button, by the id the Leanback button had. */
    val buttonStates = mutableStateMapOf<Int, Int>()
    /** What More lists while its sheet is open, else null. */
    var sheetGroups by mutableStateOf<List<ButtonGroup>?>(null)
    var sheetCursor by mutableIntStateOf(0)
    /** The menu of a hold on a button; null when none is open, and then nothing about it is composed. */
    var menu by mutableStateOf<ButtonMenu?>(null)
    /** The button at [buttonIndex] is lifted and LEFT and RIGHT move it. */
    var moving by mutableStateOf(false)
    /** Null when no chip is on screen, and then nothing about it is composed. */
    var chip by mutableStateOf<SkipChipUi?>(null)

    /**
     * What the panel's layout follows. They trail [focus] and [seekPreview] on a close: the layout keeps its shape until the
     * fade-out has ended, so the bar does not jump down while it fades. Written by the overlay, read in layout and draw only.
     */
    var layoutShown by mutableStateOf(false)
    var layoutOpen by mutableStateOf(false)
    /** The top of the seek bar in the panel, in pixels, for the scrim to reach full strength there. Read in draw only. */
    var barTopPx by mutableFloatStateOf(0f)
}
