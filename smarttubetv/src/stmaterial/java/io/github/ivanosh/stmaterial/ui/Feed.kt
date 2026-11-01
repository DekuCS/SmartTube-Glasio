package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import io.github.ivanosh.stmaterial.browse.BrowseState
import io.github.ivanosh.stmaterial.browse.FocusKey
import io.github.ivanosh.stmaterial.browse.VideoRow
import io.github.ivanosh.stmaterial.browse.videoKey
import io.github.ivanosh.stmaterial.logic.RowShape
import io.github.ivanosh.stmaterial.logic.anchorDistance
import io.github.ivanosh.stmaterial.logic.lineCount
import io.github.ivanosh.stmaterial.logic.itemCount
import io.github.ivanosh.stmaterial.logic.outerIndex

interface FeedActions {
    fun onVideoFocused(video: Video)
    fun onVideoClicked(video: Video)
    fun onVideoLongClicked(video: Video)
    fun onNeedMore(row: VideoRow)
}

private val SidePadding = 58.dp
private val CardGap = 32.dp
/** The outline and the focus scale reach past a card; a row clips what is further than this. */
private val CardBleed = 16.dp
/** Room under the last row for the anchor to reach it whatever has loaded: a screen is at most this tall in dp. */
private val ListBottomPadding = 1080.dp
private const val GRID_COLUMNS = 4
/** Next page when focus is this close to the end. */
private const val MORE_MARGIN = 5

// Below the first row the whole feed slides up with the tabs, so a card can keep one position in the list.
private val ListTopPadding = CardBleed
private val RowHeader = 40.dp + 18.dp
private val RowCardTop = ListTopPadding + RowHeader
private val GridCardTop = ListTopPadding

private val RowTitleStyle = TextStyle(color = Palette.OnBackground, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)

/** Keeps the focused card at a fixed height: the list moves a whole row per step. As in margantcovka/SmartTube-New (MIT). */
@OptIn(ExperimentalFoundationApi::class)
private class AnchoredSpec(private val target: Float, private val tolerance: Float, private val listState: LazyListState) : BringIntoViewSpec {
    // The focus scale of 1.04 and a move along the row change the rect by a few pixels; the column stays.
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        anchorDistance(offset, target, listState.isScrollInProgress, tolerance)
}

/** Moves a row only when the card would leave the screen, and then by as little as possible. */
@OptIn(ExperimentalFoundationApi::class)
private class MarginSpec(private val margin: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = when {
        offset < margin -> offset - margin
        offset + size > containerSize - margin -> offset + size - containerSize + margin
        else -> 0f
    }
}

/** The vertical scroll of a feed: a list of rows, or of the lines of a grid. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedColumn(
    state: BrowseState,
    cardTop: Dp,
    listState: LazyListState,
    padding: PaddingValues,
    spacing: Dp,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val spec = remember(density, cardTop, listState) { AnchoredSpec(with(density) { cardTop.toPx() }, with(density) { CardBleed.toPx() }, listState) }
    LaunchedEffect(listState) {
        snapshotFlow { state.focusOnTabs }.collect { if (it) listState.scrollToItem(0) }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

private fun List<VideoRow>.shapes() = map { RowShape(it.isGrid, it.videos.size) }

/**
 * Rows of cards and grids, one below the other. The usual section is one or the other; Subscriptions
 * is rows with a grid under them. Positions survive a row leaving the screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Feed(state: BrowseState, actions: FeedActions) {
    val rows = state.rows
    val listState = rememberLazyListState()
    val rowStates = remember { HashMap<String, LazyListState>() }
    val gridOnly = rows.firstOrNull()?.isGrid == true

    // A row that arrives above the first one (the history, late) must not leave the start screen scrolled past it.
    val firstId = rows.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (state.focusOnTabs) listState.scrollToItem(0)
    }

    val pending = state.pendingFocus
    LaunchedEffect(pending) {
        if (pending != null && !scrollTo(pending, state, listState, rowStates)) state.pendingFocus = null
    }

    val density = LocalDensity.current
    val rowSpec = remember(density) { MarginSpec(with(density) { SidePadding.toPx() }) }
    FeedColumn(
        state,
        if (gridOnly) GridCardTop else RowCardTop,
        listState,
        if (gridOnly) PaddingValues(bottom = ListBottomPadding) else PaddingValues(top = ListTopPadding, bottom = ListBottomPadding),
        spacing = if (gridOnly) 44.dp - CardBleed * 2 else 44.dp - CardBleed,
    ) {
        var first = 0
        rows.forEachIndexed { rowIndex, row ->
            val start = first
            first += itemCount(RowShape(row.isGrid, row.videos.size), GRID_COLUMNS)
            if (row.isGrid) {
                gridItems(state, actions, row, start, compact = !gridOnly)
            } else {
                item(key = row.id, contentType = "row") {
                    Column {
                        RowHeading(row.title)
                        Spacer(Modifier.height(18.dp - CardBleed))
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                            LazyRow(
                                state = rowStates.getOrPut(row.id) { LazyListState() },
                                contentPadding = PaddingValues(start = SidePadding, end = SidePadding, top = CardBleed, bottom = CardBleed),
                                horizontalArrangement = Arrangement.spacedBy(CardGap),
                            ) {
                                itemsIndexed(row.videos, key = { _, video -> videoKey(video) }, contentType = { _, _ -> "card" }) { index, video ->
                                    FeedCard(state, actions, row, video, index, start)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowHeading(title: String?) {
    BasicText(
        title.orEmpty(),
        style = RowTitleStyle,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = SidePadding).height(40.dp),
    )
}

/**
 * A grid cut into lines of [GRID_COLUMNS]; a title is a heading in the item of the first line. [first] is the
 * index of its first item. [compact]: under rows, where the list's spacing already is the gap between lines.
 */
