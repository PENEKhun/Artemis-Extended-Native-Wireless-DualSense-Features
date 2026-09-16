package com.limelight.dualsense

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectEightBitDoUltimate2BtTest {
    @Test fun identifiesOnlyTheSupportedUltimate2BluetoothProduct() {
        assertTrue(DirectEightBitDoUltimate2Bt.isUltimate2(0x2dc8, 0x6012))
        assertFalse(DirectEightBitDoUltimate2Bt.isUltimate2(0x2dc8, 0x6013))
        assertFalse(DirectEightBitDoUltimate2Bt.isUltimate2(0x054c, 0x0ce6))
    }

    @Test fun buildsTheSdlRumbleReportWithHighBytesOfBothMotors() {
        assertArrayEquals(byteArrayOf(0x05, 0xff.toByte(), 0x7f, 0x00, 0x00),
            DirectEightBitDoUltimate2Bt.buildRumbleReport(0xffff.toShort(), 0x7f00.toShort()))
    }

    @Test fun buildsAStopReportWhenBothMotorsAreZero() {
        assertArrayEquals(byteArrayOf(0x05, 0x00, 0x00, 0x00, 0x00),
            DirectEightBitDoUltimate2Bt.buildRumbleReport(0, 0))
    }

    @Test fun extractsTheBluetoothAddressThatIdentifiesEachController() {
        assertTrue(DirectEightBitDoUltimate2Bt.bluetoothAddressFromInputDescription(
            "Input Device 4: 8BitDo Ultimate 2 bluetoothAddress=AA:BB:CC:DD:EE:FF") ==
                "AA:BB:CC:DD:EE:FF")
    }

    @Test fun assignsFallbackAddressesByControllerNumberNotRumbleOrder() {
        val addresses = listOf("AA:AA:AA:AA:AA:01", "AA:AA:AA:AA:AA:02")
        assertTrue(DirectEightBitDoUltimate2Bt.fallbackAddressForController(0, addresses) == addresses[0])
        assertTrue(DirectEightBitDoUltimate2Bt.fallbackAddressForController(1, addresses) == addresses[1])
        assertTrue(DirectEightBitDoUltimate2Bt.fallbackAddressForController(-1, addresses) == null)
    }
}
