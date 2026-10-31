package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

class TabItem(val id: Int, val title: String, val isStart: Boolean)

private const val HOME_ICON = "M4 11l8-7 8 7M6 10v10h12V10"
private const val GEAR_ICON = "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM12 6a6 6 0 1 0 0 12 6 6 0 0 0 0-12zM12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2"

private val PillShape = RoundedCornerShape(32.dp)
private val LabelStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)

/**
 * The bar of the canvas: the tabs on the left and the Settings icon, outside the list, on the right.
 * Focus entering the bar always lands on the selected item.
 */
@Composable
fun Tabs(
    tabs: List<TabItem>,
    selected: Int?,
    settingsId: Int,
    settingsLabel: String,
    requesterFor: (Int) -> FocusRequester,
    onFocused: (Int) -> Unit,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 58.dp, end = 58.dp, top = 32.dp)
            .height(68.dp)
            .focusProperties {
                onEnter = {
                    val target = selected?.takeIf { id -> id == settingsId || tabs.any { it.id == id } }
                        ?: tabs.firstOrNull()?.id
                    if (target != null) requesterFor(target).requestFocus()
                }
            }
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                key(tab.id) {
                    BarButton(
                        selected = tab.id == selected,
                        shape = PillShape,
                        onFocused = { onFocused(tab.id) },
                        onClick = { onClick(tab.id) },
                        requester = requesterFor(tab.id),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 30.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (tab.isStart) StrokeIcon(HOME_ICON, Palette.Accent, Modifier.size(26.dp))
                            Text(tab.title, style = LabelStyle)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        BarButton(
            selected = settingsId == selected,
            shape = PillShape,
            onFocused = { onFocused(settingsId) },
            onClick = { onClick(settingsId) },
            requester = requesterFor(settingsId),
            modifier = Modifier.size(64.dp).semantics { contentDescription = settingsLabel },
        ) {
            StrokeIcon(GEAR_ICON, Palette.OnBackgroundVariant, Modifier.fillMaxSize().padding(14.dp))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BarButton(
    selected: Boolean,
    shape: Shape,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    requester: FocusRequester,
    modifier: Modifier = Modifier.height(64.dp),
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val container = if (selected) Palette.TabSelected else Color.Transparent
    val label = if (selected) Palette.OnBackground else Palette.OnBackgroundVariant
    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRing(focused, corner = 32.dp, gap = 2.dp)
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = container,
            contentColor = label,
            focusedContainerColor = container,
            focusedContentColor = label,
            pressedContainerColor = container,
            pressedContentColor = label
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

/** A line icon of the canvas, drawn from its 24x24 path. */
@Composable
private fun StrokeIcon(pathData: String, color: Color, modifier: Modifier) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
