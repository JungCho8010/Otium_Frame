package com.otiumframe.vehicle

/**
 * 차량 신호 추상화. mock 빌드는 [MockVehicleSignalSource], pleos 빌드는 Pleos Vehicle SDK 구현을 쓴다.
 * 구현체는 리스너를 메인 스레드에서 호출해야 한다.
 */
interface VehicleSignalSource {
    val displayName: String

    fun start(listener: (VehicleState) -> Unit)

    fun stop()

    /**
     * 앰비언트 라이트 색을 맞춘다. 차량/권한 문제로 제어할 수 없으면 onResult(false) 를 호출하고,
     * 이때 화면은 테두리 색으로 대신 표현한다.
     */
    fun setAmbientColor(argb: Int, onResult: (Boolean) -> Unit)
}
