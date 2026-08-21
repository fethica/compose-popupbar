package com.fethica.popupbar

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** The bar style the host was configured with; [PopupBar] reads it instead of taking a parameter. */
internal val LocalPopupBarStyle: ProvidableCompositionLocal<PopupBarStyle> =
    compositionLocalOf { PopupBarStyle.Floating }

/** True when the host was given a `popupImage`; the bar reserves its thumbnail slot only then. */
internal val LocalPopupHasImage: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** Progress thresholds. Each one gates a modifier chain, so each is read through `derivedStateOf`. */
private const val BarPointerThreshold = 0.1f     // the bar stops taking taps
private const val BarPlacementThreshold = 0.25f  // barAlpha() has reached 0: stop placing the bar
private const val ContentInertThreshold = 0.35f  // contentAlpha() starts rising
private const val CloseVisibleThreshold = 0.6f   // closeButtonAlpha() starts rising

@Stable
public interface PopupContentScope {
    public val state: PopupState

    /**
     * Reserves the rectangle where the host lands its `popupImage` once expanded.
     * Call it once inside `popupContent`.
     */
    @Composable
    public fun PopupImageSlot(modifier: Modifier, shape: Shape = RectangleShape)
}

/**
 * A rounded rectangle **inside** the node's bounds rather than around them: the popup layer is
 * always host-sized and only its clip morphs, so the content never reflows during the transition.
 */
internal data class PopupClipShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius, radius)))
}

private enum class Slot { Screen, BottomBar, Popup, Close }

