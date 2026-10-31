package io.github.ivanosh.stmaterial.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.tv.R
import kotlinx.coroutines.delay
import io.github.ivanosh.stmaterial.logic.EditorState
import io.github.ivanosh.stmaterial.logic.moveBy
import io.github.ivanosh.stmaterial.logic.pick
import io.github.ivanosh.stmaterial.logic.reset
import io.github.ivanosh.stmaterial.logic.toggleList

enum class EditorKey { Up, Down, Left, Right, Ok, Back }

enum class Side { Shown, Hidden }

enum class EditorNotice { KeepOne, Displaced, Reset }

data class EditorCursor(val side: Side, val index: Int)

data class EditorUi<T>(val state: EditorState<T>, val defaults: EditorState<T>, val cursor: EditorCursor)

data class KeyResult<T>(val ui: EditorUi<T>, val displaced: T? = null, val notice: EditorNotice? = null, val done: Boolean = false)

/**
 * One remote key in the editor. OK picks up or puts down the item under the cursor, or presses Reset
 * when the cursor is on the row below the shown list. Up and Down move the cursor, or the picked item.
 * Left is the shown list and Right the hidden one: they move the cursor over, or carry the picked item.
 * Back finishes, and the caller saves [KeyResult.ui]'s state.
 */
fun <T> editorKey(ui: EditorUi<T>, key: EditorKey): KeyResult<T> {
    val state = ui.state
    val cursor = ui.cursor
    val picked = state.picked
    return when (key) {
        EditorKey.Back -> KeyResult(ui.copy(state = state.copy(picked = null)), done = true)
        EditorKey.Up, EditorKey.Down -> {
            val delta = if (key == EditorKey.Up) -1 else 1
            if (picked == null) {
                val last = if (cursor.side == Side.Shown) state.shown.size else state.hidden.size - 1
                KeyResult(ui.copy(cursor = cursor.copy(index = (cursor.index + delta).coerceIn(0, maxOf(last, 0)))))
            } else {
                val moved = state.moveBy(delta)
                KeyResult(ui.copy(state = moved, cursor = cursorOn(moved, picked, cursor)))
            }
        }
        EditorKey.Left, EditorKey.Right -> {
            val target = if (key == EditorKey.Left) Side.Shown else Side.Hidden
            when {
                picked == null -> {
                    val size = if (target == Side.Shown) state.shown.size else state.hidden.size
                    if (target == cursor.side || size == 0) KeyResult(ui)
                    else KeyResult(ui.copy(cursor = EditorCursor(target, cursor.index.coerceAtMost(size - 1))))
                }
                sideOf(state, picked) == target -> KeyResult(ui)
                else -> {
                    val result = state.toggleList()
                    when {
                        result.state == state -> KeyResult(ui, notice = EditorNotice.KeepOne)
                        else -> KeyResult(
                            ui.copy(state = result.state, cursor = cursorOn(result.state, picked, cursor)),
                            displaced = result.displaced,
                            notice = if (result.displaced != null) EditorNotice.Displaced else null,
                        )
                    }
                }
            }
        }
        EditorKey.Ok -> when {
            picked != null -> KeyResult(ui.copy(state = state.copy(picked = null)))
            cursor.side == Side.Shown && cursor.index == state.shown.size ->
                KeyResult(
                    ui.copy(state = state.reset(ui.defaults), cursor = EditorCursor(Side.Shown, 0)),
                    notice = EditorNotice.Reset,
                )
            else -> {
                val item = (if (cursor.side == Side.Shown) state.shown else state.hidden).getOrNull(cursor.index)
                if (item == null) KeyResult(ui) else KeyResult(ui.copy(state = state.pick(item)))
            }
        }
    }
}

private fun <T> sideOf(state: EditorState<T>, item: T): Side =
    if (item in state.shown) Side.Shown else Side.Hidden

