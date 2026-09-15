# Artemis Android Extended

This fork is based on [Artemis Android](https://github.com/ClassicOldSong/moonlight-android) and adds native DualSense streaming, advanced controller features, input customization, microphone forwarding, and quality-of-life improvements.

---

## 🎮 Native DualSense Streaming

Artemis Android Extended supports native end-to-end DualSense functionality through [Apollo Extended](https://github.com/Taveszfito/Apollo-Extended).

On **Android 12 and newer**, a DualSense can connect directly to the Android device over Bluetooth. No external Bluetooth adapter is required anymore.

DualSense functionality is available through both wired and wireless connections:

- Adaptive triggers
- Native HD haptics
- Conventional rumble
- Lightbar control
- Player LED control
- Multi-touch touchpad input
- Gyroscope and motion input
- Built-in controller-speaker audio
- Automatic headset-jack detection
- Stream-audio routing to a headset connected to the controller
- Automatic return to the controller speaker after unplugging the headset
- DualSense mute button support as a global client microphone mute switch
- In-stream microphone mute and unmute status feedback

The controller maintains its lighting, trigger, audio, haptic, rumble, touchpad, and motion state simultaneously while streaming.

## 🎮 8BitDo Ultimate 2 Bluetooth Rumble

Artemis Extended can forward conventional game rumble to an **8BitDo Ultimate 2 Wireless** paired directly over Bluetooth on Android 12 or newer. The first supported device is Steam-mode Bluetooth VID/PID `2DC8:6012`.

- Pair the controller normally in Android Bluetooth settings, then use it as the Artemis gamepad.
- Apollo Extended (and hosts that emit standard Moonlight rumble packets) require no controller-specific host setting.
- Artemis selects only a connected Bluetooth HID device named `8BitDo Ultimate 2`; other controllers keep the existing Android vibrator path.
- Rumble is stopped when the stream input handler closes.

This path uses Android's hidden HID-host output API, which is already used by the direct Bluetooth DualSense implementation. Android TV vendor Bluetooth stacks may still block that API; physical verification is required on the target TV and controller firmware.

For diagnostics, run:

```bash
adb logcat | grep -i 'Ultimate 2'
```

Expected entries include detection of `2DC8:6012`, each left/right rumble value, and any HID output failure. Test left, right, both, and stop feedback from a game or XInput rumble test, then verify that disconnecting the stream immediately stops vibration.

### Connection support

- **Android 12+ Bluetooth:** Connect the DualSense directly to the Android device without an external adapter.
- **Wired USB:** Connect the controller directly through USB.
- The optional USB Bluetooth HCI bridge remains part of Artemis for compatible external Bluetooth adapters and setups that require it.

### Current limitation

The DualSense built-in microphone is not currently available when the controller is connected directly through Android Bluetooth.

DualSense microphone forwarding currently requires either:

- A wired USB connection, or
- The optional USB Bluetooth HCI bridge

The DualSense mute button and in-stream microphone status feedback work in every connection mode. This limitation applies only to forwarding audio from the controller’s built-in microphone. Other native DualSense features remain available through the direct Android Bluetooth connection.

## 🎙️ Microphone Forwarding

Forward microphone audio from the Android client to the host as a Steam Streaming Microphone device.

- Choose the Android device microphone or an available DualSense microphone as the source
- Receive in-stream feedback when client microphone forwarding is enabled or disabled
- Automatically use a headset microphone when supported by the active DualSense connection

Microphone forwarding requires **Apollo Extended** on the host.

The Android device microphone can be used with a directly connected wireless DualSense. The DualSense built-in microphone requires a wired USB connection or the optional USB Bluetooth HCI bridge.

## 🎯 Gyro Aim

Use controller gyro for aiming by blending it into **right-stick input**, or convert it directly into **mouse input** in KBM Mode.

Gyro behavior, sensitivity, activation, and controls are fully customizable.

## ⌨️ Controller KBM Mode

Convert controller input directly into fully customizable **keyboard and mouse controls**, including:

- Controller buttons
- Analog sticks
- Triggers
- Paddles
- Touchpad
- Gyroscope

Custom mappings can be saved, imported, and exported as presets.

## 🕹️ Quick Menu

A fullscreen Quick Menu designed for complete controller navigation.

The menu layout and activation shortcut are customizable, and all controls can be used without touching the screen.

## 🔎 Settings Search

Quickly find and access settings without browsing through individual categories.

## 🔊 Android or Windows Volume Control

Choose whether the device volume buttons control **Android volume** or **Windows volume** while streaming.

Windows volume control requires a corresponding [PowerToys Keyboard Manager](https://learn.microsoft.com/windows/powertoys/keyboard-manager) mapping.

---

## 🖥️ Apollo Extended Host

[Apollo Extended](https://github.com/Taveszfito/Apollo-Extended) provides **Xbox 360, DualShock 4, and native DualSense host emulation** for Artemis Android Extended.

Together, Artemis Android Extended and Apollo Extended provide native end-to-end DualSense streaming while preserving:

- Adaptive triggers
- Native HD haptics
- Controller-speaker audio
- Lightbar and player LEDs
- Touchpad input
- Gyroscope and motion input
- Conventional rumble
- Controller-specific microphone controls

Download the host installer from the [Apollo Extended releases](https://github.com/Taveszfito/Apollo-Extended/releases) page.

Standard Sunshine and Apollo hosts remain compatible, but native DualSense emulation and Extended controller selection require Apollo Extended.

---

### Credits

Based on [Artemis Android](https://github.com/ClassicOldSong/moonlight-android).

Thanks to **Idkiamaguy645** and the [CloudPad-Android-Dualsense-Wireless](https://github.com/TechAntohere/CloudPad-Android-Dualsense-Wireless) project for demonstrating the Android wireless HID approach used to enable full-featured DualSense operation without an external Bluetooth adapter.

The Windows client is also available: [Moonlight Extended for Windows](https://github.com/Taveszfito/moonlight-extended).
