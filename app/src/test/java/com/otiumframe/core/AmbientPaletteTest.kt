package com.otiumframe.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientPaletteTest {

    @Test
    fun mapsHuesToNearestPreset() {
        assertEquals(AmbientPreset.CYAN, AmbientPalette.nearest(0xFF40D0E0.toInt()))
        assertEquals(AmbientPreset.BLUE, AmbientPalette.nearest(0xFF2050E0.toInt()))
        assertEquals(AmbientPreset.PURPLE, AmbientPalette.nearest(0xFF9040D0.toInt()))
    }

    @Test
    fun lowSaturationIsWhite() {
        assertEquals(AmbientPreset.WHITE, AmbientPalette.nearest(0xFFE0E0E0.toInt()))
    }

    @Test
    fun warmColorsWrapAroundToPurple() {
        // 빨강·분홍 계열은 프리셋에 없으므로 색상환에서 가장 가까운 보라로 간다.
        assertEquals(AmbientPreset.PURPLE, AmbientPalette.nearest(0xFFE04080.toInt()))
    }
}
