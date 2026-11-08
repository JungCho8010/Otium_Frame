package com.otiumframe.core

import kotlin.math.max
import kotlin.math.min

/**
 * 사진의 대표 색을 고른다. 채도·명도가 높은 색에 가중치를 주는 12구간 색상 히스토그램 방식이라
 * 별도 라이브러리(Palette) 없이 동작한다. 입력은 축소된 ARGB 픽셀 배열이다.
 */
object ColorExtractor {
    private const val BUCKETS = 12
    private const val FALLBACK = 0xFF8FA3B8.toInt()

    fun dominantColor(pixels: IntArray): Int {
        if (pixels.isEmpty()) return FALLBACK
        val weight = DoubleArray(BUCKETS)
        val sumR = DoubleArray(BUCKETS)
        val sumG = DoubleArray(BUCKETS)
        val sumB = DoubleArray(BUCKETS)
        val hsv = FloatArray(3)
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            rgbToHsv(r, g, b, hsv)
            val w = (hsv[1] * hsv[2]).toDouble()
            if (w < 0.08) continue
            val bucket = min(BUCKETS - 1, (hsv[0] / 360f * BUCKETS).toInt())
            weight[bucket] += w
            sumR[bucket] += r * w
            sumG[bucket] += g * w
            sumB[bucket] += b * w
        }
        val best = weight.indices.maxByOrNull { weight[it] } ?: return FALLBACK
        if (weight[best] <= 0.0) return averageColor(pixels)
        val r = (sumR[best] / weight[best]).toInt()
        val g = (sumG[best] / weight[best]).toInt()
        val b = (sumB[best] / weight[best]).toInt()
        return boost(r, g, b)
    }

    private fun averageColor(pixels: IntArray): Int {
        var r = 0L; var g = 0L; var b = 0L
        for (p in pixels) {
            r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        val n = pixels.size
        return argb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /** 앰비언트 라이트/테두리로 쓸 때 너무 어둡지 않도록 명도를 끌어올린다. */
    private fun boost(r: Int, g: Int, b: Int): Int {
        val hsv = FloatArray(3)
        rgbToHsv(r, g, b, hsv)
        hsv[2] = max(hsv[2], 0.75f)
        return hsvToArgb(hsv)
    }

    internal fun rgbToHsv(r: Int, g: Int, b: Int, out: FloatArray) {
        val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
        val cmax = max(rf, max(gf, bf))
        val cmin = min(rf, min(gf, bf))
        val d = cmax - cmin
        val h = when {
            d == 0f -> 0f
            cmax == rf -> 60f * (((gf - bf) / d) % 6f)
            cmax == gf -> 60f * (((bf - rf) / d) + 2f)
            else -> 60f * (((rf - gf) / d) + 4f)
        }
        out[0] = if (h < 0f) h + 360f else h
        out[1] = if (cmax == 0f) 0f else d / cmax
        out[2] = cmax
    }

    internal fun hsvToArgb(hsv: FloatArray): Int {
        val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
    }

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}
