package com.otiumframe.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.EvBattery
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.vehicle.api.Gear as PleosGear

/**
 * 실제 차량(또는 Pleos Connect 에뮬레이터) 신호. Vehicle SDK 2.3.2 의 타입 있는 API 를 직접 호출한다.
 *
 * | 신호         | 출처                                                                | 에뮬레이터 Virtual Console 카드 |
 * |--------------|---------------------------------------------------------------------|---------------------------------|
 * | 주행 제한    | AAOS CarUxRestrictionsManager (권한 불필요)                         | (주행 시 자동)                  |
 * | 기어 P/R/N/D | Pleos Gear.registerGearSelection (pleos.car.permission.CAR_DRIVE)   | Motion > Gear Selection         |
 * | 충전 중      | Pleos EvBattery.getEvChargeSwitchState (화면이 보일 때만 10초 주기) | Energy > Charge Switch          |
 * | 배터리 %     | Pleos EvBattery.registerEvBatteryStateOfCharge                      | Energy > Battery SOC            |
 * | 앰비언트 색  | Pleos Light.setAmbientLightBrightnessColor (RGB, 1열 운전석 영역)   | Convenience > Ambient Color     |
 */
class PleosVehicleSignalSource(
    context: Context,
    /** true 면 백그라운드 감지용: 기어·주행 제한만 구독하고 배터리 조회·앰비언트는 쓰지 않는다. */
    private val watchOnly: Boolean = false,
) : VehicleSignalSource {
    override val displayName = "Pleos Vehicle SDK + AAOS"

    private val main = Handler(Looper.getMainLooper())
    private val vehicle = Vehicle(context)
    private val aaos = AaosDrivingSignals(context, ::publish)
    private var listener: ((VehicleState) -> Unit)? = null
    private var state = VehicleState()
    private var initialized = false
    private var gear: PleosGear? = null
    private var evBattery: EvBattery? = null
    private var light: Light? = null
    /** 차량이 앰비언트 색 제어를 지원하는 영역. 없으면 앰비언트는 쓰지 않고 테두리 색으로 대체한다. */
    private var ambientArea: SeatArea? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(value: VehicleGear?) {
            publish { it.copy(gear = gearOf(value)) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "기어 구독 실패", e)
            publish { it.copy(gear = Gear.UNKNOWN) }
        }
    }

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(percent: Int?) {
            if (percent != null) publish { it.copy(batteryPercent = percent) }
        }

        override fun onFailed(e: Exception) {
            Log.w(TAG, "배터리 구독 실패", e)
        }
    }

    private val pollCharging = object : Runnable {
        override fun run() {
            evBattery?.getEvChargeSwitchState(
                { on -> publish { it.copy(charging = on == true) } },
                { Log.w(TAG, "충전 상태 조회 실패", it) },
            )
            main.postDelayed(this, CHARGE_POLL_MS)
        }
    }

    override fun start(listener: (VehicleState) -> Unit) {
        this.listener = listener
        aaos.start()
        try {
            if (!initialized) {
                vehicle.initialize()
                initialized = true
            }
            gear = vehicle.getGear().also {
                it.registerGearSelection(gearListener)
                it.getCurrentGearSelectionState(
                    { v -> publish { s -> s.copy(gear = gearOf(v)) } },
                    { e -> Log.w(TAG, "기어 조회 실패", e) },
                )
            }
            if (!watchOnly) {
                evBattery = vehicle.getEvBattery().also { it.registerEvBatteryStateOfCharge(socListener) }
                light = vehicle.getLight().also {
                    it.checkAmbientLightBrightnessColorCapabilities(
                        { config, areas ->
                            ambientArea = areas.firstOrNull()
                            Log.i(TAG, "앰비언트 색 지원 영역: $areas ($config)")
                        },
                        { e -> Log.i(TAG, "앰비언트 색 제어 미지원", e) },
                    )
                }
                main.post(pollCharging)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vehicle SDK 초기화 실패 (Pleos 기기/에뮬레이터인지 확인)", e)
        }
        listener(state)
    }

    override fun stop() {
        listener = null
        main.removeCallbacks(pollCharging)
        runCatching { gear?.unregisterGearSelection(gearListener) }
        runCatching { evBattery?.unregisterEvBatteryStateOfCharge(socListener) }
        gear = null
        evBattery = null
        light = null
        aaos.stop()
        if (initialized) {
            runCatching { vehicle.release() }
            initialized = false
        }
    }

    override fun setAmbientColor(argb: Int, onResult: (Boolean) -> Unit) {
        val target = light ?: return onResult(false)
        val area = ambientArea ?: return onResult(false)
        target.setAmbientLightBrightnessColor(
            area,
            argb and 0xFFFFFF,
            { _, _ -> main.post { onResult(true) } },
            { e ->
                // 서드파티 앱에 조명 제어가 아직 열리지 않았을 수 있다(Playground 'Vehicle Control' SDK).
                Log.i(TAG, "앰비언트 색 적용 불가 → 테두리 색으로 대체", e)
                main.post { onResult(false) }
            },
        )
    }

    private fun publish(transform: (VehicleState) -> VehicleState) {
        main.post {
            val next = transform(state)
            if (next != state) {
                state = next
                listener?.invoke(state)
            }
        }
    }

    private fun gearOf(value: VehicleGear?): Gear = when (value) {
        VehicleGear.GEAR_PARK -> Gear.P
        VehicleGear.GEAR_REVERSE -> Gear.R
        VehicleGear.GEAR_NEUTRAL -> Gear.N
        null, VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.D // DRIVE, 1~9단은 모두 주행으로 본다
    }

    companion object {
        private const val TAG = "OtiumPleosVehicle"
        private const val CHARGE_POLL_MS = 10_000L
    }
}
