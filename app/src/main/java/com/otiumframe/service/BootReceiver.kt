package com.otiumframe.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.otiumframe.media.FrameSettings

/** 차량 시동(부팅) 후 주차 감지 서비스를 다시 켠다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && FrameSettings(context).autoLaunch) {
            ParkWatchService.start(context)
        }
    }
}
