package com.otiumframe.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.otiumframe.R
import com.otiumframe.media.FrameSettings
import com.otiumframe.media.MediaRepository
import com.otiumframe.service.ParkWatchService
import com.otiumframe.vehicle.VehicleModule

/**
 * 재생 목록(폰에서 가져오기 → 선택)과 동작 옵션. distractionOptimized 가 아니므로 주행 중에는 시스템이 가린다.
 * 차량에는 뒤로 가기 버튼이 없으므로 화면 안에 '돌아가기' 버튼을 둔다.
 */
class SettingsActivity : Activity() {

    private lateinit var settings: FrameSettings
    private lateinit var repository: MediaRepository
    private lateinit var mediaCount: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = getString(R.string.settings_title)
        settings = FrameSettings(this)
        repository = MediaRepository(this)
        mediaCount = findViewById(R.id.media_count)

        findViewById<TextView>(R.id.vehicle_source).text =
            getString(R.string.settings_vehicle_source, VehicleModule.create(this).displayName)

        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        findViewById<Button>(R.id.select_media).setOnClickListener {
            startActivity(Intent(this, SelectActivity::class.java))
        }
        findViewById<Button>(R.id.import_phone).setOnClickListener {
            startActivity(Intent(this, UploadActivity::class.java))
        }
        findViewById<Button>(R.id.clear_media).setOnClickListener {
            repository.clear()
            renderCount()
        }

        val intervalLabel = findViewById<TextView>(R.id.interval_label)
        val interval = findViewById<SeekBar>(R.id.interval)
        interval.max = FrameSettings.MAX_INTERVAL - FrameSettings.MIN_INTERVAL
        interval.progress = settings.slideIntervalSec - FrameSettings.MIN_INTERVAL
        intervalLabel.text = getString(R.string.settings_interval, settings.slideIntervalSec)
        interval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                settings.slideIntervalSec = progress + FrameSettings.MIN_INTERVAL
                intervalLabel.text = getString(R.string.settings_interval, settings.slideIntervalSec)
            }

            override fun onStartTrackingTouch(bar: SeekBar) = Unit

            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })

        bindSwitch(R.id.ambient_switch, settings.ambientSync) { settings.ambientSync = it }
        bindSwitch(R.id.clock_switch, settings.showClock) { settings.showClock = it }
        bindSwitch(R.id.auto_launch_switch, settings.autoLaunch) { enabled ->
            settings.autoLaunch = enabled
            if (enabled) ParkWatchService.start(this) else ParkWatchService.stop(this)
        }
        renderCount()
    }

    override fun onResume() {
        super.onResume()
        renderCount() // 선택·가져오기 화면에서 돌아오면 개수 갱신
    }

    private fun renderCount() {
        mediaCount.text = getString(R.string.settings_media_count, repository.selectedCount(), repository.totalCount())
    }

    private fun bindSwitch(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        findViewById<Switch>(id).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
    }
}
