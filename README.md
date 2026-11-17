# OpenFit

An independent, open-source Android starter for a simple watch companion. OpenFit is not affiliated with Noise or NoiseFit and does not contain their code, branding, assets, or services.

## Current scope

- Dark, high-contrast dashboard with Home, Health, Devices, and Sleep tabs.
- Permission-aware Bluetooth LE discovery for recognizable Noise / ColorFit names, saved-watch reconnect, and a connect-first GATT flow: the app now connects directly, retries transient failures (for example GATT 133), and starts Android system bonding only when the watch reports that the link needs authentication or encryption.
- Pairing QR scanner accepts QR content containing a Bluetooth MAC address; proprietary NoiseFit QR payloads are not decoded.
- Live heart rate only when a connected device exposes the Bluetooth SIG Heart Rate Service (0x180D) and a notifiable Heart Rate Measurement characteristic (0x2A37). Notification-write outcomes are reflected in the connection state.
- Read-only GATT diagnostics list discovered service/characteristic UUIDs and properties, plus an on-device connection log (with a Copy button) so a failed physical test can be reported without adb. No proprietary command is sent.
- Latest heart rate plus local daily min/max and minute-sample summary. This is a summary of received BLE notifications, not continuous monitoring.
- Editable manual sleep-duration entry in 15-minute increments, stored locally.
- On-device crash capture: the last uncaught exception (including a QR scanner failure) is shown in the diagnostics report, so bugs can be reported without adb.
- No account, analytics, network permission, or cloud sync.

Static inspection of NoiseFit 5.5.0's supplied XAPK found a main APK and ARM64 split, the `libVeryFitMulti.so` and `libble-crypto.so` libraries, plus `com.zhapp.ble.BerryBluetoothService` methods/logs showing bond-state checks and Android `createBond()` before connecting. OpenFit keeps that bonding path as a fallback instead of a precondition, so a watch that accepts a plain BLE connection is no longer blocked by a failed or hanging system pairing prompt. A later decompilation pass recovered the watch's Berry GATT profile, frame layout, protobuf envelope and bind commands (see `PROTOCOL.md`); that profile uses no encryption or challenge-response, and an independent implementation of its frame/protobuf layer now lives in `app/src/main/java/com/openfit/app/protocol/` with unit tests. On 2026-11-17 a physical ColorFit Ultra Buzz on Android 16 was confirmed to expose the Berry service, connect, and accept notification setup for all five Berry characteristics. The frame/protobuf layer is not yet used to send commands, so vendor-level binding is still pending. The APK also contains a JNI BLE cipher (RC4 variant, decoded) for a different watch family whose opcodes remain unknown. OpenFit does not copy NoiseFit code or send unvalidated writes. The QR flow accepts a Bluetooth MAC address only. Device-name filtering is a convenience, not proof of identity.

## Build

Requires JDK 17+ and Android SDK platform/build tools 35, plus JDK 17 and Gradle 8.9 (AGP 8.6.1). From this directory:

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

Install on a test device with:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

The GitHub Actions Android workflow runs unit tests and creates this debug APK for every push/PR; version tags publish it as a GitHub Release asset. Android 12+ requests Bluetooth scan/connect permission; Android 6–11 requests location permission while scanning. Location is not collected or stored.

## Health data and limitations

The current release does not estimate, infer, or import sleep stages, and does not use Health Connect. The heart-rate history is an on-device, notification-derived daily summary and cannot promise continuous coverage. A manually logged sleep duration is a journal entry, not a medical measurement. Background synchronization, Health Connect integration, the Berry vendor bind/heart-rate commands, and watch sleep-transfer support are future work. BLE connection and Berry profile detection are validated on hardware (the watch connected while Android reported it as not bonded); QR scanning has a dependency fix that now builds, with the physical retest still pending. A failed QR launch is recorded locally and appears in the diagnostics report.

## Development

- Package: com.openfit.app
- Min SDK: 26
- Unit tests cover the Bluetooth heart-rate measurement parser and the Berry protocol frame/protobuf layer (`app/src/main/java/com/openfit/app/protocol/`).
- `PROTOCOL.md` documents the recovered watch protocol and its provenance.
- License: MIT (see LICENSE).
