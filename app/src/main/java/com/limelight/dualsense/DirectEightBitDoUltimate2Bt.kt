// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.limelight.dualsense

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.InputDevice
import com.limelight.LimeLog
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Sends rumble reports to an Ultimate 2 paired through Android Bluetooth HID. */
object DirectEightBitDoUltimate2Bt {
    const val VENDOR_ID = 0x2dc8
    const val PRODUCT_ID = 0x6012
    private val inputDeviceBluetoothAddressRegex = Regex("bluetoothAddress=([0-9A-Fa-f:]{17})")

    private val lock = Any()
    private var bridge: DirectDualSenseBtHidBridge? = null
    private val fallbackAddressesByControllerNumber = HashMap<Short, String>()
    @Volatile private var initialized = false

    @JvmStatic fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || initialized) return
        synchronized(lock) {
            if (initialized) return
            HiddenApiBypass.addHiddenApiExemptions("Landroid/view/InputDevice;")
            bridge = DirectDualSenseBtHidBridge(context.applicationContext,
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
        if (!status.proxyReady) active.start()
        val refreshed = active.getStatus(permissionGranted = true)
        return refreshed.proxyReady && refreshed.connectedDeviceName != null &&
            (refreshed.sendDataReady || refreshed.setReportReady)
    }

    @JvmStatic fun sendRumble(
        context: Context,
        controllerNumber: Short,
        inputDevice: InputDevice?,
        lowFrequency: Short,
        highFrequency: Short,
    ): Boolean {
        if (!isConnected(context)) return false
        val addresses = bridge?.getMatchingDeviceAddresses().orEmpty().map(String::uppercase)
        val address = bluetoothAddress(inputDevice)?.takeIf { it in addresses }
            ?: fallbackBluetoothAddress(controllerNumber, addresses) ?: run {
            LimeLog.warning("Ultimate 2 Bluetooth rumble skipped: controller Bluetooth address unavailable")
            return false
        }
        return sendRumbleToAddress(context, address, lowFrequency, highFrequency)
    }

    @JvmStatic fun stopRumble(context: Context): Boolean {
        val addresses = bridge?.getMatchingDeviceAddresses().orEmpty()
        val stopped = addresses.isNotEmpty() && addresses.all {
            sendRumbleToAddress(context, it, 0, 0)
        }
        synchronized(lock) {
            fallbackAddressesByControllerNumber.clear()
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

    private fun fallbackBluetoothAddress(controllerNumber: Short, addresses: List<String>): String? {
        synchronized(lock) {
            fallbackAddressesByControllerNumber.entries.removeAll { it.value !in addresses }
            val address = fallbackAddressesByControllerNumber[controllerNumber]
                ?: fallbackAddressForController(controllerNumber, addresses)?.also {
                    fallbackAddressesByControllerNumber[controllerNumber] = it
                    LimeLog.warning("Ultimate 2 Bluetooth rumble using fallback device mapping: " +
                        "controller=$controllerNumber device=$it")
                }
            return address
        }
    }

    @JvmStatic internal fun fallbackAddressForController(
        controllerNumber: Short,
        addresses: List<String>,
    ): String? = addresses.getOrNull(controllerNumber.toInt())
}
