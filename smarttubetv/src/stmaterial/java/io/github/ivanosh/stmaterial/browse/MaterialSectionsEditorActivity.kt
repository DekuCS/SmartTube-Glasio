package io.github.ivanosh.stmaterial.browse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.data.SectionStore
import io.github.ivanosh.stmaterial.logic.EditorState
import io.github.ivanosh.stmaterial.logic.Sections
import io.github.ivanosh.stmaterial.ui.ListEditor
import io.github.ivanosh.stmaterial.ui.ListEditorTexts
import io.github.ivanosh.stmaterial.ui.MaterialTvTheme

/** Edits which sections are tabs and in what order. Back saves and returns to the browse screen. */
class MaterialSectionsEditorActivity : ComponentActivity() {
    private lateinit var store: SectionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SectionStore(this)
        val initial = store.load()
        val defaults = EditorState(Sections.DEFAULT_SHOWN, Sections.HIDDEN_ADDABLE, emptySet(), Sections.LIMIT)
        setContentView(ComposeView(this).apply {
            setContent {
                MaterialTvTheme {
                    ListEditor(
                        state = initial,
                        defaults = defaults,
                        texts = ListEditorTexts(
                            title = stringResource(R.string.material_menu_customize),
                            subtitle = stringResource(R.string.material_editor_subtitle),
                            shownHeader = stringResource(R.string.material_editor_in_menu),
                            hiddenHeader = stringResource(R.string.material_editor_hidden),
                        ),
                        label = { getString(sectionLabel(it)) },
                        icon = ::sectionIcon,
                        onChange = {},
                        onDone = ::save,
                    )
                }
            }
        })
        window.setBackgroundDrawable(null)
    }

    /** The browse screen reads the result when it resumes, so the editor closes only once it is stored. */
    private fun save(state: EditorState<Int>) {
        store.saveAsync(state) { runOnUiThread { finish() } }
    }
}

internal fun sectionLabel(id: Int): Int = when (id) {
    MediaGroup.TYPE_SUBSCRIPTIONS -> R.string.material_section_subscriptions
    MediaGroup.TYPE_HOME -> R.string.material_section_home
    MediaGroup.TYPE_HISTORY -> R.string.material_section_history
    MediaGroup.TYPE_USER_PLAYLISTS -> R.string.material_section_playlists
    Sections.SEARCH_SECTION_ID -> R.string.material_search
    MediaGroup.TYPE_TRENDING -> R.string.material_section_trending
    MediaGroup.TYPE_LIVE -> R.string.material_section_live
    MediaGroup.TYPE_MY_VIDEOS -> R.string.material_section_my_videos
    MediaGroup.TYPE_GAMING -> R.string.material_section_gaming
    MediaGroup.TYPE_NEWS -> R.string.material_section_news
    MediaGroup.TYPE_MUSIC -> R.string.material_section_music
    MediaGroup.TYPE_SPORTS -> R.string.material_section_sports
    MediaGroup.TYPE_PLAYBACK_QUEUE -> R.string.material_section_queue
    MediaGroup.TYPE_NOTIFICATIONS -> R.string.material_section_notifications
    MediaGroup.TYPE_BLOCKED_CHANNELS -> R.string.material_section_blocked
    MediaGroup.TYPE_KIDS_HOME -> R.string.material_section_kids
    else -> R.string.material_empty
}

/** The 24x24 line icons of the canvas. */
internal fun sectionIcon(id: Int): String? = when (id) {
    MediaGroup.TYPE_HOME -> "M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10"
    MediaGroup.TYPE_SUBSCRIPTIONS -> "M4 8h16v12H4zM7 5h10M10 11l5 3-5 3z"
    MediaGroup.TYPE_HISTORY -> "M12 7v5l3 2M3.5 12a8.5 8.5 0 1 0 2.5-6M3 4v4h4"
    MediaGroup.TYPE_USER_PLAYLISTS -> "M4 6h16M4 12h10M4 18h10M17 13v7l4-3.5z"
    Sections.SEARCH_SECTION_ID -> "M10.5 3a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15zM16 16l5 5"
    MediaGroup.TYPE_TRENDING -> "M3 17l6-6 4 4 8-8M15 7h6v6"
    MediaGroup.TYPE_KIDS_HOME -> "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM8.5 14c1 1.5 2.2 2 3.5 2s2.5-.5 3.5-2M9 9.5v.5M15 9.5v.5"
    MediaGroup.TYPE_SPORTS -> "M8 4h8v6a4 4 0 0 1-8 0zM8 6H4c0 3 1.5 5 4 5M16 6h4c0 3-1.5 5-4 5M12 14v4M8 20h8"
    MediaGroup.TYPE_LIVE -> "M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4zM7 7a7 7 0 0 0 0 10M17 7a7 7 0 0 1 0 10M4 4a11 11 0 0 0 0 16M20 4a11 11 0 0 1 0 16"
    MediaGroup.TYPE_MY_VIDEOS -> "M4 5h16v14H4zM10 9l5 3-5 3z"
    MediaGroup.TYPE_GAMING -> "M7 8h10a5 5 0 0 1 0 10l-3-2h-4l-3 2A5 5 0 0 1 7 8zM8 12v3M6.5 13.5h3M16 12.5v.5M18 14.5v.5"
    MediaGroup.TYPE_NEWS -> "M4 5h13v15H6a2 2 0 0 1-2-2zM17 9h3v9a2 2 0 0 1-2 2M7 9h7M7 13h7M7 16h4"
    MediaGroup.TYPE_MUSIC -> "M9 18V6l11-2v12M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0zM20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"
    MediaGroup.TYPE_BLOCKED_CHANNELS -> "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM6 6l12 12"
    MediaGroup.TYPE_NOTIFICATIONS -> "M6 16V11a6 6 0 1 1 12 0v5l2 2H4zM10 21h4"
    MediaGroup.TYPE_PLAYBACK_QUEUE -> "M4 6h12M4 11h12M4 16h7M15 15l6 3.5-6 3.5z"
    else -> null
}
