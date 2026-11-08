package com.otiumframe.vehicle

import android.content.Context

/** mock 빌드: 차량 신호를 DEMO 패널과 adb 브로드캐스트로 흉내 낸다. */
object VehicleModule {
    const val IS_MOCK = true

    /** @param watchOnly 백그라운드 서비스용: 기어 감지에 필요한 신호만 받는다(mock 은 구분 없음). */
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, watchOnly: Boolean = false): VehicleSignalSource =
        MockVehicleSignalSource(context.applicationContext)

    val demoController: DemoVehicleController? get() = MockVehicle
}
