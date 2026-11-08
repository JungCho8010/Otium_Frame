package com.otiumframe.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.otiumframe.R
import com.otiumframe.core.QrCode
import com.otiumframe.media.FrameSettings
import com.otiumframe.media.MediaRepository
import com.otiumframe.media.UploadServer
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 폰에서 사진 가져오기: 업로드 서버를 띄우고 그 주소를 QR 로 보여준다. 화면이 보이는 동안만 서버가 돈다.
 * 받은 파일은 바로 재생 목록에 들어간다.
 */
class UploadActivity : Activity() {

    private lateinit var settings: FrameSettings
    private lateinit var repository: MediaRepository
    private lateinit var qr: ImageView
    private lateinit var urlText: TextView
    private lateinit var statusText: TextView
    private val main = Handler(Looper.getMainLooper())
    private var server: UploadServer? = null
    private var received = 0

    /** 디버그 빌드 전용: 에뮬레이터 시연 때 QR 에 PC 주소를 담기 위한 덮어쓰기.
     *  adb shell am broadcast -a com.otiumframe.UPLOAD_HOST --es host 192.168.0.5:18787   (빈 값이면 해제) */
    private val hostOverride = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            settings.uploadHost = intent.getStringExtra("host").orEmpty()
            stopServer()
            startServer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)
        title = getString(R.string.upload_title)
        settings = FrameSettings(this)
        repository = MediaRepository(this)
        qr = findViewById(R.id.qr)
        urlText = findViewById(R.id.upload_url)
        statusText = findViewById(R.id.upload_status)
        findViewById<Button>(R.id.upload_close).setOnClickListener { finish() }
    }

    override fun onStart() {
        super.onStart()
        startServer()
        if (isDebuggable) {
            val filter = IntentFilter(ACTION_HOST)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(hostOverride, filter, Context.RECEIVER_EXPORTED) else registerReceiver(hostOverride, filter)
        }
    }

    override fun onStop() {
        if (isDebuggable) runCatching { unregisterReceiver(hostOverride) }
        stopServer()
        super.onStop()
    }

    private fun startServer() {
        val page = assets.open("upload.html").use { it.readBytes().toString(Charsets.UTF_8) }
        val s = UploadServer(repository.uploadsDir, page) { file, _ ->
            main.post {
                repository.select(file)
                received++
                renderStatus()
            }
        }
        val port = try {
            s.start(PORT)
        } catch (e: Exception) {
            Log.w(TAG, "포트 $PORT 사용 불가, 임의 포트로", e)
            s.start(0)
        }
        server = s
        val override = settings.uploadHost.takeIf { isDebuggable && it.isNotBlank() }
        val address = wifiAddress()
        val host = override ?: address?.let { "$it:$port" }
        if (host == null) {
            urlText.text = getString(R.string.upload_no_wifi)
            qr.setImageDrawable(null)
        } else {
            val url = "http://$host/${s.token}"
            Log.i(TAG, "업로드 주소 $url")
            urlText.text = url
            qr.setImageBitmap(render(QrCode.encode(url), 560))
        }
        renderStatus()
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    private fun renderStatus() {
        statusText.text = getString(R.string.upload_received, received)
    }

    private val isDebuggable: Boolean
        get() = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** wlan 인터페이스의 IPv4 를 우선, 없으면 루프백이 아닌 아무 IPv4. */
    private fun wifiAddress(): String? {
        val candidates = runCatching { NetworkInterface.getNetworkInterfaces().toList() }.getOrDefault(emptyList())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic -> nic.inetAddresses.toList().filterIsInstance<Inet4Address>().map { nic.name to it.hostAddress } }
        return (candidates.firstOrNull { it.first.startsWith("wlan") } ?: candidates.firstOrNull())?.second
    }

    private fun render(modules: Array<BooleanArray>, px: Int): Bitmap {
        val quiet = 4
        val n = modules.size + quiet * 2
        val scale = maxOf(1, px / n)
        val size = n * scale
        val pixels = IntArray(size * size) { Color.WHITE }
        for (y in modules.indices) for (x in modules.indices) if (modules[y][x]) {
            for (dy in 0 until scale) for (dx in 0 until scale) {
                pixels[((y + quiet) * scale + dy) * size + (x + quiet) * scale + dx] = Color.BLACK
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val TAG = "OtiumUpload"
        private const val PORT = 8787
        private const val ACTION_HOST = "com.otiumframe.UPLOAD_HOST"
    }
}
