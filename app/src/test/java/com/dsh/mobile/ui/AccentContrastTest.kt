package com.dsh.mobile.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.dsh.mobile.ui.theme.readableAccent
import org.junit.Assert.*
import org.junit.Test

class AccentContrastTest {
    @Test fun accentsStayReadableOnBothDarkAndLightCards() {
        listOf(Color(0xFF23354B), Color(0xFFF7F3ED)).forEach { surface ->
            val adjusted = readableAccent(Color(0xFF4D6BFE), surface)
            val a = adjusted.luminance() + 0.05f
            val b = surface.luminance() + 0.05f
            assertTrue(maxOf(a, b) / minOf(a, b) >= 4.5f)
        }
    }

    @Test fun alreadyReadableAccentIsPreserved() {
        val accent = Color(0xFFCCD9FF)
        assertEquals(accent, readableAccent(accent, Color(0xFF102030)))
    }
}
