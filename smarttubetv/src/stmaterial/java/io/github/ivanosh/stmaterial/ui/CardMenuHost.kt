package io.github.ivanosh.stmaterial.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import io.github.ivanosh.stmaterial.browse.BrowseState
import io.github.ivanosh.stmaterial.browse.FocusKey
import kotlinx.coroutines.delay

private const val MESSAGE_MS = 3000L

/**
 * The menu of a card, for every screen of cards: the long press opens it, and [dismiss] closes it.
 * The card that had focus is kept, because focus falls away when the menu goes and that forgets the card.
 */
class CardMenuHost(private val state: BrowseState, actions: FeedPageActions) {
    var items by mutableStateOf<List<MenuItem>?>(null)
        private set
    internal var origin: FocusKey? = null

    /** [actions] with the long press taken over. */
    val feedActions: FeedPageActions = object : FeedPageActions by actions {
        override fun onVideoLongClicked(video: Video) {
            val menu = actions.videoMenuItems(video)
            if (menu.isNotEmpty()) {
                origin = state.focused
                items = menu
            }
        }
    }

    fun dismiss() {
        items = null
    }
}

@Composable
fun rememberCardMenuHost(state: BrowseState, actions: FeedPageActions): CardMenuHost =
    remember(state, actions) { CardMenuHost(state, actions) }

/**
 * Gives focus back where it belongs: when nothing holds it ([orphaned]: the first frame, or what had it is
 * gone) and there is something to focus ([hasTargets]), and when the card menu closes. A card takes it if
 * there is one to take; otherwise [fallback] decides. Also clears the confirmation line after a while.
 */
@Composable
fun CardFocusEffects(state: BrowseState, host: CardMenuHost, orphaned: Boolean, hasTargets: Boolean, fallback: () -> Unit) {
    LaunchedEffect(orphaned, hasTargets) {
        if (!orphaned || !hasTargets) return@LaunchedEffect
        withFrameNanos { }
        val card = if (state.focused != null) state.restoreFocus() else null
        if (card != null) state.pendingFocus = card else fallback()
    }
    LaunchedEffect(host.items) {
        val origin = host.origin
        if (host.items == null && origin != null) {
            host.origin = null
            withFrameNanos { }
            val card = state.restoreFocus(origin)
            if (card != null) state.pendingFocus = card else fallback()
        }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_MS)
            state.message = null
        }
    }
}
