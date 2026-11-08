package com.otiumframe.core

import com.otiumframe.vehicle.Gear
import com.otiumframe.vehicle.VehicleState

enum class FrameMode {
    /** P단 정차: 사진·영상 재생 */
    REST,

    /** P단이 아니거나 상태를 알 수 없음: 아무것도 재생하지 않는다 */
    DRIVING,
}

/**
 * 차량 신호 → 화면 모드 결정 규칙. 판단이 애매하면 항상 DRIVING 쪽으로 기운다(fail-safe).
 *
 * REST 조건: 기어 P + 정차(0.5km/h 미만) + 주행 제한(CarUxRestrictions) 없음.
 */
object FrameModePolicy {
    const val STOPPED_SPEED_KMH = 0.5f

    fun modeFor(state: VehicleState): FrameMode {
        val rest = state.gear == Gear.P &&
            state.speedKmh < STOPPED_SPEED_KMH &&
            !state.drivingRestricted
        return if (rest) FrameMode.REST else FrameMode.DRIVING
    }

    fun isVideoAllowed(mode: FrameMode): Boolean = mode == FrameMode.REST

    /** P단으로 막 들어온 순간에만 화면을 자동 전환한다. 처음 받은 신호(previous == null)로는 전환하지 않는다. */
    fun shouldAutoLaunch(previous: VehicleState?, current: VehicleState): Boolean {
        if (previous == null) return false
        return modeFor(previous) != FrameMode.REST && modeFor(current) == FrameMode.REST
    }
}
