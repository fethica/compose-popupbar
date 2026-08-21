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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The bar style the host was configured with; [PopupBar] reads it instead of taking a parameter. */
internal val LocalPopupBarStyle: ProvidableCompositionLocal<PopupBarStyle> =
    compositionLocalOf { PopupBarStyle.Floating }

/** True when the host was given a `popupImage`; the bar reserves its thumbnail slot only then. */
internal val LocalPopupHasImage: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/** Progress thresholds. Each one gates a modifier chain, so each is read through `derivedStateOf`. */
private const val BarPointerThreshold = 0.1f     // the bar stops taking taps
private const val BarPlacementThreshold = 0.3f   // barAlpha() has reached 0: stop placing the bar
private const val ContentInertThreshold = 0.2f   // contentAlpha() starts rising
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

private enum class Slot { Screen, BottomBar, Popup, Image }

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
    val expandLabel = stringResource(R.string.popupbar_expand)
    val imageRegistry = remember { PopupImageRegistry() }
    var hostCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val hostCoordinatesProvider = remember { { hostCoordinates } }
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
    // Spec §3.1: gestures are disabled until the bar is fully presented.
    val dragEnabled by remember(state) { derivedStateOf { !state.isHidden && state.presentation >= 1f } }
    val velocityThresholdPx = with(LocalDensity.current) {
        PopupDefaults.velocityThresholdDp.toPx()
    }

    // --- Haptics ------------------------------------------------------------------------------
    // A threshold crossing only buzzes during a user drag; programmatic expand()/collapse() stay silent.
    var userDragging by remember { mutableStateOf(false) }
    val onUserGesture = remember { { dragging: Boolean -> userDragging = dragging } }
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
    LaunchedEffect(state, hapticsEnabled, haptic) {
        if (!hapticsEnabled) return@LaunchedEffect
        snapshotFlow { state.draggable.targetValue }.drop(1).collect {
            if (userDragging) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
    LaunchedEffect(state, hapticsEnabled, haptic) {
        snapshotFlow { state.draggable.isAnimationRunning }.drop(1).collect { running ->
            if (!running && state.lastGestureWasUser) {
                if (hapticsEnabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                }
                state.lastGestureWasUser = false
            }
        }
    }

    // --- Predictive back ----------------------------------------------------------------------
    // The popup peels back toward the bar while the gesture is live, then collapses or springs back.
    PredictiveBackHandler(enabled = state.isExpanded) { events ->
        state.handleBackGesture(events.map { it.progress }, springBackScope = scope)
    }

    // `anchoredDraggableFlingBehavior(...)` is internal in foundation 1.10.2; the public entry point
    // for the same behaviour is AnchoredDraggableDefaults.flingBehavior (already @Composable-cached).
    val positionalThreshold = remember { { distance: Float -> distance * PopupDefaults.positionalThreshold } }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = state.draggable,
        positionalThreshold = positionalThreshold,
        animationSpec = PopupDefaults.snapSpec,
    )
    val nestedScrollConnection = remember(state, velocityThresholdPx) {
        PopupNestedScrollConnection(state, velocityThresholdPx)
    }

    CompositionLocalProvider(
        LocalPopupBarStyle provides barStyle,
        LocalPopupHasImage provides (popupImage != null),
        LocalPopupImageRegistry provides imageRegistry,
        LocalPopupHostCoordinates provides hostCoordinatesProvider,
    ) {
        SubcomposeLayout(
            modifier = modifier.onGloballyPositioned { hostCoordinates = it },
        ) { constraints ->
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
            }.first().measure(
                Constraints(minWidth = width, maxWidth = width, maxHeight = height),
            )
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
                        // The layer is opaque to hit testing whenever it is placed, whatever the
                        // interaction style: `background()` is a draw node, so without this a tap on
                        // empty space in an expanded `None`/`Snap` popup fell through to the screen
                        // underneath. It consumes nothing, so the draggable below still sees every
                        // pointer and the bar's own clickable still wins on the Main pass.
                        .absorbPointers()
                        .then(
                            when (interactionStyle) {
                                PopupInteractionStyle.Drag,
                                PopupInteractionStyle.Scroll,
                                -> Modifier.anchoredDraggable(
                                    state = state.draggable,
                                    orientation = Orientation.Vertical,
                                    enabled = dragEnabled,
                                    interactionSource = dragInteractions,
                                    flingBehavior = flingBehavior,
                                )

                                PopupInteractionStyle.Snap -> if (dragEnabled) {
                                    Modifier.popupSnapGesture(
                                        state = state,
                                        scope = scope,
                                        velocityThresholdPx = velocityThresholdPx,
                                        onUserGesture = onUserGesture,
                                    )
                                } else {
                                    Modifier
                                }

                                PopupInteractionStyle.None -> Modifier.popupIgnoreVerticalDrag()
                            },
                        ),
                ) {
                    // Popup content: laid out at full host size so its text never reflows.
                    Box(
                        Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = contentAlpha(state.progress) }
                            .then(
                                if (interactionStyle == PopupInteractionStyle.Scroll) {
                                    Modifier.nestedScroll(nestedScrollConnection)
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (contentInert) {
                                    Modifier.clearAndSetSemantics {
                                        semanticsTestTag = "popupbar:content"
                                        hideFromAccessibility()
                                    }
                                } else {
                                    Modifier.testTag("popupbar:content")
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
                                // One merged accessibility node for the whole bar: this must sit
                                // BEFORE `clickable` below so the click's Role/onClick land inside
                                // the same merge boundary as `popupBar()`'s contentDescription and
                                // progressBarRangeInfo, instead of TalkBack seeing two stops.
                                .semantics(mergeDescendants = true) {}
                                .then(
                                    if (barTappable) {
                                        // Spec: a tap expands under every interaction style,
                                        // including None.
                                        Modifier.clickable(
                                            onClickLabel = expandLabel,
                                            role = Role.Button,
                                        ) {
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
                    // 4. Close button, top-most inside the layer so the morphing clip cuts it: it
                    // is revealed as the card's top edge reaches it, never floating over the screen
                    // content above the card. `align` resolves Leading/Trailing for RTL itself.
                    Box(
                        Modifier
                            .align(closeButtonAlignment(closeButtonPosition))
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .graphicsLayer { alpha = closeButtonAlpha(state.progress) },
                    ) {
                        if (closeVisible) {
                            PopupCloseButton(
                                style = closeButtonStyle,
                                position = closeButtonPosition,
                                onClick = { scope.launch { state.collapse() } },
                            )
                        }
                    }
                }
            }.first().measure(looseConstraints)

            // 5. One persistent image instance, routed between the two empty reported slots.
            val imagePlaceable = if (popupImage == null) {
                null
            } else {
                subcompose(Slot.Image) {
                    Box(
                        Modifier
                            .layout { measurable, _ ->
                                val barSlot = imageRegistry.barSlot
                                val contentSlot = imageRegistry.contentSlot
                                val rect = when {
                                    barSlot == null && contentSlot == null -> null
                                    barSlot == null -> contentSlot
                                    contentSlot == null -> barSlot
                                    else -> imageOverlayRect(state.progress, barSlot, contentSlot)
                                }
                                if (rect == null) {
                                    layout(0, 0) {}
                                } else {
                                    val imageWidth = rect.width.roundToInt().coerceAtLeast(0)
                                    val imageHeight = rect.height.roundToInt().coerceAtLeast(0)
                                    val placeable = measurable.measure(
                                        Constraints.fixed(imageWidth, imageHeight),
                                    )
                                    layout(width, height) {
                                        placeable.place(
                                            rect.left.roundToInt(),
                                            rect.top.roundToInt(),
                                        )
                                    }
                                }
                            }
                            .graphicsLayer {
                                val p = state.progress
                                val presented = state.presentation
                                clip = true
                                shape = RoundedCornerShape(
                                    imageOverlayRadius(
                                        p,
                                        imageRegistry.barRadius,
                                        imageRegistry.contentRadius,
                                    ),
                                )
                                shadowElevation = if (p in 0.01f..0.99f) 4.dp.toPx() else 0f
                                alpha = if (presented <= 0f) 0f else 1f
                            },
                    ) { popupImage() }
                }.first().measure(looseConstraints)
            }

            layout(width, height) {
                screenPlaceable.place(0, 0)
                bottomBarPlaceable.place(0, height - bottomBarHeight)
                if (popupPlaced) popupPlaceable.place(0, 0)
                if (popupPlaced) imagePlaceable?.place(0, 0)
            }
        }
    }
}

/**
 * Body of the host's `PredictiveBackHandler`, extracted so the cancellation path can be tested.
 *
 * [springBackScope] must **not** be the coroutine that runs this function.
 * `ComposePredictiveBackHandler.onBackCancelled()` cancels the event channel *and then* the job the
 * handler body runs in, so by the time the `CancellationException` surfaces here that job is already
 * cancelling: a suspending `animateTo` on it would throw before its first frame and leave the card
 * stranded wherever the peek stopped. Handing the spring back to the host's own remembered scope is
 * what makes a cancelled gesture recover.
 */
internal suspend fun PopupState.handleBackGesture(
    backProgress: Flow<Float>,
    springBackScope: CoroutineScope,
) {
    try {
        backProgress.collect { progress ->
            val distance = travel
            if (distance > 0f) {
                draggable.anchoredDrag { _ -> dragTo(distance * (1f - backPeekProgress(progress))) }
            }
        }
        collapseFromGesture()
    } catch (_: CancellationException) {
        springBackScope.launch { draggable.animateTo(PopupValue.Expanded, snapSpec) }
    }
}

private fun closeButtonAlignment(position: PopupCloseButtonPosition): Alignment = when (position) {
    PopupCloseButtonPosition.Leading -> Alignment.TopStart
    PopupCloseButtonPosition.Center -> Alignment.TopCenter
    PopupCloseButtonPosition.Trailing -> Alignment.TopEnd
}

private class PopupContentScopeImpl(override val state: PopupState) : PopupContentScope {
    @Composable
    override fun PopupImageSlot(modifier: Modifier, shape: Shape) {
        val registry = LocalPopupImageRegistry.current
        val hostCoordinates = LocalPopupHostCoordinates.current
        val density = LocalDensity.current
        var slotSize by remember(shape) { mutableStateOf(IntSize.Zero) }
        val radiusPx = (shape as? RoundedCornerShape)?.topStart?.toPx(
            Size(slotSize.width.toFloat(), slotSize.height.toFloat()),
            density,
        ) ?: 0f
        Box(
            modifier
                .onSizeChanged { slotSize = it }
                .then(
                    if (registry == null) {
                        Modifier
                    } else {
                        Modifier.reportPopupSlot(
                            registry = registry,
                            isBar = false,
                            hostCoordinates = hostCoordinates,
                            radiusPx = radiusPx,
                        )
                    },
                ),
        )
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
