package com.fethica.popupbar

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

public enum class PopupBarStyle { Floating, FloatingCompact, Prominent, Compact }
public enum class PopupInteractionStyle { Drag, Snap, Scroll, None }
public enum class PopupCloseButtonStyle { Grabber, Chevron, Round, None }
public enum class PopupCloseButtonPosition { Leading, Center, Trailing }
public enum class PopupProgressStyle { None, Top, Bottom }

public object PopupDefaults {
    public val snapSpec: AnimationSpec<Float> = spring(dampingRatio = 0.85f, stiffness = 400f)
    public val presentationSpec: AnimationSpec<Float> = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    public val backPeekFraction: Float = 0.3f
    public val positionalThreshold: Float = 0.5f       // Drag
    public val snapPositionalThreshold: Float = 0.25f  // Snap
    public val velocityThresholdDp: Dp = 400.dp        // per second
}

/** Resting geometry of the popup bar for one [PopupBarStyle]. Spec §3.3. */
internal data class PopupBarMetrics(
    val height: Dp,
    val imageSize: Dp,
    val imageCornerRadius: Dp,
    val horizontalMargin: Dp,
    val bottomMargin: Dp,
    val cornerRadius: Dp,
    val shadowElevation: Dp,
)

internal fun PopupBarStyle.metrics(): PopupBarMetrics = when (this) {
    PopupBarStyle.Floating -> PopupBarMetrics(64.dp, 48.dp, 8.dp, 12.dp, 8.dp, 16.dp, 6.dp)
    PopupBarStyle.FloatingCompact -> PopupBarMetrics(48.dp, 36.dp, 6.dp, 12.dp, 8.dp, 16.dp, 6.dp)
    PopupBarStyle.Prominent -> PopupBarMetrics(64.dp, 48.dp, 8.dp, 0.dp, 0.dp, 0.dp, 0.dp)
    PopupBarStyle.Compact -> PopupBarMetrics(40.dp, 28.dp, 4.dp, 0.dp, 0.dp, 0.dp, 0.dp)
}
