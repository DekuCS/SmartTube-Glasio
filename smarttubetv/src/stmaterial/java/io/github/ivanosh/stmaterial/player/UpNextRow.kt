package io.github.ivanosh.stmaterial.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.PanelFocus
import io.github.ivanosh.stmaterial.logic.PanelRowShape
import io.github.ivanosh.stmaterial.logic.upNextRowShape
import io.github.ivanosh.stmaterial.ui.ItemShape
import io.github.ivanosh.stmaterial.ui.ItemStyle
import io.github.ivanosh.stmaterial.ui.MenuFill
import io.github.ivanosh.stmaterial.ui.MenuShape
import io.github.ivanosh.stmaterial.ui.Palette
import io.github.ivanosh.stmaterial.ui.VideoCard

// The same gap as the feed: a focused card grows and draws its ring, so less would touch the next card.
private val CardGap = 32.dp
private val RowTopGap = 32.dp
private val LabelHeight = 42.dp
private val CardRoom = 14.dp
private val labelStyle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, color = Palette.OnBackgroundVariant)

/** 0 hidden, 1 the tops peek, 2 the whole row: what the layout animates between. */
internal fun PanelRowShape.level(): Float = when (this) {
    PanelRowShape.Hidden -> 0f
    PanelRowShape.Peek -> 1f
    PanelRowShape.Full -> 2f
}

/**
 * The slot of a row in the panel's column. Its height is [peek] at level 1, the whole row at 2, nothing at 0 or with the
 * panel hidden; the level is read in layout and draw, so a change composes nothing. The content is as wide as the screen,
 * so that cards scroll out at its edge. At a peek the cards hang over the panel's bottom margin and the screen cuts them;
 * drawing is clipped to that margin, so that a row that gives way does not draw over the one below.
 */
internal fun Modifier.rowSlot(state: PlayerOverlayState, level: State<Float>, peek: Dp): Modifier = this
    .drawWithContent {
        val side = RowSide.toPx()
        val l = level.value
        val overflow = PeekHeight.toPx() * l.coerceAtMost(1f)
        clipRect(-side, 0f, size.width + side, size.height + overflow) { this@drawWithContent.drawContent() }
    }
    .layout { measurable, constraints ->
        val side = RowSide.roundToPx()
        val width = constraints.maxWidth + 2 * side
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = Constraints.Infinity))
        val peekPx = peek.roundToPx()
        val l = level.value
        val height = when {
            !state.layoutOpen -> 0
            l <= 1f -> (peekPx * l).toInt()
            else -> (peekPx + (placeable.height - peekPx) * (l - 1f)).toInt()
        }
        layout(constraints.maxWidth, height) { placeable.place(IntOffset(-side, 0)) }
    }

/**
 * The related videos under the buttons, or under the chapter row: the label and cards in the look of the feed. Nothing is
 * composed, and so no thumbnail asked for, until the panel has been open for this video; then the lazy row composes the
 * cards in view and a few more. Position is not read here.
 */
@Composable
fun UpNextRow(state: PlayerOverlayState) {
    if (state.upNext.isEmpty() || !state.upNextRevealed) return
    val videos = state.upNext
    val inRow by remember { derivedStateOf { state.focus == PanelFocus.UPNEXT } }
    val level = animateFloatAsState(upNextRowShape(state.focus, state.chapters.isNotEmpty()).level(), tween(150), label = "upNextRow")
    val list = rememberLazyListState()
    val focusedIndex = if (inRow) state.upNextIndex else -1
    LaunchedEffect(focusedIndex) {
        if (focusedIndex < 0) return@LaunchedEffect
        val shown = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusedIndex }
        val end = list.layoutInfo.viewportEndOffset
        val fits = shown != null && shown.offset >= list.layoutInfo.viewportStartOffset && shown.offset + shown.size <= end
        if (!fits) list.animateScrollToItem(focusedIndex)
    }
    // Composed only while the row has room (a peek or all of it) and the panel is open: a hidden row, or a closed panel, holds no
    // thumbnails. The list state outlives this, so the scroll position stays.
    val composeCards by remember { derivedStateOf { state.layoutOpen && level.value > 0f } }
    Box(Modifier.rowSlot(state, level, PeekHeight + LabelHeight + RowTopGap)) {
        if (composeCards) Column(Modifier.padding(top = RowTopGap)) {
            Text(
                stringResource(R.string.material_player_up_next),
                style = labelStyle,
                modifier = Modifier.padding(start = RowSide).height(LabelHeight),
            )
            LazyRow(
                state = list,
                contentPadding = PaddingValues(start = RowSide, end = RowSide, top = CardRoom, bottom = CardRoom),
                horizontalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                itemsIndexed(videos) { index, video ->
                    VideoCard(
                        video = video,
                        focused = index == focusedIndex,
                        onClick = {},
                        onLongClick = {},
                        interactive = false,
                        tone = Palette.Tones[index % Palette.Tones.size],
                    )
                }
            }
        }
    }
}

/**
 * The menu of a hold on a card, in the look of the app's context menu. Draws only: [MaterialPlaybackActivity] holds the
 * cursor and the keys, since the overlay takes no focus. Composed only while a menu is open.
 */
@Composable
internal fun PickMenuCard(menu: CardMenuUi) {
    Column(
        Modifier
            .width(520.dp)
            .clip(MenuShape)
            .background(MenuFill)
            .border(2.dp, Palette.ProgressTrack, MenuShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        menu.labels.forEachIndexed { index, label ->
            val focused = index == menu.menu.cursor
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(ItemShape)
                    .background(if (focused) Palette.OnBackground else Color.Transparent)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    label,
                    style = ItemStyle.copy(color = if (focused) Palette.Background else Palette.OnBackground),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
