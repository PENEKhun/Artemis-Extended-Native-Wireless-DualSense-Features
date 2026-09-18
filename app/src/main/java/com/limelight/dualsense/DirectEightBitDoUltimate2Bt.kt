// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.limelight.dualsense

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.view.InputDevice
import com.limelight.LimeLog
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.ConcurrentHashMap

internal interface Ultimate2AddressStore {
    fun get(descriptor: String): String?
    fun put(descriptor: String, address: String)
}

internal class InMemoryUltimate2AddressStore : Ultimate2AddressStore {
    private val addresses = ConcurrentHashMap<String, String>()

    override fun get(descriptor: String): String? = addresses[descriptor]

    override fun put(descriptor: String, address: String) {
        addresses[descriptor] = address
    }
}

internal class SharedPreferencesUltimate2AddressStore(
    private val preferences: SharedPreferences,
) : Ultimate2AddressStore {
    override fun get(descriptor: String): String? = preferences.getString(descriptor, null)

    @Synchronized override fun put(descriptor: String, address: String) {
        if (preferences.getString(descriptor, null)?.equals(address, ignoreCase = true) == true) return
        preferences.edit().putString(descriptor, address).apply()
    }
}

internal enum class BluetoothAddressSelectionSource {
    FRESH_EXACT,
    REMEMBERED_EXACT,
    LONE_CONTROLLER_FALLBACK,
}

internal data class BluetoothAddressSelection(
    val address: String,
    val source: BluetoothAddressSelectionSource,
) {
    val shouldRemember: Boolean
        get() = source == BluetoothAddressSelectionSource.FRESH_EXACT
}

/** Sends rumble reports to an Ultimate 2 paired through Android Bluetooth HID. */
object DirectEightBitDoUltimate2Bt {
    const val VENDOR_ID = 0x2dc8
    const val PRODUCT_ID = 0x6012
    private const val ADDRESS_PREFERENCES_NAME = "eight_bit_do_ultimate_2_addresses"
    private val inputDeviceBluetoothAddressRegex = Regex("bluetoothAddress=([0-9A-Fa-f:]{17})")

    private val lock = Any()
    private var bridge: DirectDualSenseBtHidBridge? = null
    @Volatile private var addressStore: Ultimate2AddressStore = InMemoryUltimate2AddressStore()
    @Volatile private var initialized = false

