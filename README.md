# OpenFit

An independent, open-source Android starter for a simple watch companion. OpenFit is not affiliated with Noise or NoiseFit and does not contain their code, branding, assets, or services.

## Current scope

- Dark, high-contrast dashboard with Home, Health, Devices, and Sleep tabs.
- Permission-aware Bluetooth LE discovery for recognizable Noise / ColorFit names, saved-watch reconnect, and Android system bonding before the first BLE connection (matching the supplied NoiseFit app’s observed bond-check/create-bond-before-connect flow).
- Pairing QR scanner accepts QR content containing a Bluetooth MAC address; proprietary NoiseFit QR payloads are not decoded.
- Live heart rate only when a connected device exposes the Bluetooth SIG Heart Rate Service (0x180D) and a notifiable Heart Rate Measurement characteristic (0x2A37). Notification-write outcomes are reflected in the connection state.
- Read-only GATT diagnostics list discovered service/characteristic UUIDs and properties. No proprietary command is sent.
- Latest heart rate plus local daily min/max and minute-sample summary. This is a summary of received BLE notifications, not continuous monitoring.
- Editable manual sleep-duration entry in 15-minute increments, stored locally.
- No account, analytics, network permission, or cloud sync.

Static inspection of NoiseFit 5.5.0's supplied XAPK found a main APK and ARM64 split, the `libVeryFitMulti.so` and `libble-crypto.so` libraries, plus `com.zhapp.ble.BerryBluetoothService` methods/logs showing bond-state checks and Android `createBond()` before connecting. OpenFit now follows that observed system-bond-first flow and reconnects after Android confirms bonding. The APK also contains a JNI BLE cipher, but static evidence available here does not establish the proprietary encrypted application handshake or its keys. OpenFit does not send guessed auth/protocol bytes or copy NoiseFit code. The QR flow accepts a Bluetooth MAC address only. Device-name filtering is a convenience, not proof of identity. Successful ColorFit Ultra Buzz application-level authentication remains unverified and requires a user-authorized device test.

## Build

Requires JDK 17+ and Android SDK platform/build tools 35, plus JDK 17 and Gradle 8.9 (AGP 8.6.1). From this directory:

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

Install on a test device with:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

The GitHub Actions Android workflow runs unit tests and creates this debug APK for every push/PR; version tags publish it as a GitHub Release asset. Android 12+ requests Bluetooth scan/connect permission; Android 6–11 requests location permission while scanning. Location is not collected or stored.

## Health data and limitations

The current release does not estimate, infer, or import sleep stages, and does not use Health Connect. The heart-rate history is an on-device, notification-derived daily summary and cannot promise continuous coverage. A manually logged sleep duration is a journal entry, not a medical measurement. Background synchronization, Health Connect integration, the proprietary NoiseFit application-level authentication handshake, validated ColorFit Ultra Buzz pairing, and watch sleep-transfer support are future work.

## Development

- Package: com.openfit.app
- Min SDK: 26
- Unit tests cover the Bluetooth heart-rate measurement parser.
- License: MIT (see LICENSE).
