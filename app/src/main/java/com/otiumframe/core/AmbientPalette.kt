package com.otiumframe.core

/**
 * 차량 앰비언트 라이트가 고를 수 있는 색은 RGB 가 아니라 몇 가지 프리셋이다
 * (Pleos Connect 에뮬레이터 기준: Cyan, Blue, Purple, White). 사진 대표 색을 가장 가까운 프리셋으로 바꾼다.
 */
enum class AmbientPreset(val hue: Float?) {
    CYAN(185f),
    BLUE(225f),
    PURPLE(280f),
    WHITE(null),
}

object AmbientPalette {
    private const val MIN_SATURATION = 0.18f

    fun nearest(argb: Int): AmbientPreset {
        val hsv = FloatArray(3)
        ColorExtractor.rgbToHsv((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF, hsv)
        if (hsv[1] < MIN_SATURATION) return AmbientPreset.WHITE
        return AmbientPreset.entries
            .filter { it.hue != null }
            .minBy { hueDistance(hsv[0], it.hue!!) }
    }

    private fun hueDistance(a: Float, b: Float): Float {
        val d = kotlin.math.abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }
}
