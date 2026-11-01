package io.github.ivanosh.stmaterial.dialog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.ivanosh.stmaterial.ui.Palette

private val PanelFill = Color(0xFF1D2024)
private val TitleStyle = TextStyle(fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Medium)
private val RowStyle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp)
private val DescriptionStyle = TextStyle(fontSize = 22.sp, lineHeight = 30.sp)
private val PanelShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
private val RowShape = RoundedCornerShape(14.dp)
private const val SCRIM_ALPHA = 0.62f
private const val ROWS_ABOVE_FOCUS = 2

/**
 * Every dialog of the app: a panel on the right edge listing the lines of the top page.
 * Over the player ([DialogState.isTransparent]) nothing but the panel is drawn, so the video stays visible.
 */
@Composable
fun DialogScreen(state: DialogState, onActivate: (DialogRow) -> Unit) {
    val page = state.page
    Box(
        Modifier
            .fillMaxSize()
            .then(if (state.isTransparent) Modifier else Modifier.background(Palette.Background.copy(alpha = SCRIM_ALPHA))),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (page != null) {
            Column(
                Modifier
                    .width(760.dp)
                    .fillMaxHeight()
                    .clip(PanelShape)
                    .background(PanelFill)
                    .padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (!page.title.isNullOrEmpty()) {
                    Text(page.title, style = TitleStyle, color = Palette.OnBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // A new page starts at its own scroll position and focus, the same page keeps them when a line changes.
                key(page.id) { Rows(state, page, onActivate) }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun Rows(state: DialogState, page: DialogPage, onActivate: (DialogRow) -> Unit) {
    val focusKey = remember { state.initialFocus() }
    val start = remember { page.rows.indexOfFirst { it.key == focusKey }.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (start - ROWS_ABOVE_FOCUS).coerceAtLeast(0))
    val initial = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { initial.requestFocus() }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(page.rows, key = { _, row -> row.key }) { _, row ->
            DialogLine(row, onActivate, if (row.key == focusKey) Modifier.focusRequester(initial) else Modifier)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DialogLine(row: DialogRow, onActivate: (DialogRow) -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { onActivate(row) },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Palette.OnBackground,
            focusedContainerColor = Palette.OnBackground,
            focusedContentColor = PanelFill,
            pressedContainerColor = Palette.OnBackground,
            pressedContentColor = PanelFill,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {
        val contrast = if (focused) Palette.OnBackground else PanelFill
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (row) {
                is ChoiceRow -> if (row.multi) CheckMark(row.selected, contrast) else RadioMark(row.selected)
                else -> Unit
            }
            Column(Modifier.weight(1f)) {
                Text(row.title, style = RowStyle)
                if (row is ChoiceRow && !row.description.isNullOrEmpty()) {
                    Text(row.description, style = DescriptionStyle, color = if (focused) PanelFill else Palette.OnBackgroundVariant)
                }
            }
            when (row) {
                is SwitchRow -> SwitchMark(row.selected, contrast)
                is EntryRow -> Text("›", style = RowStyle)
                else -> Unit
            }
        }
    }
}

@Composable
private fun RadioMark(selected: Boolean) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(32.dp)) {
        drawCircle(color, radius = size.minDimension / 2 - 1.5.dp.toPx(), style = Stroke(3.dp.toPx()))
        if (selected) drawCircle(color, radius = size.minDimension / 4)
    }
}

@Composable
private fun CheckMark(selected: Boolean, contrast: Color) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(32.dp)) {
        val inset = 1.5.dp.toPx()
        val box = Size(size.width - 2 * inset, size.height - 2 * inset)
        val corner = CornerRadius(6.dp.toPx())
        if (selected) {
            drawRoundRect(color, Offset(inset, inset), box, corner, Fill)
            val tick = Path().apply {
                moveTo(size.width * 0.26f, size.height * 0.52f)
                lineTo(size.width * 0.43f, size.height * 0.69f)
                lineTo(size.width * 0.75f, size.height * 0.33f)
            }
            drawPath(tick, contrast, style = Stroke(3.dp.toPx()))
        } else {
            drawRoundRect(color, Offset(inset, inset), box, corner, Stroke(3.dp.toPx()))
        }
    }
}

@Composable
private fun SwitchMark(selected: Boolean, contrast: Color) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(width = 60.dp, height = 34.dp)) {
        val radius = CornerRadius(size.height / 2)
        val stroke = 3.dp.toPx()
        if (selected) {
            drawRoundRect(color, cornerRadius = radius)
        } else {
            drawRoundRect(color, Offset(stroke / 2, stroke / 2), Size(size.width - stroke, size.height - stroke), radius, Stroke(stroke))
        }
        val thumb = size.height / 2 - (if (selected) 5.dp.toPx() else 9.dp.toPx())
        val x = if (selected) size.width - size.height / 2 else size.height / 2
        drawCircle(if (selected) contrast else color, thumb, Offset(x, size.height / 2))
    }
}
