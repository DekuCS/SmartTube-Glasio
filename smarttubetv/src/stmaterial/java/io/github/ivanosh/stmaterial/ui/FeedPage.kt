package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.browse.BrowseState

/** What a screen of cards does with them, the long press included. */
interface FeedPageActions : FeedActions {
    /** What the long press on a card offers. Empty when nothing can be done with it. */
    fun videoMenuItems(video: Video): List<MenuItem>
}

private val TitleStyle = TextStyle(color = Palette.OnBackground, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium)

/**
 * A screen with a title over a [Feed] and no tabs: search results, a channel. The first card takes focus
 * when cards arrive and nothing has it; the long press on a card opens its menu, and focus goes back to
 * the card when the menu closes.
 */
@Composable
fun FeedPage(title: String?, state: BrowseState, actions: FeedPageActions, pill: PillState? = null) {
    var contentFocused by remember { mutableStateOf(false) }
    var pillFocused by remember { mutableStateOf(false) }
    val menu = rememberCardMenuHost(state, actions)
    val hasCards = !state.isEmpty
    CardFocusEffects(state, menu, orphaned = !contentFocused && !pillFocused && menu.items == null, hasTargets = hasCards) {
        state.restoreFocus()?.let { state.pendingFocus = it }
    }

    // The pill is the first thing that can take focus, so it has it before any card exists. The cards are the
    // way in: when they arrive and none has been focused yet, the first one takes focus from the pill.
    LaunchedEffect(hasCards) {
        if (hasCards && pill != null && state.focused == null) {
            withFrameNanos { }
            state.restoreFocus()?.let { state.pendingFocus = it }
        }
    }

    MaterialTvTheme {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(PAGE_TITLE_HEIGHT).padding(start = 58.dp, end = 58.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Text(title.orEmpty(), style = TitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (pill != null) {
                        SubscribePill(
                            pill,
                            Modifier
                                .onFocusChanged { pillFocused = it.hasFocus }
                                .onPreviewKeyEvent { event ->
                                    // Down returns to the card focus came from, not to the card nearest the pill.
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                        val card = state.restoreFocus()
                                        if (card != null) state.pendingFocus = card
                                        card != null
                                    } else false
                                },
                        )
                    }
                }
                Box(Modifier.fillMaxSize().onFocusChanged { contentFocused = it.hasFocus }) {
                    when {
                        hasCards -> Feed(state, menu.feedActions)
                        state.loading -> CenteredMessage(stringResource(R.string.material_loading))
                        else -> CenteredMessage(stringResource(R.string.material_empty))
                    }
                }
            }
            state.message?.let { MessageLine(it, Modifier.align(Alignment.BottomCenter)) }
            menu.items?.let { items -> ContextMenu(items = items, onDismiss = menu::dismiss) }
        }
    }
}

/** The menu of a card opens under this line, as under the tab bar of the home screen. */
val PAGE_TITLE_HEIGHT = 112.dp
