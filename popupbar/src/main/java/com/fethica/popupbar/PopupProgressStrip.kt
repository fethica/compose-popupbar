package com.fethica.popupbar

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Fraction along the track for a touch/drag at [x] within a strip of [width].
 *
 * In RTL the fill starts at the right, so the fraction mirrors: `1f - x / width`. Guards a
 * zero-or-negative [width] (e.g. the first measure pass) by returning 0f instead of dividing.
 */
internal fun seekFraction(x: Float, width: Float, rtl: Boolean): Float {
    if (width <= 0f) return 0f
    val t = (x / width).coerceIn(0f, 1f)
    return if (rtl) 1f - t else t
}

/** The hairline itself, identical whether or not the strip is seekable. */
private val HairlineThickness = 2.dp

/** The hairline thickens by a dp while a seek drag is live, as the only touch cue. */
private val ActiveHairlineThickness = 3.dp

/**
 * Touch band for the seek drag. It hugs the bar's edge rather than being centred on the hairline, so
 * it never reaches the title or covers more than a strip of the bar.
 */
private val SeekBandHeight = 16.dp

/**
 * A 2dp hairline flush against the bar's [style] edge.
 *
 * When [onSeek] is given the node grows to a [SeekBandHeight] touch band along that same edge, but
 * the hairline stays exactly where it is: the ink is pixel-identical to the non-seekable case, only
 * thickening while a drag is live.
 *
 * Seeking is **drag only**. There is deliberately no tap-to-seek: a tap on the band has to fall
 * through to the host's tap-to-expand, and a vertical drag has to reach the popup layer's
 * `anchoredDraggable` — `detectHorizontalDragGestures` cancels without consuming when vertical slop
 * wins, which is what lets both happen.
 */
@Composable
internal fun PopupProgressStrip(
    progress: () -> Float,
    onSeek: ((Float) -> Unit)?,
    colors: PopupBarColors,
    style: PopupProgressStyle,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val seekLabel = stringResource(R.string.popupbar_seek)
    // PointerInputScope has no `layoutDirection` (unlike DrawScope below), so RTL is captured here
    // from composition and closed over by the gesture-detector lambdas.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val shown = { dragFraction ?: progress().coerceIn(0f, 1f) }
    Box(
        modifier
            .fillMaxWidth()
            .height(if (onSeek != null) SeekBandHeight else HairlineThickness)
            .testTag("popupbar:progress")
            .then(
                if (onSeek == null) {
                    Modifier
                } else {
                    Modifier
                        .semantics { contentDescription = seekLabel }
                        .pointerInput(onSeek, rtl) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragFraction = seekFraction(it.x, size.width.toFloat(), rtl) },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    dragFraction = seekFraction(change.position.x, size.width.toFloat(), rtl)
                                },
                                onDragEnd = { dragFraction?.let(onSeek); dragFraction = null },
                                onDragCancel = { dragFraction = null },
                            )
                        }
                },
            )
            .drawBehind {
                // The only place `progress()` is read every frame: a draw-phase closure, not a
                // composable-body read, so a ticking position never recomposes PopupBar.
                val trackH = if (dragFraction != null) {
                    ActiveHairlineThickness.toPx()
                } else {
                    HairlineThickness.toPx()
                }
                // Flush against the bar's edge, not centred in the touch band: a seekable strip must
                // draw the same hairline in the same place as a non-seekable one.
                val y = if (style == PopupProgressStyle.Top) 0f else size.height - trackH
                val isRtl = layoutDirection == LayoutDirection.Rtl
                drawRoundRect(
                    color = colors.progressTrackColor,
                    topLeft = Offset(0f, y),
                    size = Size(size.width, trackH),
                    cornerRadius = CornerRadius(trackH / 2f),
                )
                val w = size.width * shown()
                val x = if (isRtl) size.width - w else 0f
                drawRoundRect(
                    color = colors.progressColor,
                    topLeft = Offset(x, y),
                    size = Size(w, trackH),
                    cornerRadius = CornerRadius(trackH / 2f),
                )
            },
    )
}
