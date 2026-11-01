package io.github.ivanosh.stmaterial.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup
import com.liskovsoft.smartyoutubetv2.common.app.models.search.MediaServiceSearchTagProvider
import com.liskovsoft.smartyoutubetv2.common.app.models.search.vineyard.Tag
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.browse.FeedActivity
import io.github.ivanosh.stmaterial.logic.KeyboardLanguage
import io.github.ivanosh.stmaterial.logic.SearchInput
import io.github.ivanosh.stmaterial.logic.cleanSuggestions
import io.github.ivanosh.stmaterial.logic.isCurrentAnswer
import io.github.ivanosh.stmaterial.ui.FeedPage
import io.github.ivanosh.stmaterial.ui.MaterialTvTheme
import io.github.ivanosh.stmaterial.ui.PAGE_TITLE_HEIGHT
import io.github.ivanosh.stmaterial.ui.Palette
import io.github.ivanosh.stmaterial.ui.focusRing
import io.github.ivanosh.stmaterial.ui.onLongOk
import kotlinx.coroutines.delay

/**
 * Search: an on-screen keyboard and the query, suggestions under it, then the results as cards. Back from
 * the results returns to the keyboard; Back from the keyboard leaves. The searching stays in [SearchPresenter];
 * this implements [SearchView]. There is no voice search, and no filters.
 */
class MaterialSearchActivity : FeedActivity<SearchPresenter>(), SearchView {
    override val presenter: SearchPresenter by lazy { SearchPresenter.instance(this) }
    private var input by mutableStateOf(SearchInput())
    private var language by mutableStateOf(KeyboardLanguage.EN)
    private var showingResults by mutableStateOf(false)
    private var suggestions by mutableStateOf<List<Tag>>(emptyList())
    private var tagsProvider: MediaServiceSearchTagProvider? = null
    private var searchKeyWanted by mutableStateOf(false)

    override fun connect() {
        presenter.setView(this)
    }

    private val keys = object : KeyboardActions {
        override fun onCharacter(character: String) { input = input.type(character) }
        override fun onSpace() { input = input.space() }
        override fun onBackspace() { input = input.backspace() }
        override fun onSearch() = submit(input.query)
        override fun onLanguage() { language = language.next() }
    }

    private fun submit(query: String?) {
        if (query == null) return
        showingResults = true
        presenter.onSearch(query)
    }

    @Composable
    override fun Screen() {
        MaterialTvTheme {
            if (showingResults) {
                FeedPage(input.query, state, feedActions)
            } else {
                KeyboardScreen()
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun KeyboardScreen() {
        val first = remember { FocusRequester() }
        val searchKey = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            // Back from the results lands on Search, which the next thing to do is.
            (if (searchKeyWanted) searchKey else first).requestFocus()
            searchKeyWanted = false
        }
        // Suggestions follow the query; a key held down asks once the typing pauses.
        LaunchedEffect(input.text) {
            delay(SUGGESTIONS_DELAY_MS)
            val asked = input.text
            tagsProvider?.search(asked) { tags ->
                // Answers come late and in any order; one for an earlier query must not replace the current ones.
                if (!isCurrentAnswer(asked, input.text)) return@search
                val all = tags.orEmpty()
                suggestions = cleanSuggestions(all.map { it.tag }, SUGGESTIONS_LIMIT).mapNotNull { name -> all.firstOrNull { it.tag == name } }
            }
        }
        Column(Modifier.fillMaxSize().background(Palette.Background), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(PAGE_TITLE_HEIGHT).fillMaxWidth().padding(horizontal = 58.dp), contentAlignment = Alignment.CenterStart) {
                Text(stringResource(R.string.material_search_title), style = TitleStyle)
            }
            QueryField(input.text)
            Box(Modifier.height(88.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Suggestions()
            }
            Keyboard(language, input.canSearch, keys, first, searchKey)
        }
    }

    @Composable
    private fun QueryField(text: String) {
        Box(
            Modifier
                .padding(horizontal = 58.dp)
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Palette.Tile)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) {
                Text(stringResource(R.string.material_search_placeholder), style = QueryStyle.copy(color = Palette.OnBackgroundVariant))
            } else {
                // The bar is the caret: a hardware keyboard is not expected, so the end of the text is the only place.
                Text("$text│", style = QueryStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun Suggestions() {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 58.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(suggestions, key = { _, tag -> tag.tag ?: "" }) { _, tag ->
                var focused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { tag.tag?.let { input = SearchInput(it.take(input.limit)); submit(input.query) } },
                    modifier = Modifier
                        .height(64.dp)
                        .focusRing(focused, corner = 32.dp, gap = 3.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .onLongOk { presenter.onTagLongClicked(tag) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(32.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Palette.Tile,
                        contentColor = Palette.OnBackground,
                        focusedContainerColor = Palette.Tile,
                        focusedContentColor = Palette.OnBackground,
                        pressedContainerColor = Palette.TabSelected,
                        pressedContentColor = Palette.OnBackground,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                ) {
                    Box(Modifier.height(64.dp).padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
                        Text(tag.tag.orEmpty(), style = ChipStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    /** Back from the results: the keyboard, with the query as it was. */
    override fun finish() {
        if (isBackPressed() && showingResults) {
            resetBackState()
            presenter.disposeActions()
            clearFeed()
            searchKeyWanted = true
            showingResults = false
            return
        }
        super.finish()
    }

    // SearchView

    override fun updateSearch(group: VideoGroup) = updateFeed(group)

    override fun clearSearch() = clearFeed()

    override fun clearSearchTags() {
        suggestions = emptyList()
    }

    override fun removeSearchTag(tag: Tag) {
        suggestions = suggestions - tag
    }

    override fun setTagsProvider(provider: MediaServiceSearchTagProvider) {
        tagsProvider = provider
    }

    override fun showProgressBar(show: Boolean) {
        state.loading = show
    }

    /** With a text, the results at once (a search started from the player, a suggestion); without, the keyboard. */
    override fun startSearch(searchText: String?) {
        if (searchText.isNullOrBlank()) {
            if (showingResults) {
                presenter.disposeActions()
                clearFeed()
            }
            input = SearchInput()
            showingResults = false
            return
        }
        input = SearchInput(searchText.take(input.limit))
        submit(input.query)
    }

    override fun getSearchText(): String = input.text

    /** Voice search is not part of this app. */
    override fun startVoiceRecognition() = Unit

    private companion object {
        const val SUGGESTIONS_DELAY_MS = 250L
        const val SUGGESTIONS_LIMIT = 8
        val TitleStyle = TextStyle(color = Palette.OnBackground, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Medium)
        val QueryStyle = TextStyle(color = Palette.OnBackground, fontSize = 40.sp, lineHeight = 48.sp)
        val ChipStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}
