package io.github.ivanosh.stmaterial.browse

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import io.github.ivanosh.stmaterial.ui.ContextMenu
import io.github.ivanosh.stmaterial.ui.MenuItem
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.Sections
import io.github.ivanosh.stmaterial.ui.CenteredMessage
import io.github.ivanosh.stmaterial.ui.ErrorState
import io.github.ivanosh.stmaterial.ui.Feed
import io.github.ivanosh.stmaterial.ui.FeedPageActions
import io.github.ivanosh.stmaterial.ui.MessageLine
import io.github.ivanosh.stmaterial.ui.MaterialTvTheme
import io.github.ivanosh.stmaterial.ui.Palette
import io.github.ivanosh.stmaterial.ui.TabItem
import io.github.ivanosh.stmaterial.ui.Tabs

interface BrowseActions : FeedPageActions {
    fun onTabFocused(id: Int)
    fun onTabClicked(id: Int)
    fun onOpenFirst(id: Int)
    fun onHide(id: Int)
    fun onCustomize()
    fun onErrorAction(error: ErrorData)
}

private const val TABS_ANIMATION_MS = 200
private const val MESSAGE_MS = 3000L
private val TabsTravel = 132.dp

// The content starts below the bar and not under it: focus search takes a list as one rectangle first,
// and from a tab it only looks at what lies fully below. When the tabs leave, it slides up by FeedTravel,
// so it is that much taller than what is left of the screen.
private val ContentTop = 112.dp
private val FeedTravel = 80.dp

private fun Modifier.contentArea(): Modifier = layout { measurable, constraints ->
    val height = constraints.maxHeight - ContentTop.roundToPx() + FeedTravel.roundToPx()
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, ContentTop.roundToPx()) }
}

