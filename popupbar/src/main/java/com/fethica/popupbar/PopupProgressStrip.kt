package com.fethica.popupbar

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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

/** A 2dp hairline; when [onSeek] is given, a 24dp-tall touch strip that drags/taps to a fraction and seeks on release. */
@Composable
internal fun PopupProgressStrip(
    progress: () -> Float,
    onSeek: ((Float) -> Unit)?,
    colors: PopupBarColors,
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
            .height(if (onSeek != null) 24.dp else 2.dp)
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
                        .pointerInput(onSeek, rtl) {
                            detectTapGestures { onSeek(seekFraction(it.x, size.width.toFloat(), rtl)) }
                        }
                },
            )
            .drawBehind {
                // The only place `progress()` is read every frame: a draw-phase closure, not a
                // composable-body read, so a ticking position never recomposes PopupBar.
                val trackH = if (onSeek != null) 3.dp.toPx() else 2.dp.toPx()
                val y = (size.height - trackH) / 2f
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
