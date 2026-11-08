package com.otiumframe.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ColorExtractorTest {

    private fun channels(c: Int) = Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    @Test
    fun picksDominantVividHue_overGreyBackground() {
        val grey = 0xFF808080.toInt()
        val orange = 0xFFE07020.toInt()
        val pixels = IntArray(1024) { if (it % 3 == 0) orange else grey }
        val (r, g, b) = channels(ColorExtractor.dominantColor(pixels))
        assertTrue("expected orange-ish but was $r,$g,$b", r > g && g > b)
    }

    @Test
    fun brightensDarkColors() {
        val darkBlue = 0xFF101040.toInt()
        val (_, _, b) = channels(ColorExtractor.dominantColor(IntArray(256) { darkBlue }))
        assertTrue("blue channel should be boosted, was $b", b >= 180)
    }

    @Test
    fun greyImageFallsBackToAverage() {
        val grey = 0xFF505050.toInt()
        val (r, g, b) = channels(ColorExtractor.dominantColor(IntArray(64) { grey }))
        assertTrue(r == g && g == b)
    }
}
