package com.otiumframe.ui

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.VideoView
import com.otiumframe.R
import com.otiumframe.core.FrameMode
import com.otiumframe.core.FrameModePolicy
import com.otiumframe.media.BitmapLoader
import com.otiumframe.media.FrameSettings
import com.otiumframe.media.MediaItem
import com.otiumframe.media.MediaRepository
import com.otiumframe.service.ParkWatchService
import com.otiumframe.vehicle.DemoVehicleController
import com.otiumframe.vehicle.Gear
import com.otiumframe.vehicle.VehicleModule
import com.otiumframe.vehicle.VehicleSignalSource
import com.otiumframe.vehicle.VehicleState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 프레임 화면. 기어가 P일 때만 사진·영상을 재생한다.
 *
 * - P단(REST): 슬라이드쇼 재생.
 * - P단이 아니게 되면: 재생을 멈추고 화면을 닫아 원래 보던 화면으로 돌아간다.
 *   (P가 아닌 상태에서 사용자가 직접 열었다면 아무것도 재생하지 않고 안내만 보여준다.)
 * - 화면을 두 번 탭하면 닫힌다.
 * - 오른쪽 아래 '화면 자동 전환' 토글이 백그라운드 P기어 감지 서비스를 켜고 끈다.
 */
class FrameActivity : Activity() {

    private lateinit var settings: FrameSettings
    private lateinit var repository: MediaRepository
    private lateinit var vehicle: VehicleSignalSource
    private val demo: DemoVehicleController? = VehicleModule.demoController

    private lateinit var border: FrameLayout
    private lateinit var imageFront: ImageView
    private lateinit var imageBack: ImageView
    private lateinit var video: VideoView
    private lateinit var clockGroup: View
    private lateinit var status: TextView
    private lateinit var settingsButton: Button
    private lateinit var ambientChip: TextView
    private lateinit var idleHint: TextView
    private lateinit var dismissHint: TextView
    private lateinit var autoSwitch: Switch
    private lateinit var demoPanel: LinearLayout

    private val main = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val advance = Runnable { showNext() }

    private var items: List<MediaItem> = emptyList()
    private var index = 0
    private var mode: FrameMode? = null
    /** 이번에 화면이 보이는 동안 한 번이라도 재생했는지. 재생 중에 P를 벗어나면 화면을 닫는다. */
    private var hasPlayed = false
    private var vehicleState = VehicleState()
    /** 비동기 로딩 결과가 이미 지나간 화면에 반영되지 않도록 하는 세대 번호 */
    private var generation = 0
    private var borderColor = 0xFF000000.toInt()
    private var borderAnimator: ValueAnimator? = null
    private val demoObserver: () -> Unit = { renderDemoPanel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_frame)
        settings = FrameSettings(this)
        repository = MediaRepository(this)
        vehicle = VehicleModule.create(this)
        index = savedInstanceState?.getInt(KEY_INDEX) ?: 0

        border = findViewById(R.id.frame_border)
        imageFront = findViewById(R.id.image_front)
        imageBack = findViewById(R.id.image_back)
        video = findViewById(R.id.video)
        clockGroup = findViewById(R.id.clock_group)
        status = findViewById(R.id.status)
        settingsButton = findViewById(R.id.settings_button)
        ambientChip = findViewById(R.id.ambient_chip)
        idleHint = findViewById(R.id.idle_hint)
        dismissHint = findViewById(R.id.dismiss_hint)
        autoSwitch = findViewById(R.id.auto_switch)
        demoPanel = findViewById(R.id.demo_panel)

        settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        video.setOnPreparedListener { player ->
            player.setVolume(0f, 0f) // 다른 미디어 앱과 동시 재생하지 않도록 무음 재생
            player.isLooping = false
        }
        video.setOnCompletionListener { showNext() }
        video.setOnErrorListener { _, _, _ -> showNext(); true }

