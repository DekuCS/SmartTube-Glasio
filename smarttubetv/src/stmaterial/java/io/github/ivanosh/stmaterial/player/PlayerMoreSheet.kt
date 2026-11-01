package io.github.ivanosh.stmaterial.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.ButtonGroup
import io.github.ivanosh.stmaterial.logic.ButtonGroupKind
import io.github.ivanosh.stmaterial.logic.PlayerButton
import io.github.ivanosh.stmaterial.logic.buttonIsOn
import io.github.ivanosh.stmaterial.ui.Palette
import io.github.ivanosh.stmaterial.ui.StrokeIcon

private val PanelFill = Color(0xFF1D2024)
private val SheetTitle = TextStyle(fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Medium, color = Palette.OnBackground)
private val GroupStyle = TextStyle(fontSize = 26.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium, color = Palette.Accent)
private val RowStyle = TextStyle(fontSize = 26.sp, lineHeight = 34.sp)
private val ValueStyle = TextStyle(fontSize = 24.sp)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
private val RowShape = RoundedCornerShape(14.dp)
private const val ROWS_ABOVE_CURSOR = 3

/** One line of the sheet: a group's title, a button, or the last line that opens the editor. */
private sealed interface SheetLine {
    data class Title(val kind: ButtonGroupKind) : SheetLine
    data class Item(val button: PlayerButton, val entry: Int) : SheetLine
    data class Customize(val entry: Int) : SheetLine
}

private fun linesOf(groups: List<ButtonGroup>): List<SheetLine> {
    val lines = mutableListOf<SheetLine>()
    var entry = 0
    for (group in groups) {
        lines += SheetLine.Title(group.kind)
        for (button in group.buttons) lines += SheetLine.Item(button, entry++)
    }
    lines += SheetLine.Customize(entry)
    return lines
}

/**
 * More: a panel on the right edge in the look of the dialogs, with the buttons that are not on the player
 * panel in three groups and Customize buttons last. Draws only: [MaterialPlaybackActivity] holds the cursor.
 */
@Composable
fun BoxScope.MoreSheet(state: PlayerOverlayState) {
    val groups = state.sheetGroups ?: return
    val lines = remember(groups) { linesOf(groups) }
    val listState = rememberLazyListState()
    val cursor = state.sheetCursor
    LaunchedEffect(cursor, lines) {
        val at = lines.indexOfFirst { (it as? SheetLine.Item)?.entry == cursor || (it as? SheetLine.Customize)?.entry == cursor }
        // Near the top the first group's title stays in view.
        listState.scrollToItem(if (at <= ROWS_ABOVE_CURSOR + 1) 0 else at - ROWS_ABOVE_CURSOR)
    }
    Column(
        Modifier
            .align(Alignment.CenterEnd)
            .width(760.dp)
            .fillMaxHeight()
            .clip(SheetShape)
            .background(PanelFill)
            .padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.material_player_more), style = SheetTitle)
        LazyColumn(Modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(lines) { _, line ->
                when (line) {
                    is SheetLine.Title -> Text(
                        stringResource(groupTitle(line.kind)),
                        style = GroupStyle,
                        modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                    )
                    is SheetLine.Item -> SheetRow(
                        icon = buttonIcon(line.button),
                        label = stringResource(buttonLabel(line.button)),
                        // Repeat has seven modes and no short name for them, so it shows none.
                        value = if (line.button in STATEFUL_BUTTONS && line.button != PlayerButton.Repeat) {
                            val on = buttonIsOn(line.button, viewId(line.button)?.let { state.buttonStates[it] })
                            stringResource(if (on) R.string.material_state_on else R.string.material_state_off)
                        } else "",
                        focused = cursor == line.entry,
                    )
                    is SheetLine.Customize -> SheetRow(
                        icon = CUSTOMIZE_ICON_PATH,
                        label = stringResource(R.string.material_player_customize),
                        value = "",
                        focused = cursor == line.entry,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: String, label: String, value: String, focused: Boolean) {
    val color = if (focused) PanelFill else Palette.OnBackground
    Row(
        Modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RowShape)
            .background(if (focused) Palette.OnBackground else Color.Transparent)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        StrokeIcon(icon, color, Modifier.size(32.dp))
        Text(label, style = RowStyle.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, style = ValueStyle.copy(color = if (focused) PanelFill else Palette.OnBackgroundVariant))
    }
}
