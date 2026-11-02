package io.github.ivanosh.stmaterial.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

// Sizes of the canvas, in pixels of the bitmap.
private const val THUMB_WIDTH = 400
private const val THUMB_HEIGHT = 225
private const val THUMB_CORNER = 16f
private const val TEXT_GAP = 12
private const val TITLE_LINE = 34
private const val TITLE_SIZE = 26f
private const val META_LINE = 30
private const val META_SIZE = 24f
private const val CARD_HEIGHT = THUMB_HEIGHT + TEXT_GAP + TITLE_LINE + TEXT_GAP + META_LINE
private const val BADGE_MARGIN = 12f
private const val BADGE_LINE = 32f
private const val BADGE_PADDING = 10f
private const val BADGE_CORNER = 8f
private const val PROGRESS_HEIGHT = 6f
private const val FOCUS_ANIMATION_MS = 120

/** All a card shows. Equal faces make the same bitmap, which is also the key in Glide's memory cache. */
private data class CardFace(val url: String?, val title: String, val meta: String, val badge: String?, val progress: Float)

private fun faceOf(video: Video) = CardFace(
    url = video.getCardImageUrl(),
    title = video.getTitle().orEmpty(),
    meta = video.getSecondTitle()?.toString().orEmpty(),
    badge = video.badge?.takeIf { it.isNotBlank() },
    progress = (video.percentWatched / 100f).coerceIn(0f, 1f),
)

/**
 * One card is one bitmap, made off the main thread: a card with Compose text scrolls worse on real
 * devices, see docs/perf.md. The screen only draws finished images, and focus changes nothing but the
 * scale and the outline around them.
 *
 * Focus belongs to the caller: only the card that changed recomposes when focus moves. [revision] is
 * how the caller says the video changed in place.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoCard(
    video: Video,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = Palette.Tones[0],
    revision: Int = 0,
    /** False for a card that is only drawn, as in the player's panel, where the Activity holds the keys: then Compose never gives it focus or its own outline. */
    interactive: Boolean = true,
) {
    val scale = animateFloatAsState(if (focused) 1.04f else 1f, tween(FOCUS_ANIMATION_MS), label = "cardScale")
    val image = rememberCardImage(remember(video, revision) { faceOf(video) }, tone)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = interactive,
        modifier = modifier
            .size(THUMB_WIDTH.dp, CARD_HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .focusRing(focused, corner = THUMB_CORNER.dp, gap = 4.dp, height = THUMB_HEIGHT.dp),
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.Transparent,
            pressedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {
        Spacer(
            Modifier
                .size(THUMB_WIDTH.dp, CARD_HEIGHT.dp)
                .drawBehind {
                    val bitmap = image.value
                    if (bitmap != null) {
                        drawImage(bitmap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    } else {
                        drawRoundRect(
                            tone,
                            size = Size(size.width, THUMB_HEIGHT.dp.toPx()),
                            cornerRadius = CornerRadius(THUMB_CORNER.dp.toPx())
                        )
                    }
                }
        )
    }
}

/** Loads through the app's Glide for as long as the card is composed. The state is read only while drawing. */
@Composable
private fun rememberCardImage(face: CardFace, tone: Color): State<ImageBitmap?> {
    val glide = Glide.with(LocalContext.current.applicationContext)
    val scope = rememberCoroutineScope()
    val loader = remember { CardImageLoader(glide) }
    DisposableEffect(loader) { onDispose { loader.clear() } }
    DisposableEffect(face) {
        loader.load(face, tone, scope)
        onDispose { }
    }
    return loader.image
}

/**
 * Keeps showing the old bitmap until the new one is ready, so a changed card does not blink. Glide may
 * reuse a bitmap once its target is cleared, so the old target lives exactly as long as it is on screen.
 */
private class CardImageLoader(private val glide: RequestManager) {
    val image = mutableStateOf<ImageBitmap?>(null)
    private var shown: Target<Bitmap>? = null
    private var pending: Target<Bitmap>? = null
    private var baking: Job? = null

    fun load(face: CardFace, tone: Color, scope: CoroutineScope) {
        cancelPending()
        if (face.url == null) {
            bakeWithoutThumbnail(face, tone, scope)
            return
        }
        val target = object : CustomTarget<Bitmap>(THUMB_WIDTH, THUMB_HEIGHT) {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                pending = null
                show(resource, this)
            }

            // No picture still leaves a title to show, on the tone of the placeholder.
            override fun onLoadFailed(errorDrawable: Drawable?) {
                bakeWithoutThumbnail(face, tone, scope)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                if (shown === this) image.value = null
            }
        }
        pending = target
        glide.asBitmap()
            .load(face.url)
            .transform(CardBake(face))
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .into(target)
    }

    fun clear() {
        cancelPending()
        shown?.let(glide::clear)
        shown = null
        image.value = null
    }

    private fun cancelPending() {
        baking?.cancel()
        pending?.let(glide::clear)
        pending = null
    }

    private fun bakeWithoutThumbnail(face: CardFace, tone: Color, scope: CoroutineScope) {
        baking = scope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                Bitmap.createBitmap(THUMB_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
                    .also { bakeCard(it, null, face, tone.toArgb()) }
            }
            show(bitmap, null)
        }
    }

    private fun show(bitmap: Bitmap, target: Target<Bitmap>?) {
        val previous = shown
        shown = target
        image.value = bitmap.asImageBitmap()
        previous?.let(glide::clear)
    }
}