@Composable
public fun PopupHost(
    state: PopupState,
    popupBar: @Composable () -> Unit,
    popupContent: @Composable PopupContentScope.() -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
    popupImage: (@Composable () -> Unit)? = null,
    barStyle: PopupBarStyle = PopupBarStyle.Floating,
    interactionStyle: PopupInteractionStyle = PopupInteractionStyle.Drag,
    closeButtonStyle: PopupCloseButtonStyle = PopupCloseButtonStyle.Grabber,
    closeButtonPosition: PopupCloseButtonPosition = PopupCloseButtonPosition.Center,
    scrimColor: Color = Color.Transparent,
    hapticsEnabled: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val metrics = remember(barStyle) { barStyle.metrics() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val dragInteractions = remember { MutableInteractionSource() }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentScope = remember(state) { PopupContentScopeImpl(state) }
    // WindowInsets values are @Composable to read; capture the holder here and query it in measure.
    val navigationBarInsets = WindowInsets.navigationBars

    // --- Per-frame reads that must NOT recompose ----------------------------------------------
    // Everything below flips at most twice per transition; `derivedStateOf` keeps the frame-by-frame
    // progress out of composition while still letting a threshold swap a modifier chain.
    val barTappable by remember(state) { derivedStateOf { state.progress <= BarPointerThreshold } }
    val barPlaced by remember(state) { derivedStateOf { state.progress < BarPlacementThreshold } }
    val contentInert by remember(state) { derivedStateOf { state.progress < ContentInertThreshold } }
    val closeVisible by remember(state) { derivedStateOf { state.progress > CloseVisibleThreshold } }
    val popupPlaced by remember(state) { derivedStateOf { !state.isHidden || state.presentation > 0f } }
    // Spec §3.1: the drag is disabled until the bar is fully presented.
    val gestureAllowed = interactionStyle == PopupInteractionStyle.Drag ||
        interactionStyle == PopupInteractionStyle.Scroll
    val dragEnabled by remember(state) { derivedStateOf { !state.isHidden && state.presentation >= 1f } }

    // --- Haptics ------------------------------------------------------------------------------
    // A threshold crossing only buzzes during a user drag; programmatic expand()/collapse() stay silent.
    var userDragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragInteractions, state) {
        dragInteractions.interactions.collect {
            when (it) {
                is DragInteraction.Start -> {
                    userDragging = true
                    state.lastGestureWasUser = true
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> userDragging = false
                else -> Unit
            }
        }
    }
    LaunchedEffect(state, hapticsEnabled) {
        if (!hapticsEnabled) return@LaunchedEffect
        snapshotFlow { state.draggable.targetValue }.drop(1).collect {
            if (userDragging) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
    LaunchedEffect(state, hapticsEnabled) {
        if (!hapticsEnabled) return@LaunchedEffect
        snapshotFlow { state.draggable.isAnimationRunning }.drop(1).collect { running ->
            if (!running && state.lastGestureWasUser) {
                haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                state.lastGestureWasUser = false
            }
        }
    }

    // --- Predictive back ----------------------------------------------------------------------
    // The popup peels back toward the bar while the gesture is live, then collapses or springs back.
    PredictiveBackHandler(enabled = state.isExpanded) { events ->
        try {
            events.collect { event ->
                val travel = state.travel
                if (travel > 0f) {
                    state.draggable.anchoredDrag { _ ->
                        dragTo(travel * (1f - backPeekProgress(event.progress)))
                    }
                }
            }
            state.collapse()
        } catch (_: CancellationException) {
            state.draggable.animateTo(PopupValue.Expanded, state.snapSpec)
        }
    }

    // `anchoredDraggableFlingBehavior(...)` is internal in foundation 1.10.2; the public entry point
    // for the same behaviour is AnchoredDraggableDefaults.flingBehavior (already @Composable-cached).
    val positionalThreshold = remember { { distance: Float -> distance * PopupDefaults.positionalThreshold } }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = state.draggable,
        positionalThreshold = positionalThreshold,
        animationSpec = PopupDefaults.snapSpec,
    )

    CompositionLocalProvider(
        LocalPopupBarStyle provides barStyle,
        LocalPopupHasImage provides (popupImage != null),
    ) {
        SubcomposeLayout(modifier = modifier) { constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val looseConstraints = Constraints(maxWidth = width, maxHeight = height)

            // 1. Docking bar first: its height drives every other rectangle.
            val bottomBarPlaceable = subcompose(Slot.BottomBar) {
                Box(
                    Modifier
                        .testTag("popupbar:bottomBar")
                        .graphicsLayer {
                            translationY = bottomBarTranslation(state.progress, size.height)
                        },
                ) { bottomBar() }
            }.first().measure(Constraints(minWidth = width, maxWidth = width))
            val bottomBarHeight = bottomBarPlaceable.height
            // No docking bar: the popup bar has to respect the navigation bar inset itself.
            val dockingInset = if (bottomBarHeight == 0) navigationBarInsets.getBottom(this) else 0

            val barHeightPx = metrics.height.roundToPx()
            val hMargin = metrics.horizontalMargin.roundToPx()
            val bMargin = metrics.bottomMargin.roundToPx()
            val barBottom = height - bottomBarHeight - dockingInset - bMargin
            val barWidthPx = (width - 2 * hMargin).coerceAtLeast(0)
            val barTopPx = (barBottom - barHeightPx).coerceAtLeast(0)
            val barRect = Rect(
                left = hMargin.toFloat(),
                top = barTopPx.toFloat(),
                right = (hMargin + barWidthPx).toFloat(),
                bottom = (barTopPx + barHeightPx).toFloat(),
            )
            val hostRect = Rect(0f, 0f, width.toFloat(), height.toFloat())
            state.updateTravel(barRect.top)

            // 2. Screen content, inset by the docking bar and by the presented bar.
            val insetPx = contentBottomInset(
                bottomBarHeight = (bottomBarHeight + dockingInset).toFloat(),
                presentation = state.presentation,
                barHeight = barHeightPx.toFloat(),
                barTopMargin = 0f,
                barBottomMargin = bMargin.toFloat(),
            )
            val insetDp = insetPx.toDp()
            val screenPlaceable = subcompose(Slot.Screen) {
                Box(Modifier.fillMaxSize().testTag("popupbar:screen")) {
                    content(PaddingValues(bottom = insetDp))
                    if (scrimColor != Color.Transparent) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = state.progress }
                                .background(scrimColor),
                        )
                    }
                }
            }.first().measure(looseConstraints)

            // 3. The popup layer: host-sized, clipped to the morphing rounded rect.
            val barRadiusPx = metrics.cornerRadius.toPx()
            val barShadowPx = metrics.shadowElevation.toPx()
            val popupPlaceable = subcompose(Slot.Popup) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = state.progress
                            val presented = state.presentation
                            clip = true
                            shape = PopupClipShape(
                                popupClipRect(p, barRect, hostRect),
                                popupClipRadius(p, barRadiusPx, 0f),
                            )
                            shadowElevation =
                                if (p < 0.5f) barShadowPx * (1f - p / 0.5f) else 0f
                            translationY = (1f - presented) * (barHeightPx + bMargin)
                            alpha = if (presented <= 0f) 0f else 1f
                        }
                        .background(containerColor)
                        .then(
                            if (gestureAllowed) {
                                Modifier.anchoredDraggable(
                                    state = state.draggable,
                                    orientation = Orientation.Vertical,
                                    enabled = dragEnabled,
                                    interactionSource = dragInteractions,
                                    flingBehavior = flingBehavior,
                                )
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    // Popup content: laid out at full host size so its text never reflows.
                    Box(
                        Modifier
                            .matchParentSize()
                            .testTag("popupbar:content")
                            .graphicsLayer { alpha = contentAlpha(state.progress) }
                            .then(
                                if (contentInert) {
                                    Modifier.semantics { hideFromAccessibility() }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        contentScope.popupContent()
                        // Topmost child while the content is still invisible: it takes the hit
                        // test away from everything below it *inside this box* without consuming,
                        // so the popup layer's own drag can still start on top of it.
                        if (contentInert) Box(Modifier.fillMaxSize().absorbPointers())
                    }
                    // The bar, positioned in its resting frame. Once it has faded out completely
                    // it is no longer placed, so nothing inside it can be tapped through the
                    // popup content that has taken its place on screen.
                    Box(
                        Modifier.layout { measurable, _ ->
                            val placeable = measurable.measure(
                                Constraints.fixed(barWidthPx, barHeightPx),
                            )
                            layout(width, height) {
                                if (barPlaced) placeable.place(hMargin, barTopPx)
                            }
                        },
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .testTag("popupbar:bar")
                                .graphicsLayer { alpha = barAlpha(state.progress) }
                                .then(
                                    if (barTappable) {
                                        // Spec: a tap expands under every interaction style,
                                        // including None.
                                        Modifier.clickable(role = Role.Button) {
                                            scope.launch { state.expand() }
                                        }
                                    } else {
                                        Modifier.semantics { hideFromAccessibility() }
                                    },
                                ),
                        ) {
                            popupBar()
                            if (!barTappable) Box(Modifier.fillMaxSize().absorbPointers())
                        }
                    }
                }
            }.first().measure(looseConstraints)

            // 4. Close button, under the status bar, fading in last.
            val closePlaceable = subcompose(Slot.Close) {
                Box(
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .graphicsLayer { alpha = closeButtonAlpha(state.progress) },
                ) {
                    PopupCloseButton(
                        style = closeButtonStyle,
                        position = closeButtonPosition,
                        enabled = closeVisible,
                        onClick = { scope.launch { state.collapse() } },
                    )
                }
            }.first().measure(Constraints(maxWidth = width, maxHeight = height))

            layout(width, height) {
                screenPlaceable.place(0, 0)
                bottomBarPlaceable.place(0, height - bottomBarHeight)
                if (popupPlaced) {
                    popupPlaceable.place(0, 0)
                    if (closeVisible) {
                        val rtl = layoutDirection == LayoutDirection.Rtl
                        val end = width - closePlaceable.width
                        val closeX = when (closeButtonPosition) {
                            PopupCloseButtonPosition.Leading -> if (rtl) end else 0
                            PopupCloseButtonPosition.Center -> end / 2
                            PopupCloseButtonPosition.Trailing -> if (rtl) 0 else end
                        }
                        closePlaceable.place(closeX.coerceAtLeast(0), 0)
                    }
                }
            }
        }
    }
}

private class PopupContentScopeImpl(override val state: PopupState) : PopupContentScope {
    @Composable
    override fun PopupImageSlot(modifier: Modifier, shape: Shape) {
        // Task 7 reports these bounds to the host and draws the travelling image overlay.
        Box(modifier)
    }
}

/**
 * Makes this node an opaque hit-test target that never *consumes* anything.
 *
 * Compose stops hit-testing siblings once one of them is hit, so an overlay carrying this modifier
 * takes every pointer away from the composables beneath it in the same box. Because it consumes
 * nothing, the ancestors of that overlay — in particular the popup layer's `anchoredDraggable` —
 * still receive the same pointer stream, so the transition stays interruptible.
 */
internal fun Modifier.absorbPointers(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent()
        }
    }
}
