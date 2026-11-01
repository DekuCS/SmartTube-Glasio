package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
import io.github.ivanosh.stmaterial.browse.FocusKey
import kotlinx.coroutines.delay

/** What a screen of cards does with them, the long press included. */
interface FeedPageActions : FeedActions {
    /** What the long press on a card offers. Empty when nothing can be done with it. */
    fun videoMenuItems(video: Video): List<MenuItem>
}

private const val MESSAGE_MS = 3000L
private val TitleStyle = TextStyle(color = Palette.OnBackground, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium)

/**
 * A screen with a title over a [Feed] and no tabs: search results, a channel. The first card takes focus
 * when cards arrive and nothing has it; the long press on a card opens its menu, and focus goes back to
 * the card when the menu closes.
 */
@Composable
fun FeedPage(title: String?, state: BrowseState, actions: FeedPageActions) {
    var contentFocused by remember { mutableStateOf(false) }
    var cardMenu by remember { mutableStateOf<List<MenuItem>?>(null) }
    // Focus falls away when the menu goes, and that forgets the card, so the menu keeps it.
    var cardMenuOrigin by remember { mutableStateOf<FocusKey?>(null) }
    val feedActions = remember(actions) {
        object : FeedPageActions by actions {
            override fun onVideoLongClicked(video: Video) {
                val items = actions.videoMenuItems(video)
                if (items.isNotEmpty()) {
                    cardMenuOrigin = state.focused
                    cardMenu = items
                }
            }
        }
    }

    val orphaned = !contentFocused && cardMenu == null
    val hasCards = !state.isEmpty
    LaunchedEffect(orphaned, hasCards) {
        if (!orphaned || !hasCards) return@LaunchedEffect
        withFrameNanos { }
        state.restoreFocus()?.let { state.pendingFocus = it }
    }
    LaunchedEffect(cardMenu) {
        val origin = cardMenuOrigin
        if (cardMenu == null && origin != null) {
            cardMenuOrigin = null
            withFrameNanos { }
            state.restoreFocus(origin)?.let { state.pendingFocus = it }
        }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MS)
            state.message = null
        }
    }

    MaterialTvTheme {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(PAGE_TITLE_HEIGHT).padding(start = 58.dp, end = 58.dp), contentAlignment = Alignment.CenterStart) {
                    Text(title.orEmpty(), style = TitleStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.fillMaxSize().onFocusChanged { contentFocused = it.hasFocus }) {
                    when {
                        hasCards -> Feed(state, feedActions)
                        state.loading -> CenteredMessage(stringResource(R.string.material_loading))
                        else -> CenteredMessage(stringResource(R.string.material_empty))
                    }
                }
            }
            state.message?.let { MessageLine(it, Modifier.align(Alignment.BottomCenter)) }
            cardMenu?.let { items -> ContextMenu(items = items, onDismiss = { cardMenu = null }) }
        }
    }
}

/** The menu of a card opens under this line, as under the tab bar of the home screen. */
val PAGE_TITLE_HEIGHT = 112.dp
