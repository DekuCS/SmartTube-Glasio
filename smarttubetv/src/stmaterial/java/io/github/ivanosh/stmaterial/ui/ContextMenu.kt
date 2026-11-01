package io.github.ivanosh.stmaterial.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

data class MenuItem(val label: String, val onClick: () -> Unit)

private val ItemStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium)
private val MenuShape = RoundedCornerShape(28.dp)
private val ItemShape = RoundedCornerShape(20.dp)
private val MenuFill = Palette.Tile
private const val SCRIM_ALPHA = 0.62f

private fun isOk(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER

/**
 * Calls [onLong] once when OK is held, and swallows that press, so that releasing the key
 * does not also click. A press that is shorter reaches the element as usual.
 */
fun Modifier.onLongOk(onLong: () -> Unit): Modifier = composed {
    var fired by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        val native = event.nativeKeyEvent
        if (!isOk(native.keyCode)) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> when {
                native.repeatCount > 0 || native.isLongPress -> {
                    if (!fired) {
                        fired = true
                        onLong()
                    }
                    true
                }
                else -> {
                    fired = false
                    false
                }
            }
            KeyEventType.KeyUp -> fired.also { fired = false }
            else -> false
        }
    }
}

/**
 * The menu of the canvas: a card under the tab bar over a dimmed screen. The first item has focus.
 * Back closes it. The OK press that opened it is ignored until the key is pressed again, so
 * releasing it does not choose the first item. Choosing an item runs it and closes the menu.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ContextMenu(
    items: List<MenuItem>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    val first = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) }
    var backPressed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.requestFocus()
    }
    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                when {
                    isOk(native.keyCode) -> {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0 && !native.isLongPress) armed = true
                        !armed || (event.type == KeyEventType.KeyDown && native.repeatCount > 0)
                    }
                    event.key == Key.Back -> {
                        when (event.type) {
                            KeyEventType.KeyDown -> backPressed = true
                            KeyEventType.KeyUp -> if (backPressed) {
                                backPressed = false
                                onDismiss()
                            }
                        }
                        true
                    }
                    // The menu is the only thing to reach: nothing to the sides, nothing above or below it.
                    native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || native.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> true
                    else -> false
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = 112.dp)
                .background(Palette.Background.copy(alpha = SCRIM_ALPHA))
        )
        Column(
            Modifier
                .align(if (alignEnd) Alignment.TopEnd else Alignment.TopStart)
                .padding(start = 58.dp, end = 58.dp, top = 116.dp)
                .width(560.dp)
                .clip(MenuShape)
                .background(MenuFill)
                .padding(16.dp)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.forEachIndexed { index, item ->
                Surface(
                    onClick = {
                        item.onClick()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(ItemShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Palette.OnBackground,
                        focusedContainerColor = Palette.OnBackground,
                        focusedContentColor = Palette.Background,
                        pressedContainerColor = Palette.OnBackground,
                        pressedContentColor = Palette.Background
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
                ) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
                        Text(item.label, style = ItemStyle)
                    }
                }
            }
        }
    }
}
