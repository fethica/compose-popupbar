package com.fethica.popupbar

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupGeometryTest {
    private val host = Rect(0f, 0f, 1080f, 2400f)
    private val bar = Rect(36f, 2000f, 1044f, 2192f) // floating bar: 12dp*3 inset, 64dp*3 tall

    @Test fun `clip rect is the bar at 0 and the host at 1`() {
        assertEquals(bar, popupClipRect(0f, bar, host))
        assertEquals(host, popupClipRect(1f, bar, host))
    }

    @Test fun `clip reaches full width and bottom by half way while the top follows progress linearly`() {
        val half = popupClipRect(0.5f, bar, host)
        assertEquals(0f, half.left, 0.01f)
        assertEquals(1080f, half.right, 0.01f)
        assertEquals(2400f, half.bottom, 0.01f)
        assertEquals(1000f, half.top, 0.01f) // lerp(2000, 0, 0.5)
        val quarter = popupClipRect(0.25f, bar, host)
        assertEquals(1500f, quarter.top, 0.01f)
        assertTrue(quarter.left in 0f..36f && quarter.left < 36f * 0.5f) // ease-out: already past half by 0.25/0.5
    }

    @Test fun `clip is monotonic in progress`() {
        var prev = popupClipRect(0f, bar, host)
        for (i in 1..100) {
            val r = popupClipRect(i / 100f, bar, host)
            assertTrue(r.left <= prev.left + 0.001f); assertTrue(r.top <= prev.top + 0.001f)
            assertTrue(r.right >= prev.right - 0.001f); assertTrue(r.bottom >= prev.bottom - 0.001f)
            prev = r
        }
    }

    @Test fun `clip radius goes from bar radius to expanded radius over the width phase`() {
        assertEquals(48f, popupClipRadius(0f, 48f, 0f), 0.001f)
        assertEquals(0f, popupClipRadius(0.5f, 48f, 0f), 0.001f)
        assertEquals(0f, popupClipRadius(1f, 48f, 0f), 0.001f)
    }

    @Test fun `image overlay sits in the bar slot at 0 and the content slot at 1`() {
        val barSlot = Rect(60f, 2024f, 204f, 2168f); val contentSlot = Rect(90f, 400f, 990f, 1300f)
        assertEquals(barSlot, imageOverlayRect(0f, barSlot, contentSlot))
        assertEquals(contentSlot, imageOverlayRect(1f, barSlot, contentSlot))
        val mid = imageOverlayRect(0.5f, barSlot, contentSlot)
        assertEquals(75f, mid.left, 0.01f) // easeInOutSine(0.5) == 0.5
    }

    @Test fun `image overlay radius follows the same eased journey as its bounds`() {
        assertEquals(24f, imageOverlayRadius(0f, 24f, 48f), 0f)
        assertEquals(36f, imageOverlayRadius(0.5f, 24f, 48f), 0.001f)
        assertEquals(48f, imageOverlayRadius(1f, 24f, 48f), 0f)
    }

    @Test fun `alphas follow the spec curves`() {
        assertEquals(1f, barAlpha(0f), 0f); assertEquals(0.5f, barAlpha(0.15f), 0.001f)
        assertEquals(0f, barAlpha(0.3f), 0f); assertEquals(0f, barAlpha(1f), 0f)
        assertEquals(0f, contentAlpha(0.2f), 0f); assertEquals(0.5f, contentAlpha(0.375f), 0.001f)
        assertEquals(1f, contentAlpha(0.55f), 0f); assertEquals(1f, contentAlpha(1f), 0f)
        assertEquals(0f, closeButtonAlpha(0.6f), 0f); assertEquals(1f, closeButtonAlpha(1f), 0f)
    }

    @Test fun `content inset counts the bar only as far as it is presented`() {
        assertEquals(240f, contentBottomInset(240f, 0f, 192f, 0f, 24f), 0f)
        assertEquals(456f, contentBottomInset(240f, 1f, 192f, 0f, 24f), 0f)
        assertEquals(348f, contentBottomInset(240f, 0.5f, 192f, 0f, 24f), 0f)
    }

    @Test fun `bottom bar slides out by its own height`() {
        assertEquals(0f, bottomBarTranslation(0f, 240f), 0f); assertEquals(120f, bottomBarTranslation(0.5f, 240f), 0f)
    }

    @Test fun `back peek shrinks the popup by at most 30 percent`() {
        assertEquals(1f, backPeekProgress(0f), 0f); assertEquals(0.7f, backPeekProgress(1f), 0.0001f)
    }

    @Test fun `progress from offset`() {
        assertEquals(0f, progressFromOffset(2000f, 2000f), 0f); assertEquals(1f, progressFromOffset(0f, 2000f), 0f)
        assertEquals(0f, progressFromOffset(0f, 0f), 0f) // no travel yet: never NaN
    }

    @Test fun `snap target by position then by velocity`() {
        val t = 2000f
        assertEquals(PopupValue.Collapsed, snapTarget(offset = 1800f, travel = t, velocity = 0f, from = PopupValue.Collapsed, positionalThreshold = 0.25f, velocityThreshold = 1000f))
        assertEquals(PopupValue.Expanded, snapTarget(offset = 1400f, travel = t, velocity = 0f, from = PopupValue.Collapsed, positionalThreshold = 0.25f, velocityThreshold = 1000f))
        assertEquals(PopupValue.Expanded, snapTarget(offset = 1900f, travel = t, velocity = -1500f, from = PopupValue.Collapsed, positionalThreshold = 0.25f, velocityThreshold = 1000f))
        assertEquals(PopupValue.Collapsed, snapTarget(offset = 100f, travel = t, velocity = 1500f, from = PopupValue.Expanded, positionalThreshold = 0.25f, velocityThreshold = 1000f))
        assertEquals(PopupValue.Expanded, snapTarget(offset = 300f, travel = t, velocity = 0f, from = PopupValue.Expanded, positionalThreshold = 0.25f, velocityThreshold = 1000f))
    }
}
