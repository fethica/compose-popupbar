package com.fethica.popupbar

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupProgressStripTest {
    private val width = 300f

    @Test fun `ltr fraction follows x directly`() {
        assertEquals(0f, seekFraction(0f, width, rtl = false), 0f)
        assertEquals(1f, seekFraction(width, width, rtl = false), 0f)
        assertEquals(0.5f, seekFraction(width / 2f, width, rtl = false), 0.001f)
    }

    @Test fun `rtl fraction mirrors x so the fill starts at the right`() {
        assertEquals(1f, seekFraction(0f, width, rtl = true), 0f)
        assertEquals(0f, seekFraction(width, width, rtl = true), 0f)
        assertEquals(0.5f, seekFraction(width / 2f, width, rtl = true), 0.001f)
    }

    @Test fun `x outside the strip clamps to 0 or 1`() {
        assertEquals(0f, seekFraction(-50f, width, rtl = false), 0f)
        assertEquals(1f, seekFraction(width + 50f, width, rtl = false), 0f)
        assertEquals(1f, seekFraction(-50f, width, rtl = true), 0f)
        assertEquals(0f, seekFraction(width + 50f, width, rtl = true), 0f)
    }

    @Test fun `zero or negative width never divides by zero`() {
        assertEquals(0f, seekFraction(10f, 0f, rtl = false), 0f)
        assertEquals(0f, seekFraction(10f, 0f, rtl = true), 0f)
        assertEquals(0f, seekFraction(10f, -5f, rtl = false), 0f)
    }
}
