package io.github.ivanosh.stmaterial.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import io.github.ivanosh.stmaterial.logic.SKIP_CHIP_MS
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import io.github.ivanosh.stmaterial.logic.chapterIndexAt
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
    // Opening fades in over 150 ms and lifts 16 units; closing fades out over 120 ms. Both are drawn through the
    // layer from two animatables, so no frame composes anything: the targets come from a snapshot flow.
    val alpha = remember { Animatable(0f) }
    val lift = remember { Animatable(1f) }
    LaunchedEffect(state) {
        snapshotFlow { state.focus != PanelFocus.HIDDEN || state.seekPreview }.collectLatest { shown ->
            if (shown) {
                lift.snapTo(if (alpha.value < 1f) 1f else 0f)
                coroutineScope {
                    launch { alpha.animateTo(1f, tween(OPEN_MS)) }
                    launch { lift.animateTo(0f, tween(OPEN_MS)) }
                }
            } else {
                alpha.animateTo(0f, tween(CLOSE_MS))
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Column(Modifier.fillMaxWidth()) {
            // The chip sits on the panel's top edge, or, with the panel hidden, on its bottom margin. Above the scrim.
            Box(Modifier.zIndex(1f)) { SkipChipHost(state) }
            Column(
                Modifier
                    .collapsedWhenHidden(state)
                    .graphicsLayer {
                        this.alpha = alpha.value
                        translationY = lift.value * LiftDistance.toPx()
                    }
                    .fillMaxWidth()
                    .drawBehind {
                        // Clear at the top, 92% of the background at the bottom; it starts above the panel so that the title stands on it.
                        val fade = ScrimFade.toPx()
                        drawRect(scrim, Offset(0f, -fade), Size(size.width, size.height + fade))
                    }
                    .padding(start = 58.dp, end = 58.dp, top = 36.dp, bottom = 40.dp)
            ) {
                Box(Modifier.onlyWhenOpen(state).padding(bottom = 24.dp)) { Header(state) }
                Box(Modifier.onlyWhenPreview(state)) { SeekChapterTitle(state) }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressBar(state)
                    TimeRow(state)
                }
                Box(Modifier.onlyWhenOpen(state).padding(top = 24.dp)) { Buttons(state) }
                ChapterRow(state)
            }
        }
        MoreSheet(state)
    }
}

private const val OPEN_MS = 150
private const val CLOSE_MS = 120
private val LiftDistance = 16.dp
private val ScrimFade = 80.dp
private val scrim = Brush.verticalGradient(
    0f to Color.Transparent,
    0.2f to Palette.Background.copy(alpha = 0.85f),
    0.5f to Palette.Background.copy(alpha = 0.9f),
    1f to Palette.Background.copy(alpha = 0.92f),
)

/** A hidden panel is invisible, and it leaves only its bottom margin, so that the chip does not float. Read in layout, so no composition follows. */
private fun Modifier.collapsedWhenHidden(state: PlayerOverlayState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shown = state.focus != PanelFocus.HIDDEN || state.seekPreview
    layout(placeable.width, if (shown) placeable.height else 40.dp.roundToPx()) { placeable.place(0, 0) }
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

/** The reverse of [onlyWhenOpen]: the open panel has its header, and the title of the chapter is in it. */
private fun Modifier.onlyWhenPreview(state: PlayerOverlayState): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    if (state.focus == PanelFocus.HIDDEN) {
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    } else {
        layout(0, 0) {}
    }
}

/**
 * The chapter at the position the seek is going to, above the bar. The position is read inside a derived state, so this
 * composes again only when the chapter changes, not at every key or tick.
 */
@Composable
private fun SeekChapterTitle(state: PlayerOverlayState) {
    val chapters = state.chapters
    if (chapters.isEmpty()) return
    val starts = remember(chapters) { chapters.map { it.startMs } }
    val index = remember(chapters) { derivedStateOf { chapterIndexAt(starts, state.positionMs) } }.value
    val title = chapters.getOrNull(index)?.title?.ifBlank { null } ?: return
    Text(title, style = subtitleStyle.copy(color = Palette.OnBackground), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 12.dp))
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
 * redraws without composing the panel, and drawing walks pre-sized arrays by index (no closure, no iterator); only what Canvas itself allocates remains.
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
    // Plain arrays of the SponsorBlock marks, so that drawing walks them by index and creates no iterator.
    val markStarts = remember(marks) { marks.filter { !it.isChapter }.map { it.start }.toFloatArray() }
    val markEnds = remember(marks) { marks.filter { !it.isChapter }.map { it.end }.toFloatArray() }
    val markColors = remember(marks) { marks.filter { !it.isChapter }.map { markColor(it) }.toTypedArray() }
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
        val minMark = MinMark.toPx()
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
            if (markStarts.isNotEmpty()) {
                // Marks keep to the shape of the piece.
                clipPath(path) {
                    for (m in markStarts.indices) {
                        val from = maxOf(left, markStarts[m] * size.width)
                        val to = minOf(right, maxOf(markEnds[m] * size.width, markStarts[m] * size.width + minMark))
                        if (to > from) drawRect(markColors[m], Offset(from, top), Size(to - from, trackHeight))
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
    // The legend sits between the times, or, when it does not fit there, on a line of its own that is
    // reserved for this video; both were decided when the marks arrived.
    val legend = state.legend
    // The width of the window in the density Compose draws with: Configuration's can differ on a wrapped context.
    val widthDp = LocalWindowInfo.current.containerSize.width / LocalDensity.current.density
    // Decided when the legend changes, with room for both times; not measured while playing.
    val wraps = remember(legend, widthDp) { !legendFits(legend, widthDp - 2 * 58f - 2 * 110f) }
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatPlayerTime(state.positionMs), style = timeStyle)
            if (legend.isNotEmpty() && !wraps) Legend(legend)
            Text(if (state.durationMs > 0) formatPlayerTime(state.durationMs) else "", style = timeStyle)
        }
        if (legend.isNotEmpty() && wraps) Box(Modifier.padding(top = 8.dp)) { Legend(legend) }
    }
}

