package com.fethica.popupbar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PopupGesturesTest {
    @Test fun `upward user scroll expands the popup before content scrolls`() = runTest {
        val state = PopupState(PopupValue.Collapsed) { true }
        state.updateTravel(1_000f)
        val connection = PopupNestedScrollConnection(state, this, velocityThresholdPx = 1_000f)

        val consumed = connection.onPreScroll(
            available = Offset(0f, -200f),
            source = NestedScrollSource.UserInput,
        )

        assertEquals(Offset(0f, -200f), consumed)
        assertEquals(0.2f, state.progress, 0.001f)
    }

    @Test fun `downward leftover scroll collapses the popup`() = runTest {
        val state = PopupState(PopupValue.Expanded) { true }
        state.updateTravel(1_000f)
        val connection = PopupNestedScrollConnection(state, this, velocityThresholdPx = 1_000f)

        val consumed = connection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 250f),
            source = NestedScrollSource.UserInput,
        )

        assertEquals(Offset(0f, 250f), consumed)
        assertEquals(0.75f, state.progress, 0.001f)
    }
}
