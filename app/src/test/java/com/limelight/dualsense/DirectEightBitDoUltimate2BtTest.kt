package com.limelight.dualsense

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test fun exactAddressesSurviveReversedLiveAddressOrder() {
        // Given
        val addresses = listOf("AA:AA:AA:AA:AA:01", "AA:AA:AA:AA:AA:02")

        // When
        val firstTarget = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            addresses[0], addresses.reversed(), ultimate2InputContextCount = 2)
        val secondTarget = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            addresses[1], addresses.reversed(), ultimate2InputContextCount = 2)

        // Then
        assertEquals(addresses[0], firstTarget)
        assertEquals(addresses[1], secondTarget)
    }

    @Test fun twoControllerFallbackIsRejected() {
        // Given
        val addresses = listOf("AA:AA:AA:AA:AA:01", "AA:AA:AA:AA:AA:02")

        // When
        val target = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            null, addresses, ultimate2InputContextCount = 2)

        // Then
        assertNull(target)
    }

    @Test fun disconnectedExactAddressDoesNotRouteToTheRemainingController() {
        // Given
        val disconnectedAddress = "AA:AA:AA:AA:AA:01"
        val remainingAddresses = listOf("AA:AA:AA:AA:AA:02")

        // When
        val target = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            disconnectedAddress, remainingAddresses, ultimate2InputContextCount = 2)

        // Then
        assertNull(target)
    }

    @Test fun loneInputFallsBackToLoneLiveAddress() {
        // Given
        val addresses = listOf("AA:AA:AA:AA:AA:02")

        // When
        val target = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            null, addresses, ultimate2InputContextCount = 1)

        // Then
        assertEquals(addresses.single(), target)
    }

    @Test fun loneInputDoesNotFallbackWhenTwoAddressesAreLive() {
        // Given
        val addresses = listOf("AA:AA:AA:AA:AA:01", "AA:AA:AA:AA:AA:02")

        // When
        val target = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            null, addresses, ultimate2InputContextCount = 1)

        // Then
        assertNull(target)
    }

    @Test fun fallbackIsRejectedWhenNoAddressIsLive() {
        // Given
        val addresses = emptyList<String>()

        // When
        val target = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            resolvedInputAddress = null,
            liveAddresses = addresses,
            ultimate2InputContextCount = 1)

        // Then
        assertNull(target)
    }

    @Test fun rememberedAddressRestoresTwoControllerRoutingWithoutFreshResolution() {
        // Given
        val rememberedAddress = "AA:AA:AA:AA:AA:01"
        val addresses = listOf(rememberedAddress, "AA:AA:AA:AA:AA:02")

        // When
        val selection = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            resolvedInputAddress = null,
            rememberedAddress = rememberedAddress,
            liveAddresses = addresses,
            ultimate2InputContextCount = 2)

        // Then
        assertEquals(rememberedAddress, selection?.address)
        assertEquals(BluetoothAddressSelectionSource.REMEMBERED_EXACT, selection?.source)
        assertFalse(selection?.shouldRemember == true)
    }

    @Test fun freshAddressBeatsRememberedAddress() {
        // Given
        val freshAddress = "AA:AA:AA:AA:AA:01"
        val rememberedAddress = "AA:AA:AA:AA:AA:02"
        val addresses = listOf(freshAddress, rememberedAddress)

        // When
        val selection = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            resolvedInputAddress = freshAddress,
            rememberedAddress = rememberedAddress,
            liveAddresses = addresses,
            ultimate2InputContextCount = 2)

        // Then
        assertEquals(freshAddress, selection?.address)
        assertEquals(BluetoothAddressSelectionSource.FRESH_EXACT, selection?.source)
        assertTrue(selection?.shouldRemember == true)
    }

    @Test fun staleRememberedAddressIsRejectedForTwoControllers() {
        // Given
        val addresses = listOf("AA:AA:AA:AA:AA:01", "AA:AA:AA:AA:AA:02")

        // When
        val selection = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            resolvedInputAddress = null,
            rememberedAddress = "AA:AA:AA:AA:AA:03",
            liveAddresses = addresses,
            ultimate2InputContextCount = 2)

        // Then
        assertNull(selection)
    }

    @Test fun staleRememberedAddressPermitsLoneControllerFallback() {
        // Given
        val liveAddress = "AA:AA:AA:AA:AA:01"

        // When
        val selection = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            resolvedInputAddress = null,
            rememberedAddress = "AA:AA:AA:AA:AA:02",
            liveAddresses = listOf(liveAddress),
            ultimate2InputContextCount = 1)

        // Then
        assertEquals(liveAddress, selection?.address)
        assertEquals(BluetoothAddressSelectionSource.LONE_CONTROLLER_FALLBACK, selection?.source)
    }

    @Test fun rememberedAddressLookupIsCaseInsensitive() {
        // Given
        val liveAddress = "AA:BB:CC:DD:EE:FF"

        // When
        val selection = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            resolvedInputAddress = null,
            rememberedAddress = liveAddress.lowercase(),
            liveAddresses = listOf(liveAddress, "11:22:33:44:55:66"),
            ultimate2InputContextCount = 2)

        // Then
        assertEquals(liveAddress, selection?.address)
        assertEquals(BluetoothAddressSelectionSource.REMEMBERED_EXACT, selection?.source)
    }

    @Test fun loneControllerFallbackIsNotMarkedAsFreshLearningSource() {
        // Given
        val liveAddress = "AA:AA:AA:AA:AA:01"

        // When
        val selection = DirectEightBitDoUltimate2Bt.selectTargetBluetoothAddress(
            resolvedInputAddress = null,
            rememberedAddress = null,
            liveAddresses = listOf(liveAddress),
            ultimate2InputContextCount = 1)

        // Then
        assertEquals(liveAddress, selection?.address)
        assertEquals(BluetoothAddressSelectionSource.LONE_CONTROLLER_FALLBACK, selection?.source)
        assertFalse(selection?.shouldRemember == true)
    }

    @Test fun inMemoryAddressStoreKeysControllersIndependentlyByDescriptor() {
        // Given
        val store = InMemoryUltimate2AddressStore()

        // When
        store.put("descriptor-one", "AA:AA:AA:AA:AA:01")
        store.put("descriptor-two", "AA:AA:AA:AA:AA:02")

        // Then
        assertEquals("AA:AA:AA:AA:AA:01", store.get("descriptor-one"))
        assertEquals("AA:AA:AA:AA:AA:02", store.get("descriptor-two"))
        assertNull(store.get("descriptor-three"))
    }

    @Test fun refreshesBridgeUnlessProxyAndSelectedDeviceAreReady() {
        // Given
        val selectedDeviceName = "8BitDo Ultimate 2"

        // When
        val proxyMissing = DirectEightBitDoUltimate2Bt.shouldRefreshBridge(
            proxyReady = false, connectedDeviceName = selectedDeviceName)
        val proxyAndDeviceMissing = DirectEightBitDoUltimate2Bt.shouldRefreshBridge(
            proxyReady = false, connectedDeviceName = null)
        val selectedDeviceMissing = DirectEightBitDoUltimate2Bt.shouldRefreshBridge(
            proxyReady = true, connectedDeviceName = null)
        val ready = DirectEightBitDoUltimate2Bt.shouldRefreshBridge(
            proxyReady = true, connectedDeviceName = selectedDeviceName)

        // Then
        assertTrue(proxyMissing)
        assertTrue(proxyAndDeviceMissing)
        assertTrue(selectedDeviceMissing)
        assertFalse(ready)
    }
}