/** A swatch and a name for each category on the bar. */
@Composable
private fun Legend(entries: List<LegendEntry>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(28.dp, 8.dp).background(Color(entry.argb), RoundedCornerShape(4.dp)))
                Text(entry.name, style = timeStyle, maxLines = 1)
            }
        }
    }
}

/** Nine items (eight buttons and More) take 9 x 150 + 8 x 20 = 1510 of the 1804 units inside the panel's padding. */
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

/** A small icon-only circle, for the editor's preview of the bar; [on] marks a switched-on state with the accent colour. */
@Composable
internal fun CompactButton(iconPath: String, focused: Boolean, on: Boolean, size: Dp = 40.dp, iconSize: Dp = 24.dp) {
    Box(
        Modifier
            .size(size)
            .focusRing(focused, corner = size / 2, gap = 3.dp)
            .background(if (focused) Color.White else if (on) Palette.Accent else Palette.TabSelected, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        PanelIcon(iconPath, if (focused || on) Palette.Background else Palette.OnBackground, iconSize)
    }
}

@Composable
private fun PanelIcon(iconPath: String, color: Color, iconSize: Dp = 40.dp) {
    val path = remember(iconPath) { PathParser().parsePathString(iconPath).toPath() }
    Canvas(Modifier.size(iconSize)) {
        // The paths are drawn on a 24 by 24 grid.
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private val chipStyle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, color = Palette.OnBackground, fontWeight = FontWeight.Medium)
private val ChipBarHeight = 4.dp

/** Its own scope: the chip appearing or going composes this and nothing else, and no chip composes nothing. */
@Composable
private fun SkipChipHost(state: PlayerOverlayState) {
    val chip = state.chip ?: return
    Box(Modifier.padding(start = 58.dp, bottom = 16.dp)) { SkipChipView(chip) }
}

/**
 * A small opaque chip: it needs no scrim to read over a bright frame. The bar of the undo chip runs down in the
 * draw phase from one animatable, so no frame composes anything.
 */
@Composable
private fun SkipChipView(chip: SkipChipUi) {
    val left = remember(chip.id) { Animatable(1f) }
    if (chip.undo) LaunchedEffect(chip.id) { left.animateTo(0f, tween(SKIP_CHIP_MS.toInt(), easing = LinearEasing)) }
    val shape = RoundedCornerShape(32.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Palette.Background)
            .border(2.dp, Palette.ProgressTrack, shape)
            .drawWithContent {
                drawContent()
                if (chip.undo) {
                    val barHeight = ChipBarHeight.toPx()
                    drawRect(Palette.Accent, Offset(0f, size.height - barHeight), Size(size.width * left.value, barHeight))
                }
            }
            .padding(start = 24.dp, end = 28.dp, top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(16.dp).background(Color(chip.argb), CircleShape))
        Text(if (chip.undo) stringResource(R.string.material_skip_skipped, chip.name) else chip.name, style = chipStyle, maxLines = 1)
        if (chip.notice) {
            // A line of its own, nothing to press.
        } else if (chip.undo) {
            ChipArrow(pointsRight = false)
            Text(stringResource(R.string.material_skip_undo), style = chipStyle.copy(color = Palette.Accent))
        } else {
            Text(stringResource(R.string.material_skip_skip), style = chipStyle.copy(color = Palette.Accent))
            ChipArrow(pointsRight = true)
        }
    }
}

/**
 * The D-pad of the Chromecast remote, drawn: a ring, a dot in the middle, and the arc of the key to press filled in
 * the accent colour. One Canvas of paths and arcs, nothing to load, and it only draws when the chip does.
 */
@Composable
private fun ChipArrow(pointsRight: Boolean) {
    Canvas(Modifier.size(DPadSize)) {
        val outer = size.minDimension / 2
        val edge = 2.5.dp.toPx()
        val band = outer * 0.55f
        val middle = outer - band / 2
        drawCircle(Palette.OnBackground, radius = outer - edge / 2, style = Stroke(width = edge))
        // The key to press: a sector of the ring, over its outline.
        drawArc(
            Palette.Accent,
            startAngle = if (pointsRight) -50f else 130f,
            sweepAngle = 100f,
            useCenter = false,
            topLeft = Offset(outer - middle, outer - middle),
            size = Size(middle * 2, middle * 2),
            style = Stroke(width = band),
        )
        drawCircle(Palette.OnBackground, radius = outer * 0.16f)
    }
}

private val DPadSize = 32.dp
