package com.otiumframe.vehicle

/** mock 빌드에서만 제공되는 데모용 조작 핸들. 실제 차량 빌드에서는 null 이다. */
interface DemoVehicleController {
    val state: VehicleState
    var ambientSupported: Boolean
    val lastAmbientColor: Int?

    fun update(transform: (VehicleState) -> VehicleState)

    fun addObserver(observer: () -> Unit)

    fun removeObserver(observer: () -> Unit)
}
