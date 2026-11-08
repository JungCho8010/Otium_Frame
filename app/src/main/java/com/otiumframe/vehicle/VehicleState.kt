package com.otiumframe.vehicle

enum class Gear { P, R, N, D, UNKNOWN }

/** 앱이 필요로 하는 차량 신호만 모은 스냅샷. SDK 종류와 무관하게 이 형태로 통일한다. */
data class VehicleState(
    val gear: Gear = Gear.UNKNOWN,
    val parkBrakeOn: Boolean = false,
    val speedKmh: Float = 0f,
    val charging: Boolean = false,
    val batteryPercent: Int? = null,
    /** AAOS CarUxRestrictions 가 주행 제한(Distraction Optimization 요구)을 걸었는지 */
    val drivingRestricted: Boolean = false,
)
