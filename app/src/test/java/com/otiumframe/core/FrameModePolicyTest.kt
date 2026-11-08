package com.otiumframe.core

import com.otiumframe.vehicle.Gear
import com.otiumframe.vehicle.VehicleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameModePolicyTest {

    private val parked = VehicleState(gear = Gear.P, parkBrakeOn = true, speedKmh = 0f)
    private val driving = VehicleState(gear = Gear.D, parkBrakeOn = false, speedKmh = 20f)

    @Test
    fun gearP_isRest() {
        assertEquals(FrameMode.REST, FrameModePolicy.modeFor(parked))
        assertEquals(FrameMode.REST, FrameModePolicy.modeFor(parked.copy(parkBrakeOn = false)))
    }

    @Test
    fun everyOtherGear_isDriving_evenWhenStopped() {
        for (gear in listOf(Gear.R, Gear.N, Gear.D, Gear.UNKNOWN)) {
            assertEquals(gear.name, FrameMode.DRIVING, FrameModePolicy.modeFor(parked.copy(gear = gear)))
        }
    }

    @Test
    fun movingIsDriving_evenInP() {
        assertEquals(FrameMode.DRIVING, FrameModePolicy.modeFor(parked.copy(speedKmh = 3f)))
    }

    @Test
    fun uxRestrictionForcesDriving_evenInP() {
        assertEquals(FrameMode.DRIVING, FrameModePolicy.modeFor(parked.copy(drivingRestricted = true)))
    }

    @Test
    fun unknownStateFailsSafeToDriving() {
        assertEquals(FrameMode.DRIVING, FrameModePolicy.modeFor(VehicleState()))
    }

    @Test
    fun videoOnlyInRest() {
        assertTrue(FrameModePolicy.isVideoAllowed(FrameMode.REST))
        assertFalse(FrameModePolicy.isVideoAllowed(FrameMode.DRIVING))
    }

    @Test
    fun autoLaunch_onlyWhenEnteringP() {
        assertTrue(FrameModePolicy.shouldAutoLaunch(driving, parked))
        assertTrue(FrameModePolicy.shouldAutoLaunch(parked.copy(gear = Gear.N), parked))
        assertFalse(FrameModePolicy.shouldAutoLaunch(parked, parked))
        assertFalse(FrameModePolicy.shouldAutoLaunch(parked, driving))
    }

    @Test
    fun noAutoLaunch_onFirstSignal() {
        assertFalse(FrameModePolicy.shouldAutoLaunch(null, parked))
    }

    @Test
    fun chargingAloneDoesNotLaunch() {
        assertFalse(FrameModePolicy.shouldAutoLaunch(parked, parked.copy(charging = true)))
    }

    @Test
    fun dismissedScreenStaysClosedUntilNextPEntry() {
        // 사용자가 더블 탭으로 닫은 뒤에도 P 상태가 이어지는 동안은 다시 열지 않는다.
        assertFalse(FrameModePolicy.shouldAutoLaunch(parked, parked.copy(batteryPercent = 70)))
    }
}