        setupDoubleTapToDismiss()
        setupAutoSwitch()
        setupDemoPanel()
        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
        enterImmersive()
        items = repository.items()
        if (index >= items.size) index = 0
        clockGroup.visibility = if (settings.showClock) View.VISIBLE else View.GONE
        syncAutoSwitch()
        mode = null
        hasPlayed = false
        vehicle.start(::onVehicleState)
        demo?.addObserver(demoObserver)
        renderDemoPanel()
    }

    override fun onStop() {
        isVisible = false
        vehicle.stop()
        demo?.removeObserver(demoObserver)
        stopPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 보던 위치만 저장한다. 재생 재개는 onStart 에서 차량 상태를 다시 받은 뒤에만 한다.
        outState.putInt(KEY_INDEX, index)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun onVehicleState(state: VehicleState) {
        vehicleState = state
        renderStatus()
        val next = FrameModePolicy.modeFor(state)
        if (next == mode) return
        mode = next
        when (next) {
            FrameMode.REST -> enterRest()
            FrameMode.DRIVING -> leaveRest()
        }
    }

    // region 모드 전환

    private fun enterRest() {
        hasPlayed = true
        idleHint.visibility = View.GONE
        settingsButton.visibility = View.VISIBLE
        showDismissHint()
        showItem(index)
    }

    private fun leaveRest() {
        stopPlayback()
        if (hasPlayed) {
            // 재생 중에 P를 벗어났다: 화면을 닫고 원래 화면으로 돌아간다.
            finish()
            return
        }
        // P가 아닌 상태에서 직접 열었다: 아무것도 재생하지 않고 안내만 보여준다.
        imageFront.setImageDrawable(null)
        imageBack.setImageDrawable(null)
        ambientChip.visibility = View.GONE
        setBorder(0xFF000000.toInt(), padding = false, animate = false)
        idleHint.visibility = View.VISIBLE
    }

    private fun stopPlayback() {
        generation++
        main.removeCallbacks(advance)
        if (video.visibility == View.VISIBLE || video.isPlaying) {
            video.stopPlayback()
            video.visibility = View.GONE
        }
    }

    // endregion

    // region 슬라이드쇼

    private fun showNext() {
        if (mode != FrameMode.REST || items.isEmpty()) return
        index = (index + 1) % items.size
        showItem(index)
    }

    private fun showItem(position: Int) {
        if (items.isEmpty() || mode != FrameMode.REST) return
        main.removeCallbacks(advance)
        val token = ++generation
        when (val item = items[position]) {
            is MediaItem.Photo -> {
                if (video.visibility == View.VISIBLE) {
                    video.stopPlayback()
                    video.visibility = View.GONE
                }
                loadAsync({ BitmapLoader.loadPhoto(this, item.uri, stageWidth(), stageHeight()) }) { bitmap ->
                    if (token != generation || mode != FrameMode.REST) return@loadAsync
                    if (bitmap == null) {
                        showNext()
                        return@loadAsync
                    }
                    crossfadeTo(bitmap)
                    applyColor(BitmapLoader.dominantColor(bitmap))
                    main.postDelayed(advance, settings.slideIntervalSec * 1_000L)
                }
            }
            is MediaItem.Gif -> {
                if (video.visibility == View.VISIBLE) {
                    video.stopPlayback()
                    video.visibility = View.GONE
                }
                loadAsync({ BitmapLoader.loadGif(this, item.uri) }) { loaded ->
                    if (token != generation || mode != FrameMode.REST) return@loadAsync
                    if (loaded == null) {
                        showNext()
                        return@loadAsync
                    }
                    val (drawable, firstFrame) = loaded
                    crossfadeTo(drawable)
                    applyColor(BitmapLoader.dominantColor(firstFrame))
                    // GIF 는 슬라이드 간격 대신 한 바퀴 다 돌면 넘어간다. 움직이지 않는 GIF 면 사진처럼 간격대로.
                    val anim = drawable as? AnimatedImageDrawable
                    if (anim == null) {
                        main.postDelayed(advance, settings.slideIntervalSec * 1_000L)
                    } else {
                        anim.repeatCount = 0
                        anim.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                            override fun onAnimationEnd(d: Drawable?) {
                                if (token == generation && mode == FrameMode.REST) showNext()
                            }
                        })
                        anim.start()
                    }
                }
            }
            is MediaItem.Video -> {
                if (!FrameModePolicy.isVideoAllowed(mode ?: FrameMode.DRIVING)) return
                loadAsync({ BitmapLoader.loadVideoFrame(this, item.uri) }) { frame ->
                    if (token != generation || mode != FrameMode.REST) return@loadAsync
                    frame?.let {
                        crossfadeTo(it)
                        applyColor(BitmapLoader.dominantColor(it))
                    }
                    video.visibility = View.VISIBLE
                    video.setVideoURI(item.uri)
                    video.start()
                }
            }
        }
    }

    private fun crossfadeTo(bitmap: Bitmap) = crossfadeTo(BitmapDrawable(resources, bitmap))

    private fun crossfadeTo(drawable: Drawable) {
        (imageFront.drawable as? AnimatedImageDrawable)?.stop()
        imageBack.setImageDrawable(imageFront.drawable)
        imageBack.alpha = 1f
        imageFront.animate().cancel()
        imageFront.alpha = 0f
        imageFront.setImageDrawable(drawable)
        imageFront.animate().alpha(1f).setDuration(CROSSFADE_MS).start()
    }

    // endregion

    // region 색 연출 (앰비언트 라이트 / 테두리 대체)

    private fun applyColor(color: Int) {
        if (!settings.ambientSync) {
            setBorder(0xFF000000.toInt(), padding = false, animate = false)
            ambientChip.visibility = View.GONE
            return
        }
        vehicle.setAmbientColor(color) { applied ->
            main.post {
                if (mode != FrameMode.REST) return@post
                ambientChip.visibility = View.VISIBLE
                ambientChip.compoundDrawablesRelative[0]?.mutate()?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
                if (applied) {
                    ambientChip.setText(R.string.ambient_synced)
                    setBorder(0xFF000000.toInt(), padding = false, animate = false)
                } else {
                    ambientChip.setText(R.string.ambient_fallback)
                    setBorder(color, padding = true, animate = true)
                }
            }
        }
    }

    private fun setBorder(color: Int, padding: Boolean, animate: Boolean) {
        val px = if (padding) (BORDER_DP * resources.displayMetrics.density).toInt() else 0
        border.setPadding(px, px, px, px)
        borderAnimator?.cancel()
        if (!animate) {
            borderColor = color
            border.setBackgroundColor(color)
            return
        }
        borderAnimator = ValueAnimator.ofObject(ArgbEvaluator(), borderColor, color).apply {
            duration = CROSSFADE_MS
            addUpdateListener {
                borderColor = it.animatedValue as Int
                border.setBackgroundColor(borderColor)
            }
            start()
        }
    }

    // endregion

    // region 더블 탭 닫기, 자동 전환 토글

    private fun setupDoubleTapToDismiss() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                finish()
                return true
            }
        })
        // 버튼·토글 위의 터치는 각 위젯이 먼저 처리하므로, 빈 화면을 두 번 탭할 때만 닫힌다.
        findViewById<View>(R.id.stage).setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    private fun showDismissHint() {
        dismissHint.animate().cancel()
        dismissHint.alpha = 1f
        dismissHint.animate().alpha(0f).setStartDelay(HINT_MS).setDuration(CROSSFADE_MS).start()
    }

    private fun setupAutoSwitch() {
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked == settings.autoLaunch) return@setOnCheckedChangeListener
            settings.autoLaunch = checked
            if (checked) ParkWatchService.start(this) else ParkWatchService.stop(this)
        }
    }

    /** 설정 화면에서 바뀌었을 수 있으므로 화면에 돌아올 때마다 맞춘다. 켜져 있으면 서비스가 살아 있는지도 확인한다. */
    private fun syncAutoSwitch() {
        autoSwitch.isChecked = settings.autoLaunch
        if (settings.autoLaunch) ParkWatchService.start(this)
    }

    // endregion

    private fun renderStatus() {
        val s = vehicleState
        status.text = when {
            s.charging && s.batteryPercent != null -> getString(R.string.status_charging, s.batteryPercent)
            s.charging -> getString(R.string.status_charging_unknown)
            else -> getString(R.string.status_parked)
        }
    }

    // region DEMO 패널 (mock 빌드 전용)

    private fun setupDemoPanel() {
        val controller = demo ?: return
        val toggle = findViewById<Button>(R.id.demo_toggle)
        toggle.visibility = View.VISIBLE
        toggle.setOnClickListener {
            demoPanel.visibility = if (demoPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        fun gear(id: Int, gear: Gear, speed: Float) = findViewById<Button>(id).setOnClickListener {
            controller.update { it.copy(gear = gear, speedKmh = speed) }
        }
        gear(R.id.demo_p, Gear.P, 0f)
        gear(R.id.demo_r, Gear.R, 3f)
        gear(R.id.demo_n, Gear.N, 0f)
        gear(R.id.demo_d, Gear.D, 30f)
        findViewById<Button>(R.id.demo_brake).setOnClickListener {
            controller.update { it.copy(parkBrakeOn = !it.parkBrakeOn) }
        }
        findViewById<Button>(R.id.demo_charge).setOnClickListener {
            controller.update { it.copy(charging = !it.charging) }
        }
        findViewById<Button>(R.id.demo_ambient).setOnClickListener {
            controller.ambientSupported = !controller.ambientSupported
            if (mode == FrameMode.REST) showItem(index)
        }
    }

    private fun renderDemoPanel() {
        val controller = demo ?: return
        val s = controller.state
        findViewById<TextView>(R.id.demo_state).text =
            "기어 ${s.gear} · ${s.speedKmh.toInt()}km/h · 주차브레이크 ${onOff(s.parkBrakeOn)} · 충전 ${onOff(s.charging)}\n" +
                "→ 화면 모드: ${FrameModePolicy.modeFor(s)} · 신호: ${vehicle.displayName}"
        findViewById<Button>(R.id.demo_brake).text = "주차브레이크 ${onOff(s.parkBrakeOn)}"
        findViewById<Button>(R.id.demo_charge).text = "충전 ${onOff(s.charging)}"
        findViewById<Button>(R.id.demo_ambient).text = "앰비언트 ${if (controller.ambientSupported) "지원" else "미지원"}"
    }

    private fun onOff(value: Boolean) = if (value) "ON" else "OFF"

    // endregion

    private fun <T> loadAsync(work: () -> T, onLoaded: (T) -> Unit) {
        if (io.isShutdown) return
        io.execute {
            val result = work()
            main.post { if (!isDestroyed) onLoaded(result) }
        }
    }

    private fun stageWidth() = resources.displayMetrics.widthPixels

    private fun stageHeight() = resources.displayMetrics.heightPixels

    private fun enterImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    companion object {
        private const val KEY_INDEX = "index"
        private const val CROSSFADE_MS = 900L
        private const val HINT_MS = 3_000L
        private const val BORDER_DP = 10

        /** 서비스가 화면을 중복 실행하지 않도록 쓰는 표시 상태 */
        @Volatile
        var isVisible = false
            private set
    }
}
