package com.otiumframe.media

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * 폰에서 사진·영상을 올리는 차량 내부용 HTTP 서버. QR 화면이 떠 있는 동안만 돈다.
 *
 * 외부 라이브러리 없이 ServerSocket 으로 두 요청만 처리한다.
 *  - GET  /{token}         업로드 페이지(assets/upload.html: 올리기 + 영상 구간/사진 여러 장 → GIF 만들기)
 *  - POST /{token}/upload  파일 본문 그대로(multipart 아님) + X-Name 헤더
 * 보안: URL 에 서버가 뜰 때마다 바뀌는 토큰, 이미지·영상 MIME 만, 파일당 200MB 제한, 요청당 30초 타임아웃.
 * HTTPS 가 아니므로 차량 내부 Wi-Fi(차량 핫스팟 또는 폰 핫스팟) 안에서만 쓴다.
 */
class UploadServer(private val dir: File, private val page: String, private val onFile: (File, String) -> Unit) {
    val token: String = "%08x".format(SecureRandom().nextInt())
    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    /** 모든 인터페이스의 [port] 에 묶는다(0 이면 임의 포트). 실제 포트를 돌려준다. */
    fun start(port: Int): Int {
        val server = ServerSocket(port)
        socket = server
        pool.execute {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute {
                    client.use { runCatching { handle(it) }.onFailure { e -> Log.w(TAG, "요청 처리 실패", e) } }
                }
            }
        }
        return server.localPort
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        pool.shutdownNow()
    }

    private fun handle(client: Socket) {
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream())
        val request = readLine(input)?.split(' ') ?: return
        if (request.size < 2) return
        val method = request[0]
        val path = request[1]
        val headers = HashMap<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        val out = client.getOutputStream()
        when {
            method == "GET" && (path == "/$token" || path == "/$token/") -> respond(out, 200, "text/html; charset=utf-8", page)
            method == "POST" && path == "/$token/upload" -> receive(input, headers, out)
            else -> respond(out, 404, TEXT, "not found")
        }
        out.flush()
    }

    private fun receive(input: InputStream, headers: Map<String, String>, out: OutputStream) {
        val length = headers["content-length"]?.toLongOrNull() ?: return respond(out, 411, TEXT, "length required")
        val type = headers["content-type"].orEmpty().substringBefore(';').trim()
        val name = withExtension(safeName(URLDecoder.decode(headers["x-name"].orEmpty(), "UTF-8")), type)
        if (!(type.startsWith("image/") || type.startsWith("video/"))) return respond(out, 415, TEXT, "이미지·영상만 올릴 수 있어요")
        if (length <= 0 || length > MAX_BYTES) return respond(out, 413, TEXT, "파일이 너무 커요 (최대 200MB)")
        dir.mkdirs()
        val file = File(dir, "${System.currentTimeMillis()}_$name")
        var remaining = length
        file.outputStream().use { fos ->
            val buf = ByteArray(64 * 1024)
            while (remaining > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                if (n < 0) break
                fos.write(buf, 0, n)
                remaining -= n
            }
        }
        if (remaining > 0) {
            file.delete()
            return respond(out, 400, TEXT, "incomplete")
        }
        onFile(file, type)
        respond(out, 200, "application/json", "{\"ok\":true}")
    }

    private fun respond(out: OutputStream, code: Int, type: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 411 -> "Length Required"; 413 -> "Payload Too Large"; 415 -> "Unsupported Media Type"; else -> "Error" }
        out.write("HTTP/1.1 $code $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n".toByteArray())
        out.write(bytes)
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return if (buf.size() == 0) null else buf.toString("UTF-8")
            if (b == '\n'.code) return buf.toString("UTF-8").trimEnd('\r')
            buf.write(b)
            if (buf.size() > 8192) return null
        }
    }

    /** 확장자가 없거나 모르는 것이면 MIME 으로 붙인다(재생 목록이 확장자로 사진/GIF/영상을 구분한다). */
    private fun withExtension(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext == "gif" || ext in MediaRepository.PHOTO_EXT || ext in MediaRepository.VIDEO_EXT) return name
        val fromMime = when (mime) {
            "image/jpeg" -> "jpg"
            "video/quicktime" -> "mov"
            else -> mime.substringAfter('/').substringBefore('+').take(5)
        }
        return "$name.$fromMime"
    }

    private fun safeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[^0-9A-Za-z가-힣._-]"), "_").trim('.', '_')
        return cleaned.ifEmpty { "file" }.take(80)
    }

    companion object {
        private const val TAG = "OtiumUpload"
        private const val TEXT = "text/plain; charset=utf-8"
        private const val MAX_BYTES = 200L * 1024 * 1024
    }
}
