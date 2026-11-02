package io.github.ivanosh.stmaterial.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.liskovsoft.smartyoutubetv2.tv.R
import io.github.ivanosh.stmaterial.logic.ButtonMenu
import io.github.ivanosh.stmaterial.logic.MenuAction
import io.github.ivanosh.stmaterial.ui.ItemShape
import io.github.ivanosh.stmaterial.ui.ItemStyle
import io.github.ivanosh.stmaterial.ui.MenuFill
import io.github.ivanosh.stmaterial.ui.MenuShape
import io.github.ivanosh.stmaterial.ui.Palette
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color

private fun label(action: MenuAction): Int = when (action) {
    MenuAction.OpenChannel -> R.string.material_menu_open_channel
    MenuAction.Move -> R.string.material_menu_move
    MenuAction.Remove -> R.string.material_menu_remove
    MenuAction.Customize -> R.string.material_player_customize
    MenuAction.AddToPanel -> R.string.material_menu_add
}

/**
 * The menu of a hold on a button, in the look of the app's context menu. Draws only: [MaterialPlaybackActivity] holds the
 * cursor and the keys, since the overlay takes no focus. Composed only while a menu is open.
 */
@Composable
internal fun ButtonMenuCard(menu: ButtonMenu) {
    Column(
        Modifier
            .width(420.dp)
            .clip(MenuShape)
            .background(MenuFill)
            .border(2.dp, Palette.ProgressTrack, MenuShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        menu.actions.forEachIndexed { index, action ->
            val focused = index == menu.cursor
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(ItemShape)
                    .background(if (focused) Palette.OnBackground else Color.Transparent)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    stringResource(label(action)),
                    style = ItemStyle.copy(color = if (focused) Palette.Background else Palette.OnBackground),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
