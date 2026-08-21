package com.fethica.popupbar

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers `PopupHost`'s predictive-back body. The cancellation case is the one that matters:
 * `ComposePredictiveBackHandler.onBackCancelled()` cancels the event channel *and then* the job the
 * handler body runs in (verified with `javap -c` on activity-compose 1.12.4), so a spring back that
 * suspends on that same job never runs a frame and strands the card mid-peek.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PopupBackGestureTest {

    /** Runs an animation to completion without a real choreographer. */
    private class ImmediateFrameClock : MonotonicFrameClock {
        private var nanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            nanos += 16_000_000L
            return onFrame(nanos)
        }
    }

    @Test fun `a peek moves the popup back by the back peek fraction`() = runTest {
        val state = PopupState(PopupValue.Expanded) { true }
        state.updateTravel(1000f)
        val events = Channel<Float>(Channel.UNLIMITED)
        val host = CoroutineScope(coroutineContext + ImmediateFrameClock())
        val job = launch(ImmediateFrameClock()) { state.handleBackGesture(events.receiveAsFlow(), host) }

        events.send(1f)
        runCurrent()
        assertEquals(1f - PopupDefaults.backPeekFraction, state.progress, 0.001f)

        job.cancel()
        events.close()
    }

    @Test fun `a cancelled back gesture springs back even though the handler job was cancelled`() = runTest {
        val state = PopupState(PopupValue.Expanded) { true }
        state.updateTravel(1000f)
        val events = Channel<Float>(Channel.UNLIMITED)
        val host = CoroutineScope(coroutineContext + ImmediateFrameClock())
        val job = launch(ImmediateFrameClock()) { state.handleBackGesture(events.receiveAsFlow(), host) }

        events.send(1f)
        runCurrent()
        assertEquals(0.7f, state.progress, 0.001f)

        // Exactly what onBackCancelled() does, in that order.
        events.cancel(CancellationException("onBack cancelled"))
        job.cancel()
        advanceUntilIdle()

        assertEquals("the popup must return to Expanded, not stay stranded mid-peek", 1f, state.progress, 0.001f)
        assertEquals(PopupValue.Expanded, state.currentValue)
    }

    @Test fun `a completed back gesture collapses and records a user settle`() = runTest {
        val state = PopupState(PopupValue.Expanded) { true }
        state.updateTravel(1000f)
        val events = Channel<Float>(Channel.UNLIMITED)
        val host = CoroutineScope(coroutineContext + ImmediateFrameClock())
        val job = launch(ImmediateFrameClock()) { state.handleBackGesture(events.receiveAsFlow(), host) }

        events.send(0.5f)
        runCurrent()
        events.close()          // onBackPressed() closes the channel; the job is not cancelled
        advanceUntilIdle()

        assertEquals(0f, state.progress, 0.001f)
        assertEquals(PopupValue.Collapsed, state.currentValue)
        assertTrue(state.lastGestureWasUser)
        job.cancel()
    }
}
