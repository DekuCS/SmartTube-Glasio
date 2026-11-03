package io.github.ivanosh.stmaterial.ui

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/** The backdrop of the browse screen: near-black with two deep red washes, no violet. */
object Backdrop {
    val Top = Color(0xFF180607)
    val Bottom = Color(0xFF0A0304)
    val Wine = Color(0xFF3A0A0E)
    val Crimson = Color(0xFF540D13)
}

/** The dock material: translucent charcoal, a sheen in the top-left and a hairline rim. */
object Glass {
    val DockTop = Color(0xE6191117)
    val DockBottom = Color(0xF50A070D)
    val Sheen = Color(0x1FFFFFFF)
    val Rim = Color(0x2EFFFFFF)
    val ItemFocused = Color(0x26FFFFFF)
    val ItemSelected = Color(0x16FFFFFF)
}

/**
 * Draws [Backdrop] behind the content: a vertical base and two very wide radial washes, so there is no
 * visible boundary. The brushes are built once per size and nothing moves: a still gradient cannot stutter,
 * and focus or scrolling does not invalidate this node.
 *
 * shortcut: still gradient; add a slow offset only after measuring headroom on the Fire TV.
 */
fun Modifier.gradientBackground(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val base = Brush.verticalGradient(listOf(Backdrop.Top, Backdrop.Bottom), startY = 0f, endY = h)
    val crimson = Brush.radialGradient(
        listOf(Backdrop.Crimson.copy(alpha = 0.55f), Backdrop.Crimson.copy(alpha = 0f)),
        center = Offset(-w * 0.02f, h * 0.42f),
        radius = w * 0.78f
    )
    val wine = Brush.radialGradient(
        listOf(Backdrop.Wine.copy(alpha = 0.55f), Backdrop.Wine.copy(alpha = 0f)),
        center = Offset(w * 0.62f, h * 1.02f),
        radius = w * 0.72f
    )
    onDrawBehind {
        drawRect(base)
        drawRect(crimson)
        drawRect(wine)
    }
}

/**
 * The liquid-glass pill: translucent charcoal, one radial sheen and a thin rim, painted in a single pass.
 * There is deliberately no backdrop blur: a capture and several passes per frame is what a Fire TV stick
 * cannot spare, and on this dark backdrop the flat material reads the same.
 */
fun Modifier.liquidGlass(corner: Dp): Modifier = drawWithCache {
    val radius = CornerRadius(corner.toPx())
    val body = Brush.verticalGradient(listOf(Glass.DockTop, Glass.DockBottom))
    val sheen = Brush.radialGradient(
        listOf(Glass.Sheen, Color.Transparent),
        center = Offset(size.width * 0.25f, -size.height * 0.35f),
        radius = size.width * 0.7f
    )
    val rim = 1.5.dp.toPx()
    onDrawBehind {
        drawRoundRect(brush = body, cornerRadius = radius)
        drawRoundRect(brush = sheen, cornerRadius = radius)
        drawRoundRect(color = Glass.Rim, cornerRadius = radius, style = Stroke(rim))
    }
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