/** Runs on a Glide thread, with the decoded thumbnail. */
private class CardBake(private val face: CardFace) : BitmapTransformation() {
    override fun transform(pool: BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap =
        pool.get(THUMB_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888).also { bakeCard(it, toTransform, face, 0) }

    override fun equals(other: Any?) = other is CardBake && other.face == face

    override fun hashCode() = face.hashCode()

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(face.toString().toByteArray())
    }
}

private val TitleTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
private val OnBackground = Palette.OnBackground.toArgb()
private val OnBackgroundVariant = Palette.OnBackgroundVariant.toArgb()

private fun bakeCard(out: Bitmap, thumbnail: Bitmap?, face: CardFace, tone: Int) {
    val canvas = Canvas(out)
    val thumb = RectF(0f, 0f, THUMB_WIDTH.toFloat(), THUMB_HEIGHT.toFloat())
    val fill = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (thumbnail != null) {
        fill.shader = centerCropShader(thumbnail)
    } else {
        fill.color = tone
    }
    canvas.drawRoundRect(thumb, THUMB_CORNER, THUMB_CORNER, fill)
    fill.shader = null

    if (face.progress > 0f) {
        // SRC_ATOP keeps the rounded corners of the picture under the bar.
        val top = THUMB_HEIGHT - PROGRESS_HEIGHT
        val bar = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }
        bar.color = Palette.ProgressTrack.toArgb()
        canvas.drawRect(0f, top, thumb.right, thumb.bottom, bar)
        bar.color = Palette.Accent.toArgb()
        canvas.drawRect(0f, top, thumb.right * face.progress, thumb.bottom, bar)
    }

    face.badge?.let { badge ->
        val paint = textPaint(META_SIZE, OnBackground, Typeface.DEFAULT)
        val width = paint.measureText(badge) + 2 * BADGE_PADDING
        val box = RectF(
            thumb.right - BADGE_MARGIN - width,
            thumb.bottom - BADGE_MARGIN - BADGE_LINE - 4f,
            thumb.right - BADGE_MARGIN,
            thumb.bottom - BADGE_MARGIN
        )
        fill.color = Palette.Background.toArgb()
        canvas.drawRoundRect(box, BADGE_CORNER, BADGE_CORNER, fill)
        drawLine(canvas, badge, paint, box.left + BADGE_PADDING, box.top, box.height())
    }

    val titleTop = (THUMB_HEIGHT + TEXT_GAP).toFloat()
    val titlePaint = textPaint(TITLE_SIZE, OnBackground, TitleTypeface)
    drawLine(canvas, ellipsized(face.title, titlePaint), titlePaint, 0f, titleTop, TITLE_LINE.toFloat())
    val metaPaint = textPaint(META_SIZE, OnBackgroundVariant, Typeface.DEFAULT)
    drawLine(canvas, ellipsized(face.meta, metaPaint), metaPaint, 0f, titleTop + TITLE_LINE + TEXT_GAP, META_LINE.toFloat())
}

private fun centerCropShader(source: Bitmap): BitmapShader {
    val scale = maxOf(THUMB_WIDTH.toFloat() / source.width, THUMB_HEIGHT.toFloat() / source.height)
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate((THUMB_WIDTH - source.width * scale) / 2f, (THUMB_HEIGHT - source.height * scale) / 2f)
    }
    return BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(matrix) }
}

private fun textPaint(size: Float, color: Int, typeface: Typeface) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size
    this.color = color
    this.typeface = typeface
}

private fun ellipsized(text: String, paint: TextPaint): String =
    TextUtils.ellipsize(text, paint, THUMB_WIDTH.toFloat(), TextUtils.TruncateAt.END).toString()

/** Centres one line vertically in a box that starts at [top] and is [lineHeight] tall. */
private fun drawLine(canvas: Canvas, text: String, paint: TextPaint, x: Float, top: Float, lineHeight: Float) {
    val metrics = paint.fontMetrics
    canvas.drawText(text, x, top + (lineHeight - metrics.descent - metrics.ascent) / 2f, paint)
}