    @JvmStatic fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || initialized) return
        synchronized(lock) {
            if (initialized) return
            HiddenApiBypass.addHiddenApiExemptions("Landroid/view/InputDevice;")
            val applicationContext = context.applicationContext
            addressStore = SharedPreferencesUltimate2AddressStore(applicationContext.getSharedPreferences(
                ADDRESS_PREFERENCES_NAME, Context.MODE_PRIVATE))
            bridge = DirectDualSenseBtHidBridge(applicationContext,
                ::isUltimate2BluetoothDevice, "8BitDo Ultimate 2")
            initialized = true
        }
    }

    @JvmStatic fun isUltimate2(vendorId: Int, productId: Int): Boolean =
        vendorId == VENDOR_ID && productId == PRODUCT_ID

    @JvmStatic fun isBluetoothUltimate2Input(device: InputDevice?): Boolean =
        device != null && isUltimate2(device.vendorId, device.productId)

    @JvmStatic fun warmUp(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && permissionGranted(context)) {
            bridge?.start()
        }
    }

    @JvmStatic fun isConnected(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !permissionGranted(context)) return false
        val active = bridge ?: return false
        val status = active.getStatus(permissionGranted = true)
        if (shouldRefreshBridge(status.proxyReady, status.connectedDeviceName)) active.start()
        val refreshed = active.getStatus(permissionGranted = true)
        return refreshed.proxyReady && refreshed.connectedDeviceName != null &&
            (refreshed.sendDataReady || refreshed.setReportReady)
    }

    @JvmStatic fun sendRumble(
        context: Context,
        inputDevice: InputDevice?,
        ultimate2InputContextCount: Int,
        lowFrequency: Short,
        highFrequency: Short,
    ): Boolean {
        if (!isConnected(context)) return false
        val addresses = bridge?.getMatchingDeviceAddresses().orEmpty().map(String::uppercase)
        val descriptor = inputDevice?.descriptor
        val store = addressStore
        val selection = selectTargetBluetoothAddress(
            resolvedInputAddress = bluetoothAddress(inputDevice),
            rememberedAddress = descriptor?.let(store::get),
            liveAddresses = addresses,
            ultimate2InputContextCount = ultimate2InputContextCount) ?: run {
            LimeLog.warning("Ultimate 2 Bluetooth rumble skipped: controller Bluetooth address unavailable")
            return false
        }
        if (descriptor != null && selection.shouldRemember) {
            store.put(descriptor, selection.address)
        }
        return sendRumbleToAddress(context, selection.address, lowFrequency, highFrequency)
    }

    @JvmStatic fun stopRumble(context: Context): Boolean {
        val addresses = bridge?.getMatchingDeviceAddresses().orEmpty()
        val stopped = addresses.isNotEmpty() && addresses.all {
            sendRumbleToAddress(context, it, 0, 0)
        }
        return stopped
    }

    private fun sendRumbleToAddress(
        context: Context,
        address: String,
        lowFrequency: Short,
        highFrequency: Short,
    ): Boolean {
        if (!isConnected(context)) return false
        LimeLog.info("Ultimate 2 Bluetooth rumble: left=${(lowFrequency.toInt() ushr 8) and 0xff} " +
            "right=${(highFrequency.toInt() ushr 8) and 0xff} device=$address")
        val result = bridge?.sendOutputReport(buildRumbleReport(lowFrequency, highFrequency),
            streaming = true, preferInterrupt = true, targetDeviceAddress = address)
        if (result?.success != true) {
            LimeLog.warning("Ultimate 2 Bluetooth rumble failed: ${result?.message ?: "HID bridge unavailable"}")
            return false
        }
        return true
    }

    @JvmStatic fun buildRumbleReport(lowFrequency: Short, highFrequency: Short): ByteArray = byteArrayOf(
        0x05,
        (lowFrequency.toInt() ushr 8).toByte(),
        (highFrequency.toInt() ushr 8).toByte(),
        0x00,
        0x00,
    )

    private fun permissionGranted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun isUltimate2BluetoothDevice(device: android.bluetooth.BluetoothDevice): Boolean =
        runCatching { device.name?.contains("8BitDo Ultimate 2", ignoreCase = true) == true }
            .getOrDefault(false)

    @JvmStatic fun bluetoothAddressFromInputDescription(description: String): String? =
        inputDeviceBluetoothAddressRegex.find(description)?.groupValues?.get(1)?.uppercase()

    private fun bluetoothAddress(inputDevice: InputDevice?): String? {
        if (inputDevice == null) return null

        return runCatching {
            HiddenApiBypass.invoke(InputDevice::class.java, inputDevice, "getBluetoothAddress") as? String
        }.getOrNull()?.uppercase() ?: bluetoothAddressFromInputDescription(inputDevice.toString())
    }

    internal fun selectTargetBluetoothAddress(
        resolvedInputAddress: String?,
        liveAddresses: List<String>,
        ultimate2InputContextCount: Int,
    ): String? = selectTargetBluetoothAddress(
        resolvedInputAddress = resolvedInputAddress,
        rememberedAddress = null,
        liveAddresses = liveAddresses,
        ultimate2InputContextCount = ultimate2InputContextCount,
    )?.address

    internal fun selectTargetBluetoothAddress(
        resolvedInputAddress: String?,
        rememberedAddress: String?,
        liveAddresses: List<String>,
        ultimate2InputContextCount: Int,
    ): BluetoothAddressSelection? {
        val exactAddress = resolvedInputAddress?.let { resolved ->
            liveAddresses.firstOrNull { it.equals(resolved, ignoreCase = true) }
        }
        if (exactAddress != null) {
            return BluetoothAddressSelection(exactAddress, BluetoothAddressSelectionSource.FRESH_EXACT)
        }

        val rememberedExactAddress = rememberedAddress?.let { remembered ->
            liveAddresses.firstOrNull { it.equals(remembered, ignoreCase = true) }
        }
        if (rememberedExactAddress != null) {
            return BluetoothAddressSelection(
                rememberedExactAddress, BluetoothAddressSelectionSource.REMEMBERED_EXACT)
        }

        return liveAddresses.singleOrNull()?.takeIf { ultimate2InputContextCount == 1 }?.let {
            BluetoothAddressSelection(it, BluetoothAddressSelectionSource.LONE_CONTROLLER_FALLBACK)
        }
    }

    internal fun shouldRefreshBridge(proxyReady: Boolean, connectedDeviceName: String?): Boolean =
        !proxyReady || connectedDeviceName == null
}
