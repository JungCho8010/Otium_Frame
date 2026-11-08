package com.otiumframe.vehicle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * [MockVehicle] 을 [VehicleSignalSource] 로 노출한다. 실행 중에는 아래 adb 명령으로도 신호를 바꿀 수 있다.
 *
 *   adb shell am broadcast -a com.otiumframe.MOCK_SIGNAL --es gear D --ef speed 30
 *   adb shell am broadcast -a com.otiumframe.MOCK_SIGNAL --es gear P --ez park_brake true --ez charging true
 */
class MockVehicleSignalSource(private val context: Context) : VehicleSignalSource {
    override val displayName = "Mock (DEMO 패널)"

    private var listener: ((VehicleState) -> Unit)? = null
    private val observer: () -> Unit = { listener?.invoke(MockVehicle.state) }
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            MockVehicle.update { current ->
                current.copy(
                    gear = intent.getStringExtra("gear")?.let { runCatching { Gear.valueOf(it.uppercase()) }.getOrNull() }
                        ?: current.gear,
                    speedKmh = if (intent.hasExtra("speed")) intent.getFloatExtra("speed", 0f) else current.speedKmh,
                    parkBrakeOn = if (intent.hasExtra("park_brake")) intent.getBooleanExtra("park_brake", false) else current.parkBrakeOn,
                    charging = if (intent.hasExtra("charging")) intent.getBooleanExtra("charging", false) else current.charging,
                    batteryPercent = if (intent.hasExtra("battery")) intent.getIntExtra("battery", 0) else current.batteryPercent,
                )
            }
            Log.i(TAG, "mock signal -> ${MockVehicle.state}")
        }
    }

    override fun start(listener: (VehicleState) -> Unit) {
        this.listener = listener
        MockVehicle.addObserver(observer)
        if (!receiverRegistered) {
            val filter = IntentFilter(ACTION_MOCK_SIGNAL)
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            receiverRegistered = true
        }
        listener(MockVehicle.state)
    }

    override fun stop() {
        MockVehicle.removeObserver(observer)
        listener = null
        if (receiverRegistered) {
            context.unregisterReceiver(receiver)
            receiverRegistered = false
        }
    }

    override fun setAmbientColor(argb: Int, onResult: (Boolean) -> Unit) {
        val ok = MockVehicle.ambientSupported
        if (ok) MockVehicle.lastAmbientColor = argb
        Log.i(TAG, "ambient color #%06X -> %s".format(argb and 0xFFFFFF, if (ok) "applied" else "unsupported"))
        onResult(ok)
    }

    companion object {
        const val ACTION_MOCK_SIGNAL = "com.otiumframe.MOCK_SIGNAL"
        private const val TAG = "OtiumMockVehicle"
    }
}
