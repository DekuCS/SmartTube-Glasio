package io.github.ivanosh.stmaterial.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ivanosh.stmaterial.logic.ButtonGroup
import io.github.ivanosh.stmaterial.logic.DEFAULT_BUTTONS
import io.github.ivanosh.stmaterial.logic.PanelFocus
import io.github.ivanosh.stmaterial.logic.PlayerButton

/**
 * A stretch of the progress bar, in fractions of the video. A chapter start is drawn as a gap.
 * [argb] is the colour of a SponsorBlock category, 0 when unknown.
 */
data class ProgressMark(val start: Float, val end: Float, val isChapter: Boolean, val argb: Int = 0)

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
}
