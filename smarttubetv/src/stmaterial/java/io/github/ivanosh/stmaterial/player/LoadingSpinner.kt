package io.github.ivanosh.stmaterial.player

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.ivanosh.stmaterial.logic.CYCLES_PER_TURN
import io.github.ivanosh.stmaterial.logic.arcStart
import io.github.ivanosh.stmaterial.logic.arcSweep
import io.github.ivanosh.stmaterial.ui.Palette

private val IndicatorSize = 56.dp
private val IndicatorStroke = 5.dp
internal val SpinnerPlateSize = 88.dp
private val PlateColor = Palette.Background.copy(alpha = 0.8f)

private const val CYCLE_MS = 1332

/**
 * The player's loading indicator, a view that the Leanback progress bar manager shows and hides (see
 * [MaterialPlaybackFragment]), with its own delay and its own conditions. The Compose content exists only
 * while the view is visible, so the infinite transition, and with it every frame, stops when loading ends.
 * It takes no focus and no key.
 */
class LoadingSpinnerView(context: Context) : FrameLayout(context) {
    private var loading by mutableStateOf(false)

    init {
        isFocusable = false
        isClickable = false
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(ComposeView(context).apply { setContent { if (loading) LoadingSpinner() } })
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        loading = visibility == View.VISIBLE
    }
}

/** 56dp arc, 5dp round stroke, no track, on an 88dp dark translucent circle. */
@Composable
private fun LoadingSpinner() {
    val transition = rememberInfiniteTransition(label = "loading")
    // One clock for the whole turn. Everything else is worked out from it while drawing, so nothing composes per frame.
    val turn = transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(CYCLE_MS * CYCLES_PER_TURN, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    val density = LocalDensity.current
    val stroke = remember(density) { Stroke(with(density) { IndicatorStroke.toPx() }, cap = StrokeCap.Round) }
    Canvas(Modifier.size(SpinnerPlateSize)) {
        val clock = turn.value
        drawCircle(PlateColor)
        val diameter = IndicatorSize.toPx() - stroke.width
        val inset = (size.width - diameter) / 2f
        drawArc(Palette.Accent, arcStart(clock), arcSweep(clock), false, Offset(inset, inset), Size(diameter, diameter), style = stroke)
    }
}
