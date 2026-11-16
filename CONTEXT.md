# OpenFit project context

## Purpose and current scope

OpenFit is an independent Android watch-companion starter (`com.openfit.app`), not affiliated with Noise/NoiseFit. It has a local-only dashboard, BLE discovery for recognizable Noise/ColorFit names, saved-watch reconnect, conditional Android system bonding after an explicit GATT authentication/encryption error, QR scanning for QR contents that contain a Bluetooth MAC address, standard Bluetooth SIG heart-rate support, and manual local sleep-duration notes. It does not yet implement the proprietary NoiseFit application-level authentication handshake. There is no account, analytics, network permission, or cloud sync.

## Implementation notes

- Native Android app is implemented in Java in `app/src/main/java/com/openfit/app/MainActivity.java`.
- Min SDK 26, compile/target SDK 35. AGP 8.6.1 and Gradle 8.9; Java source compatibility 11; CI uses JDK 17.
- Pairing QR uses JourneyApps ZXing Embedded 4.3.0. CAMERA is requested at runtime. The app explicitly registers and selects `com.journeyapps.barcodescanner.CaptureActivity`; because the library manifest also sets `screenOrientation` and `theme`, the app manifest must declare `tools:replace="android:screenOrientation,android:theme"` when overriding them. QR contents are still accepted only when they include a Bluetooth MAC address. This has not yet been verified on a physical Android device.
- Dashboard styling is intentionally dark and high contrast, with white/black surfaces, small red status accents, monospaced labels, and a live date. MainActivity now has bottom tabs for Home, Health, Devices, and Sleep; tab content is separated into panels.
- Heart-rate data is accepted only from the standard BLE Heart Rate Measurement characteristic (service 0x180D, characteristic 0x2A37). Daily min/max and minute sample counts are stored locally; they describe received notifications, not continuous monitoring.
- Manual sleep duration is stored locally and is not a device measurement or sleep-stage estimate.
- BLE screen has a selectable GATT diagnostics report listing discovered service/characteristic UUIDs and READ/WRITE/NOTIFY/INDICATE properties. It does not read/write proprietary characteristics. Previous hard-coded NoiseFit protobuf/base UUID guesses were removed; absence of SIG heart-rate support is no longer mislabeled as a protobuf-service result.
- BLE scan-start runtime exceptions now become visible status messages, and scan timeouts are canceled on stop/restart to avoid stale callbacks. QR result parsing is guarded and surfaces a retry/fallback message. These defensive changes are not confirmed fixes for the reported physical-device crash.
- If a GATT operation explicitly returns insufficient-authentication/encryption (5/15), the app starts Android's standard bond flow once, observes the bond-state broadcast, and reconnects after bonding. If access remains protected for an already-bonded device, it reports that proprietary authentication is unsupported; it does not send guessed protocol writes. This standard bonding behavior is not confirmed on a physical watch.

## NoiseFit protocol research and safety boundary

The user supplied `/home/shiv/com.noisefit_5.5.0.xapk` (NoiseFit 5.5.0, 313 MB), containing `com.noisefit.apk` (18 DEX files) plus ARM64, English, and density splits. Static inspection in this session found `libVeryFitMulti.so`, `libble-crypto.so` (JNI `BleCipher.nativeEncrypt` and related native functions), the `com/zhapp/ble` stack and GATT APIs, and NoiseFit/Ryeex DEX/protobuf metadata. This confirms proprietary BLE/encryption code exists, but does not establish the target watch's UUID-to-role mapping, authentication handshake, key derivation, command IDs, frame checksums, notifications, or transfer protocol. The available JADX MCP is not configured in the running REA server, no Java/Dex decompiler is on PATH, and bounded Python DEX string scanning timed out on this low-resource ARM64 host. Do not send guessed writes, reuse/copy proprietary binaries, or claim app-level authentication/interoperability without validated protocol evidence and authorized safe device testing.

REA 6.9.0 is installed and `rea mcp doctor` passed (255 tools). `/home/shiv/jadx-headless-mcp-0.7.1-all.jar` exists, but Java/Javac, Gradle, and JADX CLI are not available on PATH in the current shell, and no REA JADX analysis was executed in this session. No NoiseFit APK/XAPK was present in the checkout, Downloads, shared `/mnt/data`, or `/tmp` searches. `adb` is installed, but `adb devices -l` reports no attached device. The previously inspected package observations in this context were inherited, not newly verified during this session.

## Build and verification

From the repository root, with JDK 17+, Android SDK platform/build tools 35, and Gradle 8.9 available:

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

GitHub Actions (`.github/workflows/android.yml`) runs these unit tests/builds for pushes and pull requests, uploads the debug APK as an artifact, and publishes it as a release asset for `v*` tags. `HeartRateParserTest` covers 8-bit, 16-bit, and malformed measurements.

## Current work handoff

Current feature branch: `feature/ble-connection-handling`. Base changes were pushed in commit `cc4f12f`; the GitHub Actions run for that commit succeeded (run 10). Current session additionally adds conditional Android BLE bonding on explicit GATT insufficient-authentication/encryption errors and reconnects afterward, while reporting that already-bonded-but-still-protected watches need proprietary authentication. It does not implement/copy the NoiseFit protocol or send guessed bytes. The supplied XAPK is present at `/home/shiv/com.noisefit_5.5.0.xapk`; static evidence and limitations are above. Local Gradle/Java are unavailable, so use the repository GitHub Actions workflow for builds/tests. GitHub CLI is not installed, and the API tool lacked credentials to create PRs; the branch push itself triggers the push workflow. An actual authorized watch test is still required to verify pairing/authentication.
