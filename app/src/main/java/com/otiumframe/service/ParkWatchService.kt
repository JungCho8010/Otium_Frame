package com.otiumframe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.otiumframe.R
import com.otiumframe.core.FrameModePolicy
import com.otiumframe.media.FrameSettings
import com.otiumframe.ui.FrameActivity
import com.otiumframe.vehicle.VehicleModule
import com.otiumframe.vehicle.VehicleSignalSource
import com.otiumframe.vehicle.VehicleState

/**
 * 백그라운드에서 P기어 감지'만' 하는 가벼운 서비스. 사진·영상은 여기서 다루지 않는다.
 *
 * 실행 조건 (모두 '화면 자동 전환' 토글이 켜져 있을 때만)
 *  - 사용자가 토글을 켤 때
 *  - 차량 부팅 완료 시 (BootReceiver)
 *  - 앱 화면이 열릴 때 (서비스가 시스템에 의해 종료됐을 경우 복구)
 * 중단 조건
 *  - 사용자가 토글을 끌 때 (즉시 stopService)
 *  - 시작 시점에 토글이 꺼져 있으면 바로 stopSelf
 * 하는 일
 *  - 차량 신호 콜백만 구독한다(폴링·타이머·네트워크 없음).
 *  - P단으로 '바뀌는 순간'에만 프레임 화면을 연다. 사용자가 두 번 탭해 닫은 뒤에는 다음 P 진입 때까지 다시 열지 않는다.
 */
class ParkWatchService : Service() {

    private lateinit var vehicle: VehicleSignalSource
    private var previous: VehicleState? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        vehicle = VehicleModule.create(this, watchOnly = true)
        vehicle.start(::onVehicleState)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!FrameSettings(this).autoLaunch) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        vehicle.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun onVehicleState(state: VehicleState) {
        val launch = FrameModePolicy.shouldAutoLaunch(previous, state)
        previous = state
        if (!launch || FrameActivity.isVisible) return
        Log.i(TAG, "P기어 감지 → 화면 전환 ($state)")
        // Android 10+ 의 백그라운드 액티비티 실행 제한이 있어, 차량 OEM 정책상 허용되지 않으면 무시될 수 있다.
        runCatching {
            startActivity(Intent(this, FrameActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "자동 전환 실패", it) }
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, FrameActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_frame)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "OtiumParkWatch"
        private const val CHANNEL_ID = "park_watch"
        private const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, ParkWatchService::class.java)) }
                .onFailure { Log.w(TAG, "서비스 시작 실패", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ParkWatchService::class.java))
        }
    }
}
