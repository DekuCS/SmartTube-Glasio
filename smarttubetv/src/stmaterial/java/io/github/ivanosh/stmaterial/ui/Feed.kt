package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import io.github.ivanosh.stmaterial.browse.BrowseState
import io.github.ivanosh.stmaterial.browse.FocusKey
import io.github.ivanosh.stmaterial.browse.VideoRow
import io.github.ivanosh.stmaterial.browse.videoKey

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
private class AnchoredSpec(private val target: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - target
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
    val spec = remember(density, cardTop) { AnchoredSpec(with(density) { cardTop.toPx() }) }
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

/** Items a row takes in the vertical list: one for a row, a heading (if titled) and the lines for a grid. */
private fun itemCount(row: VideoRow): Int =
    if (row.isGrid) (if (row.title != null) 1 else 0) + (row.videos.size + GRID_COLUMNS - 1) / GRID_COLUMNS else 1

/** Index in the vertical list of the item that holds the card, which is also what a card reports as its row. */
private fun outerIndex(rows: List<VideoRow>, rowIndex: Int, cardIndex: Int): Int {
    val before = (0 until rowIndex).sumOf { itemCount(rows[it]) }
    val row = rows[rowIndex]
    return if (row.isGrid) before + (if (row.title != null) 1 else 0) + cardIndex / GRID_COLUMNS else before
}

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
        if (gridOnly) PaddingValues(bottom = 400.dp) else PaddingValues(top = ListTopPadding, bottom = 400.dp),
        spacing = if (gridOnly) 44.dp - CardBleed * 2 else 44.dp - CardBleed,
    ) {
        var first = 0
        rows.forEachIndexed { rowIndex, row ->
            val start = first
            first += itemCount(row)
            if (row.isGrid) {
                gridItems(state, actions, row, start)
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

/** A grid cut into lines of [GRID_COLUMNS], under a heading if it has a title. [first] is the index of its first item. */
private fun LazyListScope.gridItems(state: BrowseState, actions: FeedActions, row: VideoRow, first: Int) {
    val headed = row.title != null
    if (headed) {
        item(key = "${row.id}:heading", contentType = "heading") {
            Column {
                RowHeading(row.title)
                Spacer(Modifier.height(18.dp - CardBleed))
            }
        }
    }
    val lines = (row.videos.size + GRID_COLUMNS - 1) / GRID_COLUMNS
    val firstLine = first + (if (headed) 1 else 0)
    items(lines, key = { "${row.id}:line$it" }, contentType = { "line" }) { line ->
        Row(
            Modifier.padding(start = SidePadding, top = CardBleed, bottom = CardBleed),
            horizontalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            val end = minOf((line + 1) * GRID_COLUMNS, row.videos.size)
            for (index in line * GRID_COLUMNS until end) {
                val video = row.videos[index]
                key(videoKey(video)) {
                    FeedCard(state, actions, row, video, index, firstLine + line)
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
    val outer = outerIndex(state.rows, rowIndex, cardIndex)
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
                    if (index >= row.videos.size - margin) actions.onNeedMore(row)
                }
            },
    )
}

/** The tiles of the old Settings section. Each opens its old dialog; Task 12 replaces this. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsGrid(state: BrowseState) {
    val items = state.settings.toList()
    val lines = items.chunked(GRID_COLUMNS)
    FeedColumn(
        state, GridCardTop, rememberLazyListState(),
        PaddingValues(bottom = 400.dp),
        spacing = 32.dp - CardBleed * 2,
    ) {
        itemsIndexed(lines, key = { line, _ -> line }) { line, tiles ->
            Row(
                Modifier.padding(start = SidePadding, top = CardBleed, bottom = CardBleed),
                horizontalArrangement = Arrangement.spacedBy(CardGap),
            ) {
                tiles.forEach { item -> SettingsTile(item) { state.onContentFocused(line) } }
            }
        }
    }
}

private val TileShape = RoundedCornerShape(16.dp)
private val TileStyle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsTile(item: SettingsItem, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { item.onClick?.run() },
        modifier = Modifier
            .size(400.dp, 120.dp)
            .focusRing(focused, corner = 16.dp, gap = 4.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = ClickableSurfaceDefaults.shape(TileShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Palette.Tile,
            contentColor = Palette.OnBackground,
            focusedContainerColor = Palette.Tile,
            focusedContentColor = Palette.OnBackground,
            pressedContainerColor = Palette.Tile,
            pressedContentColor = Palette.OnBackground
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
            Text(item.title.orEmpty(), style = TileStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
