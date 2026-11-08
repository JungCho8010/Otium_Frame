package com.otiumframe.vehicle

import android.content.Context

/** pleos 빌드: Pleos Vehicle SDK 로 실제 차량 신호를 받는다. */
object VehicleModule {
    const val IS_MOCK = false

    /** @param watchOnly 백그라운드 서비스용: 기어·주행 제한만 받고 배터리 조회·앰비언트는 쓰지 않는다. */
    fun create(context: Context, watchOnly: Boolean = false): VehicleSignalSource =
        PleosVehicleSignalSource(context.applicationContext, watchOnly)

    val demoController: DemoVehicleController? get() = null
}
