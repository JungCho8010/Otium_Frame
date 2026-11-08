package com.otiumframe.vehicle

import android.os.Handler
import android.os.Looper

/**
 * 앱 프로세스 안에서 공유되는 가짜 차량. 시작 상태는 "P단 + 주차 브레이크 체결".
 * DEMO 패널, adb 브로드캐스트, 서비스, 화면이 모두 이 상태 하나를 본다.
 */
object MockVehicle : DemoVehicleController {
    private val main = Handler(Looper.getMainLooper())
    private val observers = linkedSetOf<() -> Unit>()

    override var state = VehicleState(gear = Gear.P, parkBrakeOn = true, speedKmh = 0f, charging = false, batteryPercent = 64)
        private set

    override var ambientSupported = true
        set(value) {
            field = value
            notifyObservers()
        }

    override var lastAmbientColor: Int? = null
        internal set

    override fun update(transform: (VehicleState) -> VehicleState) {
        val next = transform(state)
        if (next == state) return
        state = next
        notifyObservers()
    }

    override fun addObserver(observer: () -> Unit) {
        observers += observer
    }

    override fun removeObserver(observer: () -> Unit) {
        observers -= observer
    }

    private fun notifyObservers() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(::notifyObservers)
            return
        }
        observers.toList().forEach { it() }
    }
}
