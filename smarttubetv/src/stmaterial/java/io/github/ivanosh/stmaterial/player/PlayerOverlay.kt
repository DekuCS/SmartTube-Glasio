package io.github.ivanosh.stmaterial.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.PanelFocus
import io.github.ivanosh.stmaterial.logic.PlayerButton
import io.github.ivanosh.stmaterial.logic.buttonIsOn
import io.github.ivanosh.stmaterial.logic.formatPlayerTime
import io.github.ivanosh.stmaterial.logic.handleCenter
import io.github.ivanosh.stmaterial.logic.layoutTrack
import io.github.ivanosh.stmaterial.logic.trackBufferSize
import io.github.ivanosh.stmaterial.ui.Palette
import io.github.ivanosh.stmaterial.ui.focusRing

private val MarkColor = Color(0xFFF2B84B)
private val titleStyle = TextStyle(fontSize = 44.sp, lineHeight = 54.sp, fontWeight = FontWeight.Medium, color = Palette.OnBackground)
private val subtitleStyle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, color = Palette.OnBackgroundVariant)
private val timeStyle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, color = Palette.OnBackgroundVariant)
internal val buttonLabelStyle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, color = Palette.OnBackground)

/**
 * Draws only: the view holds no focus, and [MaterialPlaybackActivity] handles every key.
 * Stays composed while hidden, so that opening the panel, the most-used action here, changes the
 * layer and the layout and builds nothing. Nothing writes the state while it is hidden, so it costs no frames.
 */
@Composable
fun PlayerOverlay(state: PlayerOverlayState) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (state.focus != PanelFocus.HIDDEN || state.seekPreview) 1f else 0f },
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Background)
                .padding(start = 58.dp, end = 58.dp, top = 36.dp, bottom = 40.dp)
        ) {
            Box(Modifier.onlyWhenOpen(state).padding(bottom = 24.dp)) { Header(state) }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressBar(state)
                TimeRow(state)
            }
            Box(Modifier.onlyWhenOpen(state).padding(top = 24.dp)) { Buttons(state) }
        }
        MoreSheet(state)
    }
}

/** Takes no room in the seek preview, which shows the progress bar alone. Read in layout, so no composition follows. */
private fun Modifier.onlyWhenOpen(state: PlayerOverlayState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    if (state.focus != PanelFocus.HIDDEN) {
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        layout(0, 0) {}
    }
}

