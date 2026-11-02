package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

/** What the pill beside a page title shows: [checked] adds a check mark and the accent colour. */
data class PillState(val label: String, val checked: Boolean, val enabled: Boolean, val onClick: () -> Unit)

private val PillStyle = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SubscribePill(pill: PillState, modifier: Modifier = Modifier) {
    Surface(
        onClick = pill.onClick,
        enabled = pill.enabled,
        modifier = modifier.height(64.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(32.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Tile,
            contentColor = if (pill.checked) Palette.Accent else Palette.OnBackground,
            focusedContainerColor = Palette.OnBackground,
            focusedContentColor = Palette.Background,
            pressedContainerColor = Palette.OnBackground,
            pressedContentColor = Palette.Background,
            disabledContainerColor = Palette.Tile,
            disabledContentColor = Palette.OnBackgroundVariant,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {
        Row(
            Modifier.padding(horizontal = 28.dp).height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (pill.checked) Tick(LocalContentColor.current)
            Text(pill.label, style = PillStyle)
        }
    }
}

@Composable
private fun Tick(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val tick = Path().apply {
            moveTo(size.width * 0.14f, size.height * 0.54f)
            lineTo(size.width * 0.40f, size.height * 0.78f)
            lineTo(size.width * 0.86f, size.height * 0.24f)
        }
        drawPath(tick, color, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
