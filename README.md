# OpenFit

An independent, open-source Android starter for a simple watch companion. OpenFit is not affiliated with Noise or NoiseFit and does not contain their code, branding, assets, or services.

## Current scope

- Dark, high-contrast dashboard with Home, Health, Devices, and Sleep tabs.
- Permission-aware Bluetooth LE discovery for recognizable Noise / ColorFit names, saved-watch reconnect, and a connect-first GATT flow: the app now connects directly, retries transient failures (for example GATT 133), and starts Android system bonding only when the watch reports that the link needs authentication or encryption.
- Pairing QR scanner accepts QR content containing a Bluetooth MAC address; proprietary NoiseFit QR payloads are not decoded.
- Live heart rate only when a connected device exposes the Bluetooth SIG Heart Rate Service (0x180D) and a notifiable Heart Rate Measurement characteristic (0x2A37). Notification-write outcomes are reflected in the connection state.
- Read-only GATT diagnostics list discovered service/characteristic UUIDs and properties, plus an on-device connection log and a pairing log (both with a Copy button) so a failed physical test can be reported without adb.
- On a Noise watch exposing `16186f00`, the app negotiates MTU and runs the vendor account bind over the SE ("Apricot") chunked-protobuf transport (binding status → bind check → bind result, or token verification when already bound), with announce/chunk framing and replies reassembled. This write path is implemented but not yet confirmed on a physical watch.
- Latest heart rate plus local daily min/max and minute-sample summary. This is a summary of received BLE notifications, not continuous monitoring.
- Editable manual sleep-duration entry in 15-minute increments, stored locally.
- On-device crash capture: the last uncaught exception (including a QR scanner failure) is shown in the diagnostics report, so bugs can be reported without adb.
- No account, analytics, network permission, or cloud sync.

Static inspection of NoiseFit 5.5.0's supplied XAPK found a main APK and ARM64 split, the `libVeryFitMulti.so` and `libble-crypto.so` libraries, plus `com.zhapp.ble.BerryBluetoothService` methods/logs showing bond-state checks and Android `createBond()` before connecting. OpenFit keeps that bonding path as a fallback instead of a precondition, so a watch that accepts a plain BLE connection is no longer blocked by a failed or hanging system pairing prompt. A later decompilation pass recovered the watch's Berry GATT profile, SPP frame layout, ack frame, protobuf envelope and bind commands (see `PROTOCOL.md`). On 2026-11-17 a physical ColorFit Ultra Buzz on Android 16 was confirmed to expose the `16186f00` service, connect, and accept notification setup for all five characteristics. A write attempt showed that the service answers the Berry frame with the SE "busy" control frame `00 00 01 02 00 00`, so a further pass recovered the SE ("Apricot") chunked-protobuf transport and its `SEFunctionId` account bind. The on-device build confirmed the transport (ready/received control frames at MTU 247) and exposed that the vendor ships two different `SEWear` schemas; the bind now uses the BLE one. Independent implementations of both layers live in `app/src/main/java/com/openfit/app/protocol/` with 60 unit tests. On the next device run the watch answered the first SE command, but with `SEWear{id=0, error_code=NOT_SUPPORT}` for the id-0 MTU config, not with a bind reply; the greeting is now dropped and the bind probe is sent first. Vendor-level pairing is still pending a device test. The APK also contains a JNI BLE cipher (RC4 variant, decoded) for a different watch family whose opcodes remain unknown. OpenFit does not copy NoiseFit code. The QR flow accepts a Bluetooth MAC address only. Device-name filtering is a convenience, not proof of identity.

## Build

Requires JDK 17+ and Android SDK platform/build tools 35, plus JDK 17 and Gradle 8.9 (AGP 8.6.1). From this directory:

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

Install on a test device with:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

The GitHub Actions Android workflow runs unit tests and creates this debug APK for every push/PR; version tags publish it as a GitHub Release asset. Android 12+ requests Bluetooth scan/connect permission; Android 6–11 requests location permission while scanning. Location is not collected or stored.

## Health data and limitations

The current release does not estimate, infer, or import sleep stages, and does not use Health Connect. The heart-rate history is an on-device, notification-derived daily summary and cannot promise continuous coverage. A manually logged sleep duration is a journal entry, not a medical measurement. Background synchronization, Health Connect integration, Noise heart-rate/settings commands, and watch sleep-transfer support are future work. BLE connection, `16186f00` profile detection, and notification setup are validated on hardware (the watch connected while Android reported it as not bonded). The SE account bind now runs on connect, but whether this watch accepts the OpenFit id-17/id-18 exchange is unconfirmed; if it stays silent, the next transport is the vendor's classic bond + SPP. QR scanning has a dependency fix that now builds, with the physical retest still pending. A failed QR launch is recorded locally and appears in the diagnostics report.

## Development

- Package: com.openfit.app
- Min SDK: 26
- Unit tests cover the Bluetooth heart-rate measurement parser plus the Berry SPP layer and the SE transport/bind layer (framing, transfer reassembly, protobuf encoders in `app/src/main/java/com/openfit/app/protocol/`).
- `PROTOCOL.md` documents the recovered watch protocol and its provenance.
- License: MIT (see LICENSE).
