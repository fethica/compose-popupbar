package com.fethica.popupbar

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private object TestSaverScope : androidx.compose.runtime.saveable.SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }
}
