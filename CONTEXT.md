# OpenFit project context

## Purpose and current scope

OpenFit is an independent Android watch-companion starter (`com.openfit.app`), not affiliated with Noise/NoiseFit. It has a local-only dashboard, BLE discovery for recognizable Noise/ColorFit names, saved-watch reconnect, QR scanning for QR contents that contain a Bluetooth MAC address, standard Bluetooth SIG heart-rate support, and manual local sleep-duration notes. There is no account, analytics, network permission, or cloud sync.

## Implementation notes

- Native Android app is implemented in Java in `app/src/main/java/com/openfit/app/MainActivity.java`.
- Min SDK 26, compile/target SDK 35. AGP 8.6.1 and Gradle 8.9; Java source compatibility 11; CI uses JDK 17.
- Pairing QR uses JourneyApps ZXing Embedded 4.3.0. CAMERA is requested at runtime. The app explicitly registers and selects `com.journeyapps.barcodescanner.CaptureActivity`; because the library manifest also sets `screenOrientation` and `theme`, the app manifest must declare `tools:replace="android:screenOrientation,android:theme"` when overriding them. QR contents are still accepted only when they include a Bluetooth MAC address. This has not yet been verified on a physical Android device.
- Dashboard styling is intentionally dark and high contrast, with white/black surfaces, small red status accents, monospaced labels, and a live date.
- Heart-rate data is accepted only from the standard BLE Heart Rate Measurement characteristic (service 0x180D, characteristic 0x2A37). Daily min/max and minute sample counts are stored locally; they describe received notifications, not continuous monitoring.
- Manual sleep duration is stored locally and is not a device measurement or sleep-stage estimate.
- Known NoiseFit service UUIDs are only inspected for presence. No proprietary commands or payload decoding are implemented.

## NoiseFit protocol research and safety boundary

A supplied `com.noisefit_5.5.0.xapk` was inspected offline. It contains a main NoiseFit APK and split APKs. The main manifest declares Bluetooth scan/connect and Health Connect permissions; DEX metadata includes Ryeex protocol/model classes. This does not establish the watch's pairing/authentication handshake, command IDs, protobuf envelope, checksums, notification sequence, or sleep-transfer format. Do not send guessed writes to a watch or describe the app as interoperable without validated static evidence plus safe device testing or an authorized capture.

REA is available via MCP, but its JADX bridge needs `REA_JADX_MCP_JAR` configured. Native REA providers cannot analyze APK archives. The bundled APK decompilation attempt exceeded the environment's memory limit. No Android device was attached during the QR investigation, so the crash root cause and fix remain unconfirmed on-device.

## Build and verification

From the repository root, with JDK 17+, Android SDK platform/build tools 35, and Gradle 8.9 available:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions (`.github/workflows/android.yml`) runs these unit tests/builds for pushes and pull requests, uploads the debug APK as an artifact, and publishes it as a release asset for `v*` tags. `HeartRateParserTest` covers 8-bit, 16-bit, and malformed measurements.

## Current work handoff

Current branch: `feat/nothing-dark-pairing-ui`. Latest CI identified duplicate ZXing `CaptureActivity` orientation/theme attributes; the app now declares tools:replace for its intentional portrait orientation and app theme overrides. The working tree also includes camera failure feedback, revised dark/red UI treatment and dynamic date label, and explicit reporting when BLE notification descriptor writes cannot be queued. Re-run the Android workflow after committing/pushing; test QR scanning and watch behavior on a real device before calling the QR fix verified.
