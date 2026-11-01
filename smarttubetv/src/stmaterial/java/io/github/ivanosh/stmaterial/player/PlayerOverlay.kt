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
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.PanelFocus
import io.github.ivanosh.stmaterial.logic.formatPlayerTime
import io.github.ivanosh.stmaterial.ui.Palette
import io.github.ivanosh.stmaterial.ui.focusRing

private val MarkColor = Color(0xFFF2B84B)
private val titleStyle = TextStyle(fontSize = 44.sp, lineHeight = 54.sp, fontWeight = FontWeight.Medium, color = Palette.OnBackground)
private val subtitleStyle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, color = Palette.OnBackgroundVariant)
private val timeStyle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, color = Palette.OnBackgroundVariant)
private val buttonLabelStyle = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, color = Palette.OnBackground)

/** Draws only: the view holds no focus, and [MaterialPlaybackActivity] handles every key. */
@Composable
fun PlayerOverlay(state: PlayerOverlayState) {
    val open = state.focus != PanelFocus.HIDDEN
    if (!open && !state.seekPreview) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Palette.Background)
                .padding(start = 58.dp, end = 58.dp, top = 36.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (open) Header(state)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProgressBar(state)
                TimeRow(state)
            }
            if (open) Buttons(state)
        }
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

@Composable
private fun ProgressBar(state: PlayerOverlayState) {
    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
        // Read here, so that the position redraws the bar without composing the panel again.
        val progress = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        val top = 8.dp.toPx()
        val height = 8.dp.toPx()
        val radius = CornerRadius(4.dp.toPx())
        drawRoundRect(Palette.ProgressTrack, Offset(0f, top), Size(size.width, height), radius)
        drawRoundRect(Palette.Accent, Offset(0f, top), Size(size.width * progress, height), radius)
        for (mark in state.marks) {
            if (!mark.isChapter) {
                val left = mark.start * size.width
                drawRect(MarkColor, Offset(left, top), Size((mark.end * size.width - left).coerceAtLeast(6.dp.toPx()), height))
            }
        }
        for (mark in state.marks) {
            if (mark.isChapter) {
                drawRect(Palette.Background, Offset(mark.start * size.width, top), Size(6.dp.toPx(), height))
            }
        }
        val center = Offset(size.width * progress, size.height / 2)
        val thumb = 12.dp.toPx()
        drawCircle(Color.White, thumb, center)
        if (state.focus == PanelFocus.PROGRESS) {
            drawCircle(Palette.FocusRing, thumb + 5.dp.toPx(), center, style = Stroke(4.dp.toPx()))
        }
    }
}

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
        PANEL_BUTTONS.forEachIndexed { index, action ->
            val focused = state.focus == PanelFocus.BUTTONS && state.buttonIndex == index
            val paused = action == PanelAction.PlayPause && !state.playing
            PanelButton(
                label = if (paused) stringResource(R.string.material_player_play) else stringResource(action.label),
                iconPath = if (paused) PLAY_ICON_PATH else action.iconPath,
                focused = focused,
            )
        }
    }
}

@Composable
private fun PanelButton(label: String, iconPath: String, focused: Boolean) {
    Column(
        Modifier.width(150.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier
                .size(84.dp)
                .focusRing(focused, corner = 42.dp, gap = 3.dp)
                .background(if (focused) Color.White else Palette.TabSelected, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            PanelIcon(iconPath, if (focused) Palette.Background else Palette.OnBackground)
        }
        Text(label, style = buttonLabelStyle, maxLines = 1)
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
