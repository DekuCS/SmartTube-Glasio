package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
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
// A gear outline: eight teeth around a hub ring. Drawn with a 2 stroke it still shows teeth at 36 px.
private const val GEAR_ICON = "M10.0 4.7L10.4 1.7L13.6 1.7L14.0 4.7L15.8 5.4L18.1 3.6L20.4 5.9L18.6 8.2L19.3 10.0L22.3 10.4L22.3 13.6L19.3 14.0L18.6 15.8L20.4 18.1L18.1 20.4L15.8 18.6L14.0 19.3L13.6 22.3L10.4 22.3L10.0 19.3L8.2 18.6L5.9 20.4L3.6 18.1L5.4 15.8L4.7 14.0L1.7 13.6L1.7 10.4L4.7 10.0L5.4 8.2L3.6 5.9L5.9 3.6L8.2 5.4zM15.2 12a3.2 3.2 0 1 0-6.4 0 3.2 3.2 0 0 0 6.4 0z"

private val GEAR_SLOT = 80.dp
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
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The 8 dp inside the list keeps the focus ring of the first and last tab out of the scroll clip;
    // the bar is 12 dp taller than the tabs for the same reason. The gear stays put at the right.
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 50.dp, end = 58.dp, top = 28.dp)
            .height(76.dp)
            .focusProperties {
                onEnter = {
                    val target = selected?.takeIf { id -> id == settingsId || tabs.any { it.id == id } }
                        ?: tabs.firstOrNull()?.id
                    if (target != null) requesterFor(target).requestFocus()
                }
            }
            .focusGroup(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(end = GEAR_SLOT)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
                .align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                key(tab.id) {
                    BarButton(
                        selected = tab.id == selected,
                        shape = PillShape,
                        onFocused = { onFocused(tab.id) },
                        onClick = { onClick(tab.id) },
                        onLongPress = { onLongPress(tab.id) },
                        requester = requesterFor(tab.id),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 30.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (tab.isStart) StrokeIcon(HOME_ICON, Palette.Accent, Modifier.size(26.dp))
                            Text(tab.title, style = LabelStyle, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
        BarButton(
            selected = settingsId == selected,
            shape = PillShape,
            onFocused = { onFocused(settingsId) },
            onClick = { onClick(settingsId) },
            onLongPress = { onLongPress(settingsId) },
            requester = requesterFor(settingsId),
            modifier = Modifier.align(Alignment.CenterEnd).size(64.dp).semantics { contentDescription = settingsLabel },
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
    onLongPress: () -> Unit,
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
            .onLongOk(onLongPress)
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
internal fun StrokeIcon(pathData: String, color: Color, modifier: Modifier) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        withTransform({ scale(unit, unit, Offset.Zero) }) {
            drawPath(path, color, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
