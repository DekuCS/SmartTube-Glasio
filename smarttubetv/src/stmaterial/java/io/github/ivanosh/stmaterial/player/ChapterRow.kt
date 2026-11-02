package io.github.ivanosh.stmaterial.player

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.github.ivanosh.stmaterial.logic.PanelFocus
import io.github.ivanosh.stmaterial.logic.chapterIndexAt
import io.github.ivanosh.stmaterial.logic.formatPlayerTime
import io.github.ivanosh.stmaterial.ui.Palette
import io.github.ivanosh.stmaterial.ui.focusRing

private val CardWidth = 240.dp
private val CardThumbHeight = 135.dp
private val RowSide = 58.dp
private val RowGap = 20.dp
private val RingRoom = 8.dp
private val RowTop = 16.dp
private val ProgressHeight = 6.dp
private val cardTitle = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, color = Palette.OnBackground)
private val cardTime = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, color = Palette.OnBackgroundVariant)
private val plateTime = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, color = Palette.OnBackgroundVariant)

/**
 * The slot's height in layout, in the panel's column: nothing with the panel hidden, [PeekHeight] while the buttons are
 * in focus (the tops of the cards then hang over the panel's bottom margin and are cut by the screen), the whole row
 * when focus is in it. The content is as wide as the screen so that cards scroll out at its edge.
 */
private val PeekHeight = 40.dp

@Composable
fun ChapterRow(state: PlayerOverlayState) {
    // Nothing is composed, so no thumbnail is asked for, until the panel has been open for this video.
    if (state.chapters.isEmpty() || !state.chaptersRevealed) return
    val chapters = state.chapters
    val inRow by remember { derivedStateOf { state.focus == PanelFocus.CHAPTERS } }
    val spread = animateFloatAsState(if (inRow) 1f else 0f, tween(150), label = "chapterRow")
    val starts = remember(chapters) { chapters.map { it.startMs } }
    val current by remember(chapters) { derivedStateOf { chapterIndexAt(starts, state.positionMs) } }
    val list = rememberLazyListState()
    val focusedIndex = if (inRow) state.chapterIndex else -1
    LaunchedEffect(focusedIndex) {
        if (focusedIndex < 0) return@LaunchedEffect
        val shown = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusedIndex }
        val end = list.layoutInfo.viewportEndOffset
        val fits = shown != null && shown.offset >= list.layoutInfo.viewportStartOffset && shown.offset + shown.size <= end
        if (!fits) list.animateScrollToItem(focusedIndex)
    }
    Box(
        Modifier.layout { measurable, constraints ->
            val shown = state.layoutOpen
            val side = RowSide.roundToPx()
            val width = constraints.maxWidth + 2 * side
            val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
            val peek = PeekHeight.roundToPx()
            val height = if (shown) (peek + (placeable.height - peek) * spread.value).toInt() else 0
            layout(constraints.maxWidth, height) { placeable.place(IntOffset(-side, 0)) }
        }
    ) {
        Column(Modifier.padding(top = RowTop)) {
            LazyRow(
                state = list,
                contentPadding = PaddingValues(start = RowSide, end = RowSide, top = RingRoom, bottom = RingRoom),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(RowGap),
            ) {
                itemsIndexed(chapters) { index, chapter ->
                    ChapterCard(
                        chapter = chapter,
                        state = state,
                        index = index,
                        endMs = chapters.getOrNull(index + 1)?.startMs,
                        isCurrent = index == current,
                        focused = index == focusedIndex,
                    )
                }
            }
        }
    }
}

/** A thumbnail with the chapter's progress mark, the title in one line and the start time. */
@Composable
private fun ChapterCard(chapter: ChapterUi, state: PlayerOverlayState, index: Int, endMs: Long?, isCurrent: Boolean, focused: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val image = rememberChapterThumb(chapter.thumbUrl)
    Column(Modifier.width(CardWidth), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(CardWidth, CardThumbHeight)
                .focusRing(focused, corner = 12.dp, gap = 3.dp)
                .clip(shape)
                .background(Palette.Tile)
                .drawWithContent {
                    drawContent()
                    val bitmap = image.value
                    if (bitmap != null) {
                        drawImage(bitmap, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()))
                    }
                    if (isCurrent) {
                        // Read here, so that the position redraws this bar and composes nothing.
                        val end = endMs ?: state.durationMs
                        val span = (end - chapter.startMs).coerceAtLeast(1)
                        val fraction = ((state.positionMs - chapter.startMs).toFloat() / span).coerceIn(0f, 1f)
                        val bar = ProgressHeight.toPx()
                        drawRect(Palette.ProgressTrack, Offset(0f, size.height - bar), Size(size.width, bar))
                        drawRect(Palette.Accent, Offset(0f, size.height - bar), Size(size.width * fraction, bar))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (chapter.thumbUrl == null) Text(formatPlayerTime(chapter.startMs), style = plateTime)
        }
        Text(
            chapter.title,
            style = cardTitle.copy(color = if (isCurrent) Palette.Accent else Palette.OnBackground),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(formatPlayerTime(chapter.startMs), style = cardTime.copy(color = if (isCurrent) Palette.Accent else Palette.OnBackgroundVariant))
    }
}

/** Glide at the size of the card, for as long as the card is composed. The bitmap is read while drawing. */
@Composable
private fun rememberChapterThumb(url: String?): androidx.compose.runtime.State<ImageBitmap?> {
    val image = remember(url) { mutableStateOf<ImageBitmap?>(null) }
    if (url == null) return image
    val context = LocalContext.current.applicationContext
    val density = LocalDensity.current
    val width = with(density) { CardWidth.roundToPx() }
    val height = with(density) { CardThumbHeight.roundToPx() }
    DisposableEffect(url, width) {
        val glide = Glide.with(context)
        val target = object : CustomTarget<Bitmap>(width, height) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                image.value = resource.asImageBitmap()
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                image.value = null
            }
        }
        glide.asBitmap().load(url).centerCrop().into(target)
        onDispose {
            image.value = null
            glide.clear(target)
        }
    }
    return image
}
