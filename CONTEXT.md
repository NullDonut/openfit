# OpenFit project context

## Purpose and current scope

OpenFit is an independent Android watch-companion starter (`com.openfit.app`), not affiliated with Noise/NoiseFit. It has a local-only dashboard, BLE discovery for recognizable Noise/ColorFit names, saved-watch reconnect, Android system bonding before BLE connect (following the supplied NoiseFit APK's observed bond-check/createBond-before-connect behavior), QR scanning for QR contents that contain a Bluetooth MAC address, standard Bluetooth SIG heart-rate support, and manual local sleep-duration notes. It does not yet talk to the watch's vendor GATT profile; the recovered Berry profile (`PROTOCOL.md`) needs no application-level authentication, so the earlier "proprietary handshake blocks pairing" assumption no longer applies to that stack. There is no account, analytics, network permission, or cloud sync.

## Implementation notes

- Native Android app is implemented in Java in `app/src/main/java/com/openfit/app/MainActivity.java`.
- Min SDK 26, compile/target SDK 35. AGP 8.6.1 and Gradle 8.9; Java source compatibility 11; CI uses JDK 17.
- Pairing QR uses JourneyApps ZXing Embedded 4.3.0. CAMERA is requested at runtime. The app explicitly registers and selects `com.journeyapps.barcodescanner.CaptureActivity`; because the library manifest also sets `screenOrientation` and `theme`, the app manifest must declare `tools:replace="android:screenOrientation,android:theme"` when overriding them. QR contents are still accepted only when they include a Bluetooth MAC address. This has not yet been verified on a physical Android device.
- Dashboard styling is intentionally dark and high contrast, with white/black surfaces, small red status accents, monospaced labels, and a live date. MainActivity now has bottom tabs for Home, Health, Devices, and Sleep; tab content is separated into panels.
- Heart-rate data is accepted only from the standard BLE Heart Rate Measurement characteristic (service 0x180D, characteristic 0x2A37). Daily min/max and minute sample counts are stored locally; they describe received notifications, not continuous monitoring.
- Manual sleep duration is stored locally and is not a device measurement or sleep-stage estimate.
- BLE screen has a selectable GATT diagnostics report listing discovered service/characteristic UUIDs and READ/WRITE/NOTIFY/INDICATE properties. It does not read/write proprietary characteristics. Previous hard-coded NoiseFit protobuf/base UUID guesses were removed; absence of SIG heart-rate support is no longer mislabeled as a protobuf-service result.
- BLE scan-start runtime exceptions now become visible status messages, and scan timeouts are canceled on stop/restart to avoid stale callbacks. QR result parsing is guarded and surfaces a retry/fallback message. These defensive changes are not confirmed fixes for the reported physical-device crash.
- Initial connect first checks `BluetoothDevice.getBondState()`; unbonded devices enter Android `createBond()` and the app waits for `ACTION_BOND_STATE_CHANGED` before connecting. Existing bonding is awaited and resumes through the same receiver. On explicit GATT insufficient-authentication/encryption (5/15), it can trigger the same standard bond flow. This follows the supplied NoiseFit APK's `BerryBluetoothService` bond-check, `createBond`, and bond-state receiver evidence; actual device behavior is not confirmed.

## NoiseFit protocol research and safety boundary

The user supplied `/home/shiv/com.noisefit_5.5.0.xapk` (NoiseFit 5.5.0, 313 MB), containing `com.noisefit.apk` (18 DEX files) plus ARM64, English, and density splits. A 2026-11-17 pass with a rootless JADX CLI install recovered the Berry profile end to end: GATT service `16186f00-0000-1000-8000-00807f9b34fb` (chars `…6f01`–`…6f05`, writes on `…6f02`), the 9-byte frame header, the `SEWear` protobuf envelope, and the bind commands. That stack has no encryption or challenge/response. `libble-crypto.so`'s RC4-variant cipher and its `mixA/mixB` permutations are decoded for the alternate Ryeex stack, whose opcodes and handshake remain unknown. Full tables, provenance and open questions are in `PROTOCOL.md`; independent implementations of the frame/protobuf layer live in `app/src/main/java/com/openfit/app/protocol/` with unit tests. Do not send unvalidated writes to a watch, do not copy proprietary binaries, and do not claim application-level interoperability without a device test.

REA 6.9.0 is installed and `rea mcp doctor` passed (255 tools). `/home/shiv/jadx-headless-mcp-0.7.1-all.jar` exists, but Java/Javac, Gradle, and JADX CLI are not available on PATH in the current shell, and no REA JADX analysis was executed in this session. No NoiseFit APK/XAPK was present in the checkout, Downloads, shared `/mnt/data`, or `/tmp` searches. `adb` is installed, but `adb devices -l` reports no attached device. The previously inspected package observations in this context were inherited, not newly verified during this session.

## Build and verification

From the repository root, with JDK 17+, Android SDK platform/build tools 35, and Gradle 8.9 available:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions (`.github/workflows/android.yml`) runs these unit tests/builds for pushes and pull requests, uploads the debug APK as an artifact, and publishes it as a release asset for `v*` tags. `HeartRateParserTest` covers 8-bit, 16-bit, and malformed measurements.

## Current work handoff

Current feature branch: `feature/ble-connection-handling`. Base changes were pushed in commit `cc4f12f`; the GitHub Actions run for that commit succeeded (run 10). The branch adds conditional Android BLE bonding on explicit GATT insufficient-authentication/encryption errors and reconnects afterward. On 2026-11-17 the NoiseFit package was decompiled properly (rootless JDK 21 + JADX CLI in `/tmp`, one DEX at a time) and the Berry vendor profile was recovered and implemented as an Android-free protocol layer with unit tests; it is **not yet wired into `MainActivity`**. `PROTOCOL.md` is the authoritative wire reference. Local Gradle/Android SDK remain unavailable, so builds/tests go through the GitHub Actions workflow; the new protocol layer was additionally compiled and tested locally with `javac` + JUnit 4.13.2. An authorized watch test (and a GATT dump to identify which stack the watch exposes) is still required before claiming a working connection.
