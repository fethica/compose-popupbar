package com.fethica.popupbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
public class PopupState internal constructor(
    initialValue: PopupValue,
    internal val confirmValueChange: (PopupValue) -> Boolean,
) {
    internal val draggable: AnchoredDraggableState<PopupValue> = AnchoredDraggableState(
        initialValue = if (initialValue == PopupValue.Expanded) PopupValue.Expanded else PopupValue.Collapsed,
        confirmValueChange = { confirmValueChange(it) },
    )
    internal val presentationAnimatable: Animatable<Float, AnimationVector1D> =
        Animatable(if (initialValue == PopupValue.Hidden) 0f else 1f)
    private var hidden: Boolean by mutableStateOf(initialValue == PopupValue.Hidden)
    internal var travel: Float by mutableFloatStateOf(0f)
        private set
    internal var lastGestureWasUser: Boolean = false
    internal val snapSpec: AnimationSpec<Float> get() = PopupDefaults.snapSpec
    internal val presentationSpec: AnimationSpec<Float> get() = PopupDefaults.presentationSpec

    public val currentValue: PopupValue get() = if (hidden) PopupValue.Hidden else draggable.settledValue
    public val targetValue: PopupValue get() = if (hidden) PopupValue.Hidden else draggable.targetValue
    public val progress: Float get() = if (hidden || travel <= 0f) 0f else progressFromOffset(draggable.offset, travel)
    public val presentation: Float get() = presentationAnimatable.value
    public val isHidden: Boolean get() = currentValue == PopupValue.Hidden
    public val isCollapsed: Boolean get() = currentValue == PopupValue.Collapsed
    public val isExpanded: Boolean get() = currentValue == PopupValue.Expanded
    public val isAnimationRunning: Boolean get() = draggable.isAnimationRunning || presentationAnimatable.isRunning

    internal fun updateTravel(newTravel: Float) {
        if (newTravel == travel && draggable.anchors.size == 2) return
        travel = newTravel
        val target = draggable.targetValue
        draggable.updateAnchors(
            DraggableAnchors { PopupValue.Expanded at 0f; PopupValue.Collapsed at newTravel },
            target,
        )
    }

    /**
     * Hidden -> Collapsed, animated.
     *
     * Also *heals* a partial presentation: a caller cancelled mid-[hide] (or mid-`present`) leaves
     * the bar part-way through its slide, and this animates the rest of the way instead of
     * short-circuiting on the settled flag and stranding it there for good.
     */
    public suspend fun present() {
        if (!hidden && presentationAnimatable.value >= 1f) return
        if (!confirmValueChange(PopupValue.Collapsed)) return
        if (hidden) {
            draggable.snapTo(PopupValue.Collapsed)
            hidden = false
        }
        presentationAnimatable.animateTo(1f, presentationSpec)
    }

    public suspend fun expand() {
        if (hidden) {
            present()
            // present() can be vetoed by confirmValueChange, and expanding a popup that was never
            // presented animates a card nobody can see. Only proceed once the bar really is out.
            if (hidden) return
        }
        if (!confirmValueChange(PopupValue.Expanded)) return
        lastGestureWasUser = false
        draggable.animateTo(PopupValue.Expanded, snapSpec)
    }

    public suspend fun collapse() {
        collapse(userGesture = false)
    }

    internal suspend fun collapseFromGesture() {
        collapse(userGesture = true)
    }

    private suspend fun collapse(userGesture: Boolean) {
        if (hidden) return
        if (!confirmValueChange(PopupValue.Collapsed)) return
        lastGestureWasUser = userGesture
        draggable.animateTo(PopupValue.Collapsed, snapSpec)
    }

    /**
     * -> Hidden, animated (collapses first if Expanded).
     *
     * The mirror of [present]: a `hide()` whose caller was cancelled leaves a ghost bar part-way
     * through its exit, and a second `hide()` has to finish it rather than no-op on the flag.
     */
    public suspend fun hide() {
        if (hidden && presentationAnimatable.value <= 0f) return
        if (!confirmValueChange(PopupValue.Hidden)) return
        if (draggable.targetValue == PopupValue.Expanded) draggable.animateTo(PopupValue.Collapsed, snapSpec)
        // Claim Hidden *before* the slide, not after: if the caller's coroutine is cancelled
        // mid-animation (a quick source switch re-running a LaunchedEffect), the state must read as
        // "hidden, still finishing its exit" — which present() heals — and not as "presented" with a
        // half-slid bar, which no later present() would ever touch again.
        hidden = true
        presentationAnimatable.animateTo(0f, presentationSpec)
    }

    public suspend fun snapTo(value: PopupValue) {
        if (!confirmValueChange(value)) return
        when (value) {
            PopupValue.Hidden -> { draggable.snapTo(PopupValue.Collapsed); presentationAnimatable.snapTo(0f); hidden = true }
            PopupValue.Collapsed -> { draggable.snapTo(PopupValue.Collapsed); presentationAnimatable.snapTo(1f); hidden = false }
            PopupValue.Expanded -> { draggable.snapTo(PopupValue.Expanded); presentationAnimatable.snapTo(1f); hidden = false }
        }
    }

    public companion object {
        public fun Saver(confirmValueChange: (PopupValue) -> Boolean): Saver<PopupState, PopupValue> = Saver(
            save = { it.currentValue },
            restore = { PopupState(it, confirmValueChange) },
        )
    }
}

@Composable
public fun rememberPopupState(
    initialValue: PopupValue = PopupValue.Hidden,
    confirmValueChange: (PopupValue) -> Boolean = { true },
): PopupState = rememberSaveable(saver = PopupState.Saver(confirmValueChange)) {
    PopupState(initialValue, confirmValueChange)
}
