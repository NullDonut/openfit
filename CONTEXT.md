# OpenFit project context

## Purpose and current scope

OpenFit is an independent Android watch-companion starter (`com.openfit.app`), not affiliated with Noise/NoiseFit. It has a local-only dashboard, BLE discovery for recognizable Noise/ColorFit names, saved-watch reconnect, QR scanning for QR contents that contain a Bluetooth MAC address, standard Bluetooth SIG heart-rate support, and manual local sleep-duration notes. There is no account, analytics, network permission, or cloud sync.

## Implementation notes

- Native Android app is implemented in Java in `app/src/main/java/com/openfit/app/MainActivity.java`.
- Min SDK 26, compile/target SDK 35. AGP 8.6.1 and Gradle 8.9; Java source compatibility 11; CI uses JDK 17.
- Pairing QR uses JourneyApps ZXing Embedded 4.3.0. CAMERA is requested at runtime. The app explicitly registers and selects `com.journeyapps.barcodescanner.CaptureActivity`; because the library manifest also sets `screenOrientation` and `theme`, the app manifest must declare `tools:replace="android:screenOrientation,android:theme"` when overriding them. QR contents are still accepted only when they include a Bluetooth MAC address. This has not yet been verified on a physical Android device.
- Dashboard styling is intentionally dark and high contrast, with white/black surfaces, small red status accents, monospaced labels, and a live date. MainActivity now has bottom tabs for Home, Health, Devices, and Sleep; tab content is separated into panels.
- Heart-rate data is accepted only from the standard BLE Heart Rate Measurement characteristic (service 0x180D, characteristic 0x2A37). Daily min/max and minute sample counts are stored locally; they describe received notifications, not continuous monitoring.
- Manual sleep duration is stored locally and is not a device measurement or sleep-stage estimate.
- BLE screen has a selectable GATT diagnostics report listing discovered service/characteristic UUIDs and READ/WRITE/NOTIFY/INDICATE properties. It does not read/write proprietary characteristics. Previous hard-coded NoiseFit protobuf/base UUID guesses were removed; absence of SIG heart-rate support is no longer mislabeled as a protobuf-service result.
- BLE scan-start runtime exceptions now become visible status messages, and scan timeouts are canceled on stop/restart to avoid stale callbacks. QR result parsing is guarded and surfaces a retry/fallback message. These defensive changes are not confirmed fixes for the reported physical-device crash.

## NoiseFit protocol research and safety boundary

A supplied `com.noisefit_5.5.0.xapk` was inspected offline. It contains a main NoiseFit APK and split APKs. The main manifest declares Bluetooth scan/connect and Health Connect permissions; DEX metadata includes Ryeex protocol/model classes. This does not establish the watch's pairing/authentication handshake, command IDs, protobuf envelope, checksums, notification sequence, or sleep-transfer format. Do not send guessed writes to a watch or describe the app as interoperable without validated static evidence plus safe device testing or an authorized capture.

REA 6.9.0 is installed and `rea mcp doctor` passed (255 tools). `/home/shiv/jadx-headless-mcp-0.7.1-all.jar` exists, but Java/Javac, Gradle, and JADX CLI are not available on PATH in the current shell, and no REA JADX analysis was executed in this session. No NoiseFit APK/XAPK was present in the checkout, Downloads, shared `/mnt/data`, or `/tmp` searches. `adb` is installed, but `adb devices -l` reports no attached device. The previously inspected package observations in this context were inherited, not newly verified during this session.

## Build and verification

From the repository root, with JDK 17+, Android SDK platform/build tools 35, and Gradle 8.9 available:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions (`.github/workflows/android.yml`) runs these unit tests/builds for pushes and pull requests, uploads the debug APK as an artifact, and publishes it as a release asset for `v*` tags. `HeartRateParserTest` covers 8-bit, 16-bit, and malformed measurements.

## Current work handoff

Current branch: `feat/openfit-android-starter`. Working tree changes in this session: MainActivity multi-tab UI, GATT diagnostics, removal of guessed proprietary service UUID detection, safe scan startup/timeout handling, and guarded QR result parsing. QR still accepts only a QR string containing a Bluetooth MAC address; proprietary NoiseFit QR provisioning is not implemented. REA/JADX APK reverse engineering and physical-device verification are blocked until the APK/XAPK and a configured Java/JADX runtime or attached device are available. No proprietary protocol or direct watch communication is claimed. Run `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug` in an Android/JDK environment; it could not run in this shell because Java and Gradle are absent.
