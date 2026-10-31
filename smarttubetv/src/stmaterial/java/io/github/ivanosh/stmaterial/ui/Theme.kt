package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Colours of the canvas. */
object Palette {
    val Background = Color(0xFF111318)
    val OnBackground = Color(0xFFE2E2E9)
    val OnBackgroundVariant = Color(0xFFC4C6D0)
    val Accent = Color(0xFFA8C7FA)
    val TabSelected = Color(0xFF32353A)
    val ProgressTrack = Color(0xFF44474E)
    val Tile = Color(0xFF282A2F)
    val FocusRing = Color(0xFFFFFFFF)
    val Tones = listOf(
        Color(0xFF2B3A4A), Color(0xFF3A3346), Color(0xFF2F3F3A), Color(0xFF40382E), Color(0xFF3B2F36)
    )
}

private const val CANVAS_WIDTH = 1920f

/** Every size in the UI is a canvas pixel: 1.dp and 1.sp are 1/1920 of the screen width. */
@Composable
fun MaterialTvTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val density = remember(context) {
        Density(context.resources.displayMetrics.widthPixels / CANVAS_WIDTH, fontScale = 1f)
    }
    val colors = remember {
        darkColorScheme(
            background = Palette.Background,
            surface = Palette.Background,
            onBackground = Palette.OnBackground,
            onSurface = Palette.OnBackground,
            primary = Palette.Accent
        )
    }
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalDensity provides density,
            LocalOverscrollFactory provides null, // a remote does not overscroll, and it draws every frame
            content = content
        )
    }
}

/**
 * The canvas outline: [width] thick, [gap] away from the element whose corners are [corner].
 * [height] limits it to the top part of the element. Apply it outside a Surface, which clips its content.
 */
fun Modifier.focusRing(focused: Boolean, corner: Dp, gap: Dp, width: Dp = 4.dp, height: Dp? = null): Modifier =
    drawWithContent {
        drawContent()
        if (focused) {
            val middle = gap.toPx() + width.toPx() / 2
            drawRoundRect(
                color = Palette.FocusRing,
                topLeft = Offset(-middle, -middle),
                size = Size(size.width + 2 * middle, (height?.toPx() ?: size.height) + 2 * middle),
                cornerRadius = CornerRadius(corner.toPx() + middle),
                style = Stroke(width.toPx())
            )
        }
    }