private fun LazyListScope.gridItems(state: BrowseState, actions: FeedActions, row: VideoRow, first: Int, compact: Boolean) {
    val lines = lineCount(row.videos.size, GRID_COLUMNS)
    items(lines, key = { "${row.id}:line$it" }, contentType = { if (it == 0 && row.title != null) "headed" else "line" }) { line ->
        Column {
            if (line == 0 && row.title != null) {
                RowHeading(row.title)
                Spacer(Modifier.height(18.dp - CardBleed))
            }
            Row(
                Modifier.padding(start = SidePadding, top = CardBleed, bottom = if (compact) 0.dp else CardBleed),
                horizontalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                val end = minOf((line + 1) * GRID_COLUMNS, row.videos.size)
                for (index in line * GRID_COLUMNS until end) {
                    val video = row.videos[index]
                    key(videoKey(video)) {
                        FeedCard(state, actions, row, video, index, first + line)
                    }
                }
            }
        }
    }
}

/** Brings the row of a card that must take focus on screen, leaving it in place if it already is. */
private suspend fun scrollTo(
    key: FocusKey,
    state: BrowseState,
    listState: LazyListState,
    rowStates: Map<String, LazyListState>,
): Boolean {
    val rowIndex = state.rows.indexOfFirst { it.id == key.rowId }
    if (rowIndex < 0) return false
    val row = state.rows[rowIndex]
    val cardIndex = row.videos.indexOfFirst { videoKey(it) == key.videoId }
    if (cardIndex < 0) return false
    val outer = outerIndex(state.rows.shapes(), rowIndex, cardIndex, GRID_COLUMNS)
    if (listState.layoutInfo.visibleItemsInfo.none { it.index == outer }) listState.scrollToItem(outer)
    val rowState = rowStates[row.id] ?: return true
    if (rowState.layoutInfo.visibleItemsInfo.none { it.index == cardIndex }) rowState.scrollToItem(cardIndex)
    return true
}

@Composable
private fun FeedCard(state: BrowseState, actions: FeedActions, row: VideoRow, video: Video, index: Int, rowIndex: Int) {
    val id = videoKey(video)
    val key = remember(row.id, id) { FocusKey(row.id, id) }
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    val wanted = state.pendingFocus == key
    LaunchedEffect(wanted) {
        if (wanted) {
            requester.requestFocus()
            state.pendingFocus = null
        }
    }

    VideoCard(
        video = video,
        revision = row.revision,
        focused = focused,
        onClick = { actions.onVideoClicked(video) },
        onLongClick = { actions.onVideoLongClicked(video) },
        tone = Palette.Tones[(rowIndex + index) % Palette.Tones.size],
        modifier = Modifier
            .focusRequester(requester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    state.onCardFocused(key, rowIndex)
                    actions.onVideoFocused(video)
                    val margin = if (row.isGrid) GRID_COLUMNS * 2 else MORE_MARGIN
                    if (row.eager || index >= row.videos.size - margin) actions.onNeedMore(row)
                }
            },
    )
}
