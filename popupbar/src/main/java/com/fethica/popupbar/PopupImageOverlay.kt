package com.fethica.popupbar

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/** Bounds and corner radii reported by the two empty popup-image slots. */
internal class PopupImageRegistry {
    var barSlot: Rect? by mutableStateOf(null)
    var barRadius: Float by mutableFloatStateOf(0f)
    var contentSlot: Rect? by mutableStateOf(null)
    var contentRadius: Float by mutableFloatStateOf(0f)
}

/** The registry for the [PopupHost] currently composing a bar or popup-content image slot. */
internal val LocalPopupImageRegistry: ProvidableCompositionLocal<PopupImageRegistry?> =
    staticCompositionLocalOf { null }

/** Resolves slot coordinates into the owning host without coupling the slots to its implementation. */
internal val LocalPopupHostCoordinates: ProvidableCompositionLocal<() -> LayoutCoordinates?> =
    staticCompositionLocalOf { { null } }

/** Reports this empty slot in host-local coordinates without clipping it to the morphing layer. */
internal fun Modifier.reportPopupSlot(
    registry: PopupImageRegistry,
    isBar: Boolean,
    hostCoordinates: () -> LayoutCoordinates?,
    radiusPx: Float,
): Modifier = onGloballyPositioned { slotCoordinates ->
    val host = hostCoordinates() ?: return@onGloballyPositioned
    val bounds = host.localBoundingBoxOf(slotCoordinates, clipBounds = false)
    if (isBar) {
        registry.barSlot = bounds
        registry.barRadius = radiusPx
    } else {
        registry.contentSlot = bounds
        registry.contentRadius = radiusPx
    }
}
