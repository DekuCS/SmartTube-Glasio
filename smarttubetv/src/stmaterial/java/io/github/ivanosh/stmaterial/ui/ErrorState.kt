package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val MessageStyle = TextStyle(color = Palette.OnBackground, fontSize = 32.sp, lineHeight = 40.sp, textAlign = TextAlign.Center)
private val ButtonStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)
private val ButtonShape = RoundedCornerShape(32.dp)

/** A message and one button. Focus stays where it is until the user moves down to the button. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ErrorState(message: String, actionText: String, onAction: () -> Unit, onFocused: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 160.dp, vertical = 64.dp),
        verticalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MessageStyle, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Surface(
            onClick = onAction,
            modifier = Modifier
                .height(64.dp)
                .focusRing(focused, corner = 32.dp, gap = 2.dp)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
            shape = ClickableSurfaceDefaults.shape(ButtonShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Palette.TabSelected,
                contentColor = Palette.OnBackground,
                focusedContainerColor = Palette.TabSelected,
                focusedContentColor = Palette.OnBackground,
                pressedContainerColor = Palette.TabSelected,
                pressedContentColor = Palette.OnBackground
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        ) {
            Box(Modifier.fillMaxHeight().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
                Text(actionText, style = ButtonStyle)
            }
        }
    }
}

/** A line in the middle of the content area, for the states with nothing to focus. */
@Composable
fun CenteredMessage(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MessageStyle.copy(color = Palette.OnBackgroundVariant))
    }
}

/** A short line over the bottom of the screen: the confirmation of something that was done. */
@Composable
fun MessageLine(text: String, modifier: Modifier) {
    Text(
        text,
        style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Medium, color = Palette.OnBackground),
        modifier = modifier
            .padding(bottom = 48.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Palette.Tile)
            .padding(horizontal = 32.dp, vertical = 14.dp),
    )
}
