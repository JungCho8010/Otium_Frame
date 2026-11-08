package com.otiumframe.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context

/**
 * 주행 제한 신호를 AAOS 표준 CarUxRestrictionsManager 로 받는다.
 * Pleos '주행 제한 개발 가이드'가 안내하는 공식 방식으로, UX_RESTRICTIONS_NO_VIDEO 가 켜지면 영상을 멈춰야 한다. 권한이 필요 없다.
 * 기어는 Pleos Vehicle SDK(Gear 도메인)로 받는다 → [PleosVehicleSignalSource].
 */
internal class AaosDrivingSignals(
    private val context: Context,
    private val publish: ((VehicleState) -> VehicleState) -> Unit,
) {
    private var car: Car? = null
    private var uxManager: CarUxRestrictionsManager? = null

    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
        applyUx(restrictions)
    }

    fun start() {
        if (car != null) return
        car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
            if (ready) onCarReady(connected) else onCarLost()
        }
    }

    fun stop() {
        runCatching { uxManager?.unregisterListener() }
        uxManager = null
        car?.disconnect()
        car = null
    }

    private fun onCarReady(connected: Car) {
        uxManager = (connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.also {
            it.registerListener(uxListener)
            applyUx(it.currentCarUxRestrictions)
        }
    }

    private fun onCarLost() {
        uxManager = null
        publish { it.copy(drivingRestricted = true) }
    }

    private fun applyUx(restrictions: CarUxRestrictions?) {
        val restricted = restrictions != null && restrictions.isRequiresDistractionOptimization &&
            (restrictions.activeRestrictions and CarUxRestrictions.UX_RESTRICTIONS_NO_VIDEO) != 0
        publish { it.copy(drivingRestricted = restricted) }
    }
}
