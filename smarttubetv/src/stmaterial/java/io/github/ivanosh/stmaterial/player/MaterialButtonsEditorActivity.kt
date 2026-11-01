package io.github.ivanosh.stmaterial.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.data.ButtonStore
import io.github.ivanosh.stmaterial.logic.DEFAULT_BUTTONS
import io.github.ivanosh.stmaterial.logic.EditorState
import io.github.ivanosh.stmaterial.logic.PlayerButton
import io.github.ivanosh.stmaterial.logic.buttonsEditorState
import io.github.ivanosh.stmaterial.ui.ListEditor
import io.github.ivanosh.stmaterial.ui.ListEditorTexts
import io.github.ivanosh.stmaterial.ui.MaterialTvTheme
import io.github.ivanosh.stmaterial.ui.Palette

/**
 * Edits which buttons are on the player panel and in what order. Back saves and returns to the player,
 * which reads the result when it resumes. The window is translucent, as the dialogs', so the player stays
 * under it and keeps playing.
 */
class MaterialButtonsEditorActivity : ComponentActivity() {
    private lateinit var store: ButtonStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ButtonStore(this)
        val initial = store.load()
        setContentView(ComposeView(this).apply {
            setContent {
                MaterialTvTheme {
                    // The preview follows every edit, so it reads what the editor last reported.
                    var shown by remember { mutableStateOf(initial.shown) }
                    ListEditor(
                        state = initial,
                        defaults = buttonsEditorState(DEFAULT_BUTTONS),
                        texts = ListEditorTexts(
                            title = stringResource(R.string.material_player_customize),
                            subtitle = stringResource(R.string.material_buttons_subtitle),
                            shownHeader = stringResource(R.string.material_buttons_on_panel),
                            hiddenHeader = stringResource(R.string.material_buttons_in_more),
                            keepOne = stringResource(R.string.material_buttons_keep_one),
                            wasReset = stringResource(R.string.material_buttons_was_reset),
                            displaced = { label, limit -> getString(R.string.material_buttons_displaced, label, limit) },
                        ),
                        label = { getString(buttonLabel(it)) },
                        icon = ::buttonIcon,
                        onChange = { shown = it.shown },
                        onDone = ::save,
                        preview = { BarPreview(shown) },
                    )
                }
            }
        })
        window.setBackgroundDrawable(null)
    }

    private fun save(state: EditorState<PlayerButton>) {
        store.save(state)
        finish()
    }
}

/** The bar as the player shows it: the buttons in order and More at the end. */
@Composable
private fun BarPreview(shown: List<PlayerButton>) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.Tile)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val label = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, color = Palette.OnBackground)
        for (button in shown) {
            PanelButton(
                label = stringResource(buttonLabel(button)),
                iconPath = buttonIcon(button),
                focused = false,
                on = false,
                size = 56.dp,
                labelStyle = label,
            )
        }
        PanelButton(stringResource(R.string.material_player_more), MORE_ICON_PATH, focused = false, on = false, size = 56.dp, labelStyle = label)
    }
}
