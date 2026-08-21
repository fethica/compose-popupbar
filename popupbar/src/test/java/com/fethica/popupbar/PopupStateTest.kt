package com.fethica.popupbar

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PopupStateTest {
    @Test fun `starts hidden with zero progress and presentation`() {
        val s = PopupState(PopupValue.Hidden) { true }
        assertTrue(s.isHidden); assertEquals(0f, s.progress, 0f); assertEquals(0f, s.presentation, 0f)
    }

    @Test fun `snapTo moves between all three values without animation`() = runTest {
        val s = PopupState(PopupValue.Hidden) { true }
        s.updateTravel(1000f)
        s.snapTo(PopupValue.Collapsed)
        assertTrue(s.isCollapsed); assertEquals(1f, s.presentation, 0f); assertEquals(0f, s.progress, 0f)
        s.snapTo(PopupValue.Expanded)
        assertTrue(s.isExpanded); assertEquals(1f, s.progress, 0f)
        s.snapTo(PopupValue.Hidden)
        assertTrue(s.isHidden); assertEquals(0f, s.presentation, 0f); assertEquals(0f, s.progress, 0f)
    }

    @Test fun `confirmValueChange can veto`() = runTest {
        val s = PopupState(PopupValue.Collapsed) { it != PopupValue.Expanded }
        s.updateTravel(1000f)
        s.snapTo(PopupValue.Expanded)
        assertFalse(s.isExpanded)
    }

    @Test fun `saver round trips the value`() {
        val saver = PopupState.Saver { true }
        val s = PopupState(PopupValue.Expanded) { true }
        val saved = with(saver) { TestSaverScope.save(s) }
        val restored = saver.restore(saved!!)
        assertEquals(PopupValue.Expanded, restored!!.currentValue)
    }

    /**
     * C1: a caller whose coroutine dies mid-`hide()` (FRadio's `LaunchedEffect(hasNowPlaying)` on a
     * quick station switch) used to strand the bar half-slid with `hidden == false`, and every later
     * `present()` short-circuited on that flag. `hide()` now claims Hidden before it animates, so the
     * interrupted state is "hidden, still visible", which `present()` heals by animating the rest.
     */
    @Test fun `a hide cancelled mid animation is healed by the next present`() = runTest {
        val s = PopupState(PopupValue.Collapsed) { true }
        s.updateTravel(1000f)

        val hideJob = launch(SteppingFrameClock()) { s.hide() }
        advanceTimeBy(150)
        val stranded = s.presentation
        assertTrue("hide must be caught mid-slide, was $stranded", stranded in 0.01f..0.99f)
        hideJob.cancel()
        advanceUntilIdle()
        assertEquals("a cancelled hide leaves the bar where it stopped", stranded, s.presentation, 0f)

        launch(SteppingFrameClock()) { s.present() }
        advanceUntilIdle()

        assertEquals(1f, s.presentation, 0.001f)
        assertTrue(s.isCollapsed)
        assertEquals(0f, s.progress, 0f)
    }

    @Test fun `hide during an in-flight expand collapses first and then hides`() = runTest {
        val s = PopupState(PopupValue.Collapsed) { true }
        s.updateTravel(1000f)

        launch(SteppingFrameClock()) { s.expand() }
        advanceTimeBy(80)
        assertTrue("expand must still be in flight, was ${s.progress}", s.progress in 0.01f..0.99f)

        launch(SteppingFrameClock()) { s.hide() }
        advanceUntilIdle()

        assertTrue(s.isHidden)
        assertEquals(0f, s.presentation, 0.001f)
        // Collapsed offset == travel: the popup was walked back down before the bar slid away, so
        // re-presenting later starts from the bar and not from a full-screen popup.
        assertEquals(1000f, s.draggable.offset, 0.5f)
        assertEquals(PopupValue.Collapsed, s.draggable.settledValue)
    }

    @Test fun `expand stays hidden when the implicit present is vetoed`() = runTest {
        val s = PopupState(PopupValue.Hidden) { it != PopupValue.Collapsed }
        s.updateTravel(1000f)

        launch(SteppingFrameClock()) { s.expand() }
        advanceUntilIdle()

        assertTrue(s.isHidden)
        assertEquals(0f, s.presentation, 0f)
        assertEquals(0f, s.progress, 0f)
        // The veto must stop the drag too: expanding an unpresented popup animates an invisible card.
        assertEquals(PopupValue.Collapsed, s.draggable.settledValue)
    }

    private object TestSaverScope : androidx.compose.runtime.saveable.SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}
