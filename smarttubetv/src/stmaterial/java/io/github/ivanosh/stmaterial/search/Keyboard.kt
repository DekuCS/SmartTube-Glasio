package io.github.ivanosh.stmaterial.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.DIGIT_ROW
import io.github.ivanosh.stmaterial.logic.KeyboardLanguage
import io.github.ivanosh.stmaterial.ui.Palette
import io.github.ivanosh.stmaterial.ui.focusRing

private val KeyWidth = 112.dp
private val KeyHeight = 84.dp
private val KeyGap = 10.dp
private val KeyShape = RoundedCornerShape(16.dp)
private val KeyStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Medium)
private val ActionStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)

/** What the keys do. The screen owns the query. */
interface KeyboardActions {
    fun onCharacter(character: String)
    fun onSpace()
    fun onBackspace()
    fun onSearch()
    fun onLanguage()
}

/**
 * Digits, the letters of [language], and a row of Language, Space, Delete and Search. The first letter
 * takes [first] so that the screen can put focus there.
 */
@Composable
fun Keyboard(language: KeyboardLanguage, canSearch: Boolean, actions: KeyboardActions, first: FocusRequester, searchKey: FocusRequester) {
    Column(verticalArrangement = Arrangement.spacedBy(KeyGap), horizontalAlignment = Alignment.CenterHorizontally) {
        KeyRow(DIGIT_ROW.map(Char::toString), actions)
        language.rows.forEachIndexed { index, letters ->
            KeyRow(letters.map(Char::toString), actions, if (index == 0) first else null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
            Key(KeyWidth * 2 + KeyGap, actions::onLanguage) { LanguageLabel(language) }
            Key(KeyWidth * 4 + KeyGap * 3, actions::onSpace) { Text(stringResource(R.string.material_key_space), style = ActionStyle) }
            Key(KeyWidth * 2 + KeyGap, actions::onBackspace) { Text(stringResource(R.string.material_key_delete), style = ActionStyle) }
            Key(KeyWidth * 2 + KeyGap, { if (canSearch) actions.onSearch() }, Modifier.focusRequester(searchKey), accent = canSearch) {
                Text(stringResource(R.string.material_key_search), style = ActionStyle)
            }
        }
    }
}

@Composable
private fun KeyRow(characters: List<String>, actions: KeyboardActions, first: FocusRequester? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
        characters.forEachIndexed { index, character ->
            Key(
                KeyWidth,
                { actions.onCharacter(character) },
                if (index == 0 && first != null) Modifier.focusRequester(first) else Modifier,
            ) { Text(character, style = KeyStyle) }
        }
    }
}

/** Both layouts, the one in use bright: one key switches, and the owner sees what it switches to. */
@Composable
private fun LanguageLabel(language: KeyboardLanguage) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        KeyboardLanguage.entries.forEach { entry ->
            val color = if (entry == language) Palette.Accent else Palette.OnBackgroundVariant
            Text(entry.label, style = ActionStyle.copy(color = color))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Key(
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(KeyHeight)
            .focusRing(focused, corner = 16.dp, gap = 3.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(KeyShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) Palette.Accent else Palette.Tile,
            contentColor = if (accent) Palette.Background else Palette.OnBackground,
            focusedContainerColor = if (accent) Palette.Accent else Palette.Tile,
            focusedContentColor = if (accent) Palette.Background else Palette.OnBackground,
            pressedContainerColor = Palette.TabSelected,
            pressedContentColor = Palette.OnBackground,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) { content() }
    }
}
