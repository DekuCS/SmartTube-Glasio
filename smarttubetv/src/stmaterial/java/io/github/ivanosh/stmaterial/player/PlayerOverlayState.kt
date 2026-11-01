package io.github.ivanosh.stmaterial.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ivanosh.stmaterial.logic.PanelFocus

/** A stretch of the progress bar, in fractions of the video. A chapter start is drawn as a gap. */
data class ProgressMark(val start: Float, val end: Float, val isChapter: Boolean)

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
}
