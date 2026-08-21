package com.fethica.popupbar

import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
internal fun easeOutCubic(t: Float): Float { val u = 1f - t.coerceIn(0f, 1f); return 1f - u * u * u }
internal fun easeInOutSine(t: Float): Float = (-(cos(PI * t.coerceIn(0f, 1f)) - 1.0) / 2.0).toFloat()

/** Horizontal edges and the bottom finish by progress 0.5 (eased); the top edge follows the finger linearly. */
internal fun popupClipRect(progress: Float, barRect: Rect, hostRect: Rect): Rect {
    val p = progress.coerceIn(0f, 1f)
    val w = easeOutCubic(p / 0.5f)
    return Rect(
        left = lerp(barRect.left, hostRect.left, w),
        top = lerp(barRect.top, hostRect.top, p),
        right = lerp(barRect.right, hostRect.right, w),
        bottom = lerp(barRect.bottom, hostRect.bottom, w),
    )
}

internal fun popupClipRadius(progress: Float, barRadiusPx: Float, expandedRadiusPx: Float): Float =
    lerp(barRadiusPx, expandedRadiusPx, easeOutCubic(progress.coerceIn(0f, 1f) / 0.5f))

internal fun imageOverlayRect(progress: Float, barSlot: Rect, contentSlot: Rect): Rect {
    val t = easeInOutSine(progress)
    return Rect(lerp(barSlot.left, contentSlot.left, t), lerp(barSlot.top, contentSlot.top, t), lerp(barSlot.right, contentSlot.right, t), lerp(barSlot.bottom, contentSlot.bottom, t))
}

internal fun imageOverlayRadius(progress: Float, barRadiusPx: Float, contentRadiusPx: Float): Float =
    lerp(barRadiusPx, contentRadiusPx, easeInOutSine(progress))

internal fun contentBottomInset(bottomBarHeight: Float, presentation: Float, barHeight: Float, barTopMargin: Float, barBottomMargin: Float): Float =
    bottomBarHeight + presentation.coerceIn(0f, 1f) * (barHeight + barTopMargin + barBottomMargin)

internal fun bottomBarTranslation(progress: Float, bottomBarHeight: Float): Float = progress.coerceIn(0f, 1f) * bottomBarHeight

internal fun barAlpha(progress: Float): Float = (1f - progress / 0.3f).coerceIn(0f, 1f)
internal fun contentAlpha(progress: Float): Float {
    val start = 0.2f
    val end = 0.55f
    return ((progress - start) / (end - start)).coerceIn(0f, 1f)
}
internal fun closeButtonAlpha(progress: Float): Float {
    // width is derived from the same threshold literal (not a separately-rounded 0.4f) so that
    // progress == threshold and progress == 1f divide out to exactly 0f/1f in float32, not a
    // one-ULP-off neighbor: (progress - threshold) and width become bit-identical at progress = 1f.
    val threshold = 0.6f
    val width = 1f - threshold
    return ((progress - threshold) / width).coerceIn(0f, 1f)
}
internal fun backPeekProgress(backProgress: Float): Float = 1f - PopupDefaults.backPeekFraction * backProgress.coerceIn(0f, 1f)

internal fun progressFromOffset(offset: Float, travel: Float): Float =
    if (travel <= 0f || offset.isNaN()) 0f else (1f - offset / travel).coerceIn(0f, 1f)

/** Offset convention: Expanded = 0, Collapsed = travel. Negative velocity = moving up = towards Expanded. */
internal fun snapTarget(offset: Float, travel: Float, velocity: Float, from: PopupValue, positionalThreshold: Float, velocityThreshold: Float): PopupValue {
    if (abs(velocity) >= velocityThreshold) return if (velocity < 0f) PopupValue.Expanded else PopupValue.Collapsed
    val moved = if (from == PopupValue.Collapsed) travel - offset else offset
    val crossed = travel > 0f && moved / travel >= positionalThreshold
    return when (from) {
        PopupValue.Collapsed -> if (crossed) PopupValue.Expanded else PopupValue.Collapsed
        else -> if (crossed) PopupValue.Collapsed else PopupValue.Expanded
    }
}