/** The cursor follows [item] to wherever the edit put it. */
private fun <T> cursorOn(state: EditorState<T>, item: T, old: EditorCursor): EditorCursor {
    val shown = state.shown.indexOf(item)
    if (shown >= 0) return EditorCursor(Side.Shown, shown)
    val hidden = state.hidden.indexOf(item)
    return if (hidden >= 0) EditorCursor(Side.Hidden, hidden) else old
}

/** The words around the lists, which differ between editors. */
class ListEditorTexts(val title: String, val subtitle: String, val shownHeader: String, val hiddenHeader: String)

private const val ROWS = 8
private const val NOTICE_MS = 2500L
private val TitleStyle = TextStyle(fontSize = 44.sp, lineHeight = 54.sp, fontWeight = FontWeight.Medium)
private val SubtitleStyle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, color = Palette.OnBackgroundVariant)
private val HeaderStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)
private val MoreStyle = TextStyle(fontSize = 24.sp, color = Palette.OnBackgroundVariant)
private val RowStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)
private val TailStyle = TextStyle(fontSize = 24.sp)
private val RowShape = RoundedCornerShape(16.dp)
private val RowFill = Color(0xFF1D2024)
private val PickedText = Color(0xFF0B1D33)

private class Notice(val text: String)

/**
 * A two-list editor driven by the remote alone: [state] holds the shown and the hidden items.
 * [onChange] gets every edit, [onDone] the final state on Back. [defaults] is what Reset restores.
 * [icon] gives the 24x24 line-icon path of an item, if it has one.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun <T> ListEditor(
    state: EditorState<T>,
    defaults: EditorState<T>,
    texts: ListEditorTexts,
    label: (T) -> String,
    onChange: (EditorState<T>) -> Unit,
    onDone: (EditorState<T>) -> Unit,
    modifier: Modifier = Modifier,
    icon: (T) -> String? = { null },
    preview: @Composable () -> Unit = {},
) {
    var ui by remember { mutableStateOf(EditorUi(state, defaults, EditorCursor(Side.Shown, 0))) }
    var notice by remember { mutableStateOf<Notice?>(null) }
    var backPressed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.requestFocus()
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MS)
            notice = null
        }
    }

    fun press(key: EditorKey) {
        val before = ui.state
        val result = editorKey(ui, key)
        ui = result.ui
        if (result.ui.state != before) onChange(result.ui.state)
        notice = when (result.notice) {
            EditorNotice.KeepOne -> Notice(context.getString(R.string.material_editor_keep_one))
            EditorNotice.Reset -> Notice(context.getString(R.string.material_editor_was_reset))
            EditorNotice.Displaced -> result.displaced?.let {
                Notice(context.getString(R.string.material_editor_displaced, label(it), result.ui.state.limit))
            }
            null -> null
        }
        if (result.done) onDone(result.ui.state)
    }

    val picked = ui.state.picked
    val key = Modifier.onPreviewKeyEvent { event ->
        val code = event.nativeKeyEvent.keyCode
        if (event.key == Key.Back) {
            when (event.type) {
                KeyEventType.KeyDown -> backPressed = true
                KeyEventType.KeyUp -> if (backPressed) {
                    backPressed = false
                    press(EditorKey.Back)
                }
            }
            return@onPreviewKeyEvent true
        }
        val mapped = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> EditorKey.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> EditorKey.Down
            KeyEvent.KEYCODE_DPAD_LEFT -> EditorKey.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> EditorKey.Right
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> EditorKey.Ok
            else -> return@onPreviewKeyEvent false
        }
        // A held OK must not pick and drop again and again; the arrows may repeat.
        if (event.type == KeyEventType.KeyDown && (mapped != EditorKey.Ok || event.nativeKeyEvent.repeatCount == 0)) press(mapped)
        true
    }

    CompositionLocalProvider(LocalContentColor provides Palette.OnBackground) {
        Column(
            modifier
                .fillMaxSize()
                .background(Palette.Background)
                .padding(start = 58.dp, end = 58.dp, top = 40.dp, bottom = 32.dp)
                .then(key)
                .focusRequester(focus)
                .focusable(),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(texts.title, style = TitleStyle)
                    Text(texts.subtitle, style = SubtitleStyle)
                }
                preview()
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                val cursor = ui.cursor
                EditorColumn(
                    header = stringResource(R.string.material_editor_count, texts.shownHeader, ui.state.shown.size, ui.state.limit),
                    items = ui.state.shown,
                    active = cursor.side == Side.Shown,
                    cursorIndex = cursor.index,
                    picked = picked,
                    numbered = true,
                    label = label,
                    icon = icon,
                    resetSelected = cursor.side == Side.Shown && cursor.index == ui.state.shown.size,
                    modifier = Modifier.weight(1f),
                )
                EditorColumn(
                    header = "${texts.hiddenHeader} · ${ui.state.hidden.size}",
                    items = ui.state.hidden,
                    active = cursor.side == Side.Hidden,
                    cursorIndex = cursor.index,
                    picked = picked,
                    numbered = false,
                    label = label,
                    icon = icon,
                    resetSelected = null,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (picked != null) {
                        Hint("↑ ↓", stringResource(R.string.material_editor_hint_reorder))
                        Hint("← →", stringResource(R.string.material_editor_hint_move))
                        Hint("OK", stringResource(R.string.material_editor_hint_drop))
                    } else {
                        Hint("↑ ↓", stringResource(R.string.material_editor_hint_select))
                        Hint("← →", stringResource(R.string.material_editor_hint_list))
                        Hint("OK", stringResource(R.string.material_editor_hint_pick))
                        Hint("Back", stringResource(R.string.material_editor_hint_save))
                    }
                }
                notice?.let { Text(it.text, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Palette.Accent)) }
            }
        }
    }
}

@Composable
private fun Hint(keyName: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            keyName,
            style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Palette.OnBackground),
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Palette.TabSelected).padding(horizontal = 12.dp, vertical = 2.dp),
        )
        Text(text, style = TextStyle(fontSize = 24.sp, color = Palette.OnBackgroundVariant))
    }
}

/** [resetSelected] is null for a list without the Reset button, otherwise whether the cursor is on it. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun <T> EditorColumn(
    header: String,
    items: List<T>,
    active: Boolean,
    cursorIndex: Int,
    picked: T?,
    numbered: Boolean,
    label: (T) -> String,
    icon: (T) -> String?,
    resetSelected: Boolean?,
    modifier: Modifier,
) {
    // The window follows the cursor, with three rows above it where there are that many.
    val offset = if (active) (cursorIndex - 3).coerceIn(0, maxOf(0, items.size - ROWS)) else 0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(header, style = HeaderStyle)
            if (items.size > ROWS) Text(stringResource(R.string.material_editor_total, items.size), style = MoreStyle)
        }
        for (position in offset until minOf(items.size, offset + ROWS)) {
            val item = items[position]
            val current = active && cursorIndex == position
            val held = current && picked == item
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .graphicsLayer { if (current) { scaleX = 1.02f; scaleY = 1.02f } }
                    .drawRing(current)
                    .clip(RowShape)
                    .background(if (held) Palette.Accent else if (current) Palette.TabSelected else RowFill)
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (held) PickedText else Palette.OnBackground
                icon(item)?.let { StrokeIcon(it, color, Modifier.size(34.dp)) }
                Text(label(item), style = RowStyle.copy(color = color), maxLines = 1, modifier = Modifier.weight(1f))
                val tail = when {
                    held -> stringResource(R.string.material_editor_moving)
                    numbered -> (position + 1).toString()
                    else -> ""
                }
                Text(tail, style = TailStyle.copy(color = color))
            }
        }
        if (resetSelected != null) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .height(60.dp)
                    .drawRing(resetSelected, corner = 30.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(if (resetSelected) Palette.TabSelected else Color(0xFF272A2F))
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.material_editor_reset), style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

// The canvas ring, drawn outside the row it belongs to.
private fun Modifier.drawRing(on: Boolean, corner: androidx.compose.ui.unit.Dp = 16.dp): Modifier =
    focusRing(on, corner = corner, gap = 2.dp)
