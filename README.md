# OpenFit

An independent, open-source Android starter for a simple watch companion. OpenFit is not affiliated with Noise or NoiseFit and does not contain their code, branding, assets, or services.

## Current scope

- Original on-device dashboard with a watch pairing entry point.
- Permission-aware Bluetooth LE discovery, connection, and a manually triggered reconnect to the saved watch.
- Live heart rate only when a connected device exposes the Bluetooth SIG Heart Rate Service (0x180D) and a notifiable Heart Rate Measurement characteristic (0x2A37). Notification-write outcomes are reflected in the connection state.
- Latest heart rate plus local daily min/max and minute-sample summary. This is a summary of received BLE notifications, not continuous monitoring.
- Editable manual sleep-duration entry in 15-minute increments, stored locally.
- No account, analytics, network permission, or cloud sync.

The supplied NoiseFit package declares BLE scan/connect and Health Connect write permissions, and contains Ryeex/NoiseFit heart-rate and sleep model names. Those observations do **not** reveal the proprietary watch pairing, authentication, command, or sleep-transfer protocol. OpenFit deliberately does not claim ColorFit Ultra Buzz interoperability until a user-authorized, safe protocol capture or direct hardware test validates it. Generic BLE scanning and the standard Heart Rate Service may not be enough for this watch.

## Build

Requires JDK 17+ and Android SDK platform/build tools 35, plus JDK 17 and Gradle 8.9 (AGP 8.6.1). From this directory:

    gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug

Install on a test device with:

    adb install -r app/build/outputs/apk/debug/app-debug.apk

The GitHub Actions Android workflow runs unit tests and creates this debug APK for every push/PR; version tags publish it as a GitHub Release asset. Android 12+ requests Bluetooth scan/connect permission; Android 6–11 requests location permission while scanning. Location is not collected or stored.

## Health data and limitations

The current release does not estimate, infer, or import sleep stages, and does not use Health Connect. The heart-rate history is an on-device, notification-derived daily summary and cannot promise continuous coverage. A manually logged sleep duration is a journal entry, not a medical measurement. Background synchronization, Health Connect integration, validated ColorFit Ultra Buzz pairing, and watch sleep-transfer support are future work.

## Development

- Package: com.openfit.app
- Min SDK: 26
- Unit tests cover the Bluetooth heart-rate measurement parser.
- License: MIT (see LICENSE).
