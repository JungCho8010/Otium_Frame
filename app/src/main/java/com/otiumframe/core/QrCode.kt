package com.otiumframe.core

/**
 * 업로드 주소용 QR 코드 인코더 (바이트 모드, 오류정정 L, 버전 1~4, 마스크 0 고정).
 *
 * 외부 라이브러리 없이 쓰려고 범위를 줄였다: 버전 4 까지는 오류정정 블록이 하나뿐이라 인터리빙과 버전 정보 비트가 필요 없다.
 * 최대 78 바이트까지 담는다(URL 한 줄이면 충분). 마스크 평가는 생략하고 0 번을 쓴다 — 스캐너는 어떤 마스크든 읽는다.
 * 검증: QrCodeTest 가 segno(파이썬 기준 구현) 출력과 모듈 단위로 비교한다.
 */
object QrCode {
    const val MAX_BYTES = 78

    private val DATA_CODEWORDS = intArrayOf(19, 34, 55, 80)
    private val EC_CODEWORDS = intArrayOf(7, 10, 15, 20)
    private val ALIGNMENT_CENTER = intArrayOf(0, 18, 22, 26)

    /** true = 어두운 모듈. 조용한 영역(테두리)은 포함하지 않는다. */
    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BYTES) { "QR 에 담기엔 너무 깁니다: ${bytes.size} bytes" }
        val version = (1..4).first { bytes.size <= DATA_CODEWORDS[it - 1] - 2 }
        val size = 17 + 4 * version
        val data = dataCodewords(bytes, DATA_CODEWORDS[version - 1])
        val codewords = data + reedSolomon(data, EC_CODEWORDS[version - 1])

        // 2. 기능 패턴
        val modules = Array(size) { BooleanArray(size) }
        val function = Array(size) { BooleanArray(size) }
        fun set(x: Int, y: Int, dark: Boolean) { modules[y][x] = dark; function[y][x] = true }
        for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
        fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx; val y = cy + dy
                if (x in 0 until size && y in 0 until size) set(x, y, maxOf(Math.abs(dx), Math.abs(dy)).let { it != 2 && it != 4 })
            }
        }
        finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
        if (version >= 2) {
            val c = ALIGNMENT_CENTER[version - 1]
            for (dy in -2..2) for (dx in -2..2) set(c + dx, c + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
        }
        drawFormat(size, ::set)

        // 3. 데이터 배치(지그재그) + 마스크 0
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) for (j in 0..1) {
                val x = right - j
                val upward = ((right + 1) and 2) == 0
                val y = if (upward) size - 1 - vert else vert
                if (!function[y][x] && i < codewords.size * 8) {
                    modules[y][x] = (codewords[i ushr 3].toInt() ushr (7 - (i and 7))) and 1 == 1
                    i++
                }
            }
            right -= 2
        }
        for (y in 0 until size) for (x in 0 until size) if (!function[y][x] && (x + y) % 2 == 0) modules[y][x] = !modules[y][x]
        return modules
    }

    /**
     * 데이터 코드워드: 모드(0100) + 길이(8비트) + 바이트들 + 종료(최대 4비트 0) + 바이트 경계 패딩 + 패드 코드워드(0xEC, 0x11 교대).
     * ISO/IEC 18004 7.4.9~7.4.10. (참고: segno 는 이미 경계에 있어도 0 한 바이트를 더 넣는 버그가 있어 패딩 구간은 비교 기준으로 쓰지 않는다.)
     */
    internal fun dataCodewords(bytes: ByteArray, dataLen: Int): ByteArray {
        val bits = ArrayList<Boolean>(dataLen * 8)
        fun append(value: Int, count: Int) { for (i in count - 1 downTo 0) bits.add((value ushr i) and 1 == 1) }
        append(0b0100, 4)
        append(bytes.size, 8)
        for (b in bytes) append(b.toInt() and 0xFF, 8)
        repeat(minOf(4, dataLen * 8 - bits.size)) { bits.add(false) }
        while (bits.size % 8 != 0) bits.add(false)
        var pad = 0xEC
        while (bits.size < dataLen * 8) { append(pad, 8); pad = pad xor (0xEC xor 0x11) }
        return ByteArray(dataLen) { i -> (0 until 8).fold(0) { acc, j -> (acc shl 1) or (if (bits[i * 8 + j]) 1 else 0) }.toByte() }
    }

    /** 포맷 정보 15 비트: 오류정정 L(01) + 마스크 0 → BCH 부호화 후 0x5412 와 XOR. 결과는 0x77C4. */
    internal fun formatBits(): Int {
        val data = 0b01000
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        return ((data shl 10) or rem) xor 0x5412
    }

    private fun drawFormat(size: Int, set: (Int, Int, Boolean) -> Unit) {
        val bits = formatBits()
        fun bit(i: Int) = (bits ushr i) and 1 == 1
        for (i in 0..5) set(8, i, bit(i))
        set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
        for (i in 9..14) set(14 - i, 8, bit(i))
        for (i in 0..7) set(size - 1 - i, 8, bit(i))
        for (i in 8..14) set(8, size - 15 + i, bit(i))
        set(8, size - 8, true)
    }

    /** GF(256), 원시다항식 0x11D. [degree] 개의 오류정정 코드워드를 돌려준다. */
    private fun reedSolomon(data: ByteArray, degree: Int): ByteArray {
        val divisor = ByteArray(degree).also { it[degree - 1] = 1 }
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                divisor[j] = mul(divisor[j].toInt() and 0xFF, root).toByte()
                if (j + 1 < degree) divisor[j] = (divisor[j].toInt() xor divisor[j + 1].toInt()).toByte()
            }
            root = mul(root, 2)
        }
        val result = ByteArray(degree)
        for (b in data) {
            val factor = (b.toInt() xor result[0].toInt()) and 0xFF
            System.arraycopy(result, 1, result, 0, degree - 1)
            result[degree - 1] = 0
            for (j in 0 until degree) result[j] = (result[j].toInt() xor mul(divisor[j].toInt() and 0xFF, factor)).toByte()
        }
        return result
    }

    private fun mul(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z and 0xFF
    }
}