@Composable
private fun Header(state: PlayerOverlayState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(state.title, style = titleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val subtitle = listOfNotNull(state.author.ifBlank { null }, state.chapter?.ifBlank { null }).joinToString(" · ")
        Text(subtitle, style = subtitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val TrackThin = 12.dp
private val TrackThick = 16.dp
private val HandleWidth = 4.dp
private val HandleWidthFocused = 6.dp
private val HandleHeight = 32.dp
private val HandleGap = 6.dp
private val ChapterGap = 4.dp
private val ChapterRadius = 2.dp
private val MinMark = 6.dp
private const val FOCUS_ANIMATION_MS = 120

/**
 * The Material 3 slider, in one Canvas: a track in pieces (chapter gaps, a clear gap each side of the handle),
 * and a vertical bar for a handle. The position and the thickness are read while drawing, so the bar
 * redraws without composing the panel, and nothing is allocated per frame: the pieces go into one buffer.
 */
@Composable
private fun ProgressBar(state: PlayerOverlayState) {
    val focused = state.focus == PanelFocus.PROGRESS
    val thick = animateFloatAsState(if (focused) 1f else 0f, tween(FOCUS_ANIMATION_MS), label = "barThickness")
    val marks = state.marks
    val starts = remember(marks) {
        marks.filter { it.isChapter && it.start > 0f && it.start < 1f }.map { it.start }.distinct().sorted().toFloatArray()
    }
    val buffer = remember(starts) { FloatArray(trackBufferSize(starts.size)) }
    val path = remember { Path() }
    val radii = remember { FloatArray(8) }
    Canvas(Modifier.fillMaxWidth().height(HandleHeight)) {
        // Read here, so that the position redraws the bar without composing the panel again.
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        val t = thick.value
        val trackHeight = (TrackThin + (TrackThick - TrackThin) * t).toPx()
        val handleWidth = (HandleWidth + (HandleWidthFocused - HandleWidth) * t).toPx()
        val top = (size.height - trackHeight) / 2
        val full = trackHeight / 2
        val small = ChapterRadius.toPx()
        val count = layoutTrack(size.width, progress, starts, starts.size, ChapterGap.toPx(), handleWidth, HandleGap.toPx(), buffer)
        val androidPath = path.asAndroidPath()
        for (i in 0 until count) {
            val at = i * 5
            val left = buffer[at]
            val right = buffer[at + 1]
            val leftRadius = minOf(if (buffer[at + 3] != 0f) full else small, (right - left) / 2)
            val rightRadius = minOf(if (buffer[at + 4] != 0f) full else small, (right - left) / 2)
            radii[0] = leftRadius; radii[1] = leftRadius
            radii[2] = rightRadius; radii[3] = rightRadius
            radii[4] = rightRadius; radii[5] = rightRadius
            radii[6] = leftRadius; radii[7] = leftRadius
            path.rewind()
            androidPath.addRoundRect(left, top, right, top + trackHeight, radii, android.graphics.Path.Direction.CW)
            drawPath(path, if (buffer[at + 2] != 0f) Palette.Accent else Palette.ProgressTrack)
            if (marks.isNotEmpty()) {
                // Marks keep to the shape of the piece.
                clipPath(path) {
                    for (mark in marks) {
                        if (mark.isChapter) continue
                        val from = maxOf(left, mark.start * size.width)
                        val to = minOf(right, maxOf(mark.end * size.width, mark.start * size.width + MinMark.toPx()))
                        if (to > from) drawRect(markColor(mark), Offset(from, top), Size(to - from, trackHeight))
                    }
                }
            }
        }
        val center = handleCenter(size.width, progress, handleWidth)
        drawRoundRect(
            Color.White,
            Offset(center - handleWidth / 2, (size.height - HandleHeight.toPx()) / 2),
            Size(handleWidth, HandleHeight.toPx()),
            CornerRadius(handleWidth / 2),
        )
    }
}

private fun markColor(mark: ProgressMark): Color = if (mark.argb == 0) MarkColor else Color(mark.argb)

@Composable
private fun TimeRow(state: PlayerOverlayState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatPlayerTime(state.positionMs), style = timeStyle)
        if (state.marks.any { !it.isChapter }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(28.dp, 8.dp).background(MarkColor, RoundedCornerShape(4.dp)))
                Text(stringResource(R.string.material_player_marks), style = timeStyle)
            }
        }
        Text(if (state.durationMs > 0) formatPlayerTime(state.durationMs) else "", style = timeStyle)
    }
}

@Composable
private fun Buttons(state: PlayerOverlayState) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
        state.buttons.forEachIndexed { index, button ->
            val paused = button == PlayerButton.PlayPause && !state.playing
            PanelButton(
                label = when {
                    button != PlayerButton.PlayPause -> stringResource(buttonLabel(button))
                    paused -> stringResource(R.string.material_player_play)
                    else -> stringResource(R.string.material_player_pause)
                },
                iconPath = if (paused) PLAY_ICON_PATH else buttonIcon(button),
                focused = state.focus == PanelFocus.BUTTONS && state.buttonIndex == index,
                on = button in STATEFUL_BUTTONS && buttonIsOn(button, viewId(button)?.let { state.buttonStates[it] }),
            )
        }
        PanelButton(
            label = stringResource(R.string.material_player_more),
            iconPath = MORE_ICON_PATH,
            focused = state.focus == PanelFocus.BUTTONS && state.buttonIndex == state.buttons.size,
            on = false,
        )
    }
}

/** A button of the panel: [on] marks a switched-on state with the accent colour. */
@Composable
internal fun PanelButton(label: String, iconPath: String, focused: Boolean, on: Boolean, size: Dp = 84.dp, labelStyle: TextStyle = buttonLabelStyle) {
    Column(
        Modifier.width(150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(size)
                .focusRing(focused, corner = size / 2, gap = 3.dp)
                .background(if (focused) Color.White else if (on) Palette.Accent else Palette.TabSelected, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            PanelIcon(iconPath, if (focused || on) Palette.Background else Palette.OnBackground)
        }
        Text(label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PanelIcon(iconPath: String, color: Color) {
    val path = remember(iconPath) { PathParser().parsePathString(iconPath).toPath() }
    Canvas(Modifier.size(40.dp)) {
        // The paths are drawn on a 24 by 24 grid.
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
