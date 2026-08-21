package com.fethica.popupbar

import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A vertical drag that commits as soon as Snap's distance or velocity threshold is crossed. */
internal fun Modifier.popupSnapGesture(
    state: PopupState,
    scope: CoroutineScope,
    velocityThresholdPx: Float,
    onUserGesture: (Boolean) -> Unit,
): Modifier = pointerInput(state) {
    val tracker = VelocityTracker()
    var from = PopupValue.Collapsed
    var decided = false
    detectVerticalDragGestures(
        onDragStart = {
            tracker.resetTracking()
            from = state.draggable.settledValue
            decided = false
            onUserGesture(true)
            state.lastGestureWasUser = true
        },
        onVerticalDrag = { change, delta ->
            if (decided) return@detectVerticalDragGestures
            tracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
            state.draggable.dispatchRawDelta(delta)
            val target = snapTarget(
                offset = state.draggable.requireOffset(),
                travel = state.travel,
                velocity = tracker.calculateVelocity().y,
                from = from,
                positionalThreshold = PopupDefaults.snapPositionalThreshold,
                velocityThreshold = velocityThresholdPx,
            )
            if (target != from) {
                decided = true
                scope.launch { state.draggable.animateTo(target, state.snapSpec) }
            }
        },
        onDragEnd = {
            onUserGesture(false)
            if (!decided) scope.launch { state.draggable.animateTo(from, state.snapSpec) }
        },
        onDragCancel = {
            onUserGesture(false)
            if (!decided) scope.launch { state.draggable.animateTo(from, state.snapSpec) }
        },
    )
}

/** Hands unconsumed scroll deltas between scrollable popup content and the popup anchors. */
internal class PopupNestedScrollConnection(
    private val state: PopupState,
    private val velocityThresholdPx: Float,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (
            delta < 0f &&
            source == NestedScrollSource.UserInput &&
            state.draggable.requireOffset() > 0f
        ) {
            Offset(0f, state.draggable.dispatchRawDelta(delta))
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = if (source == NestedScrollSource.UserInput && available.y > 0f) {
        Offset(0f, state.draggable.dispatchRawDelta(available.y))
    } else {
        Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val velocity = available.y
        return if (velocity < 0f && state.draggable.requireOffset() > 0f) {
            settle(velocity)
            Velocity(0f, available.y)
        } else {
            Velocity.Zero
        }
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return if (available.y != 0f) {
            settle(available.y)
            Velocity(0f, available.y)
        } else {
            Velocity.Zero
        }
    }

    private suspend fun settle(velocity: Float) {
        val target = snapTarget(
            offset = state.draggable.requireOffset(),
            travel = state.travel,
            velocity = velocity,
            from = state.draggable.settledValue,
            positionalThreshold = PopupDefaults.positionalThreshold,
            velocityThreshold = velocityThresholdPx,
        )
        state.lastGestureWasUser = true
        state.draggable.animateTo(target, state.snapSpec)
    }
}