@Composable
fun BrowseScreen(state: BrowseState, actions: BrowseActions) {
    MaterialTvTheme {
        val requesters = remember { HashMap<Int, FocusRequester>() }
        val requesterFor = { id: Int -> requesters.getOrPut(id) { FocusRequester() } }
        var barFocused by remember { mutableStateOf(false) }
        var contentFocused by remember { mutableStateOf(false) }
        val repeatGuard = remember { KeyRepeatGuard() }
        var backPressed by remember { mutableStateOf(false) }
        // The tab whose menu is open, and the tab that gets focus back when it closes.
        var menuFor by remember { mutableStateOf<Int?>(null) }
        // The menu of a card. Focus comes back to the card through the state, as it does after any loss of focus.
        var cardMenu by remember { mutableStateOf<List<MenuItem>?>(null) }
        // The card that had focus when the menu opened. Focus falls to the tabs when the menu goes, and
        // that forgets the card, so the menu keeps it.
        var cardMenuOrigin by remember { mutableStateOf<FocusKey?>(null) }
        val feedActions = remember(actions) {
            object : BrowseActions by actions {
                override fun onVideoLongClicked(video: Video) {
                    val items = actions.videoMenuItems(video)
                    if (items.isNotEmpty()) {
                        cardMenuOrigin = state.focused
                        cardMenu = items
                    }
                }
            }
        }

        fun focusTabs() {
            val ids = state.tabs()
            val target = state.selected?.takeIf { it in ids || it == MediaGroup.TYPE_SETTINGS } ?: ids.firstOrNull() ?: return
            requesterFor(target).requestFocus()
        }

        // Nothing holds focus: the first frame, or what had it is gone (error replaced, card removed,
        // section cleared). A card takes it back if there is one, otherwise the selected tab.
        val orphaned = !barFocused && !contentFocused && menuFor == null && cardMenu == null
        val hasTabs = state.tabs().isNotEmpty()
        LaunchedEffect(orphaned, hasTabs) {
            if (!orphaned || !hasTabs) return@LaunchedEffect
            withFrameNanos { }
            val card = if (state.focused != null) state.restoreFocus() else null
            if (card != null) state.pendingFocus = card else focusTabs()
        }

        var menuClosedOn by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(menuFor) {
            val origin = menuClosedOn
            if (menuFor == null && origin != null) {
                menuClosedOn = null
                withFrameNanos { }
                if (origin == MediaGroup.TYPE_SETTINGS || origin in state.tabs()) requesterFor(origin).requestFocus() else focusTabs()
            }
        }
        LaunchedEffect(cardMenu) {
            val origin = cardMenuOrigin
            if (cardMenu == null && origin != null) {
                cardMenuOrigin = null
                withFrameNanos { }
                val card = state.restoreFocus(origin)
                if (card != null) state.pendingFocus = card else focusTabs()
            }
        }
        LaunchedEffect(state.message) {
            if (state.message != null) {
                delay(MESSAGE_MS)
                state.message = null
            }
        }

        // The presenter moved the selection (account change, say) while focus was on the bar.
        LaunchedEffect(state.selected) {
            if (barFocused) focusTabs()
        }

        val tabsAway = animateFloatAsState(if (state.tabsVisible) 0f else 1f, tween(TABS_ANIMATION_MS), label = "tabsAway")
        val searchTitle = stringResource(R.string.material_search)
        val tabs = state.tabs().map { id ->
            val title = if (id == Sections.SEARCH_SECTION_ID) searchTitle else state.sections.firstOrNull { it.id == id }?.title.orEmpty()
            TabItem(id, title, id == state.start)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Background)
                .onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    event.type == KeyEventType.KeyDown && isDpadMove(event.key) &&
                        repeatGuard.shouldDrop(native.repeatCount, native.eventTime, SystemClock.uptimeMillis())
                }
        ) {
            Box(
                Modifier
                    .contentArea()
                    .graphicsLayer { translationY = -FeedTravel.toPx() * tabsAway.value }
                    .onFocusChanged { contentFocused = it.hasFocus }
                    .onPreviewKeyEvent { event ->
                        // Back from the content goes to the tabs. Back on the tabs is the activity's.
                        // Only a press that began here counts: a screen that closes on the key down
                        // would leave its key up here.
                        if (event.key != Key.Back || !contentFocused) return@onPreviewKeyEvent false
                        when (event.type) {
                            KeyEventType.KeyDown -> backPressed = true
                            KeyEventType.KeyUp -> if (backPressed) {
                                backPressed = false
                                focusTabs()
                            }
                        }
                        true
                    }
            ) {
                key(state.selected) {
                    Content(state, feedActions)
                }
            }
            Tabs(
                tabs = tabs,
                selected = state.selected,
                settingsId = MediaGroup.TYPE_SETTINGS,
                settingsLabel = stringResource(R.string.material_settings),
                requesterFor = requesterFor,
                onFocused = {
                    state.onTabsFocused()
                    actions.onTabFocused(it)
                },
                onClick = actions::onTabClicked,
                onLongPress = { menuFor = it },
                modifier = Modifier
                    .onFocusChanged { barFocused = it.hasFocus }
                    .graphicsLayer { translationY = -TabsTravel.toPx() * tabsAway.value },
            )
            state.message?.let { MessageLine(it, Modifier.align(Alignment.BottomCenter)) }
            cardMenu?.let { items ->
                ContextMenu(items = items, onDismiss = { cardMenu = null })
            }
            menuFor?.let { id ->
                val openFirst = stringResource(R.string.material_menu_open_first)
                val hide = stringResource(R.string.material_menu_hide)
                val customize = stringResource(R.string.material_menu_customize)
                val items = buildList {
                    if (id != MediaGroup.TYPE_SETTINGS) {
                        add(MenuItem(openFirst) { actions.onOpenFirst(id) })
                        // The last tab cannot go: a screen without tabs has no way back to them.
                        if (id in state.shown && state.tabs().size > 1) add(MenuItem(hide) { actions.onHide(id) })
                    }
                    add(MenuItem(customize) { actions.onCustomize() })
                }
                ContextMenu(
                    items = items,
                    onDismiss = {
                        menuClosedOn = id
                        menuFor = null
                    },
                    alignEnd = id == MediaGroup.TYPE_SETTINGS,
                )
            }
        }
    }
}

@Composable
private fun Content(state: BrowseState, actions: BrowseActions) {
    val error = state.error
    when {
        state.selected == Sections.SEARCH_SECTION_ID -> CenteredMessage(stringResource(R.string.material_search_hint))
        !state.isEmpty -> Feed(state, actions)
        error != null -> ErrorState(
            message = error.message,
            actionText = error.actionText ?: stringResource(R.string.material_retry),
            onAction = { actions.onErrorAction(error) },
            onFocused = { state.onContentFocused(0) },
        )
        state.loading -> CenteredMessage(stringResource(R.string.material_loading))
        else -> CenteredMessage(stringResource(R.string.material_empty))
    }
}

private fun isDpadMove(key: Key): Boolean = when (key.nativeKeyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
    else -> false
}
