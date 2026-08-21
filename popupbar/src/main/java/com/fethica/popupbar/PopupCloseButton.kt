package com.fethica.popupbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val GrabberWidth = 36.dp
private val GrabberHeight = 5.dp
private val GrabberTopPadding = 8.dp
private val TouchTargetSize = 48.dp
private const val GrabberAlpha = 0.4f

/**
 * Temporary close affordance: the Grabber only. Task 6 replaces this with the full
 * Grabber / Chevron / Round / None implementation and moves the metrics into [PopupDefaults].
 *
 * [enabled] is false while the popup is collapsed; the host relies on the modifier chain dropping
 * the `clickable` node entirely so the invisible affordance is not a hit-test target at all.
 */
@Composable
internal fun PopupCloseButton(
    style: PopupCloseButtonStyle,
    position: PopupCloseButtonPosition,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `position` is unused until Task 6 gives Chevron/Round a leading/trailing mirror; the host
    // already places this composable horizontally.
    if (style == PopupCloseButtonStyle.None) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = GrabberAlpha)
    Box(
        modifier
            .size(TouchTargetSize)
            .testTag("popupbar:close")
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .padding(top = GrabberTopPadding)
                .size(width = GrabberWidth, height = GrabberHeight)
                .clip(RoundedCornerShape(percent = 50))
                .background(color),
        )
    }
}
