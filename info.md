# OpenFit / NoiseFit BLE Research Notes

Last updated: 2026-11-16

## User request and current status

The user authorized adding NoiseFit-related implementations and asked that findings be saved in this file. The current goal is to make the watch connection functional without inventing an undocumented authentication exchange or copying proprietary NoiseFit implementation code.

## Repository and branch

- Repository: `NullDonut/openfit` (remote `git@github.com:NullDonut/openfit.git`).
- Feature branch: `feature/ble-connection-handling`.
- Latest pushed commit: `4c3381e` — `Start NoiseFit watches with Android bonding`.
- Existing PR: [#2, Improve BLE watch connection handling](https://github.com/NullDonut/openfit/pull/2), targeting `feat/openfit-android-starter`.
- GitHub Actions for the latest PR head passed: 0 failing, 0 pending, 1 passed (release job is skipped/neutral).
- Local worktree was clean after the commit. Local Gradle/Java/device tests were not run: `java`, `gradle`, and a configured Android SDK are unavailable; no physical watch is attached.

## Supplied NoiseFit package

- XAPK: `/home/shiv/com.noisefit_5.5.0.xapk`, 312,617,971 bytes (~313 MB), package `com.noisefit`, version `5.5.0`, version code `896`, min SDK 26, target SDK 36.
- Contents: main `com.noisefit.apk` (179,508,504 bytes; 18 DEX files), plus ARM64, English, and HDPI split APKs.
- Extracted main APK: `/tmp/noisefit-main.apk`.
- Native libraries observed include `libVeryFitMulti.so` and `libble-crypto.so` (ARM64).
- `libble-crypto.so` exports JNI functions including `Java_com_ryeex_common_ble_stack_BleCipher_nativeEncrypt`, `nativeMixA`, `nativeMixB`, and native register/log functions. This proves native cipher functionality exists, but does not reveal its application handshake, key derivation, or legitimate parameter values.
- DEX metadata includes `com/zhapp/ble` Bluetooth code and Ryeex protocol/data model names.

## Protocol and pairing evidence

### Confirmed directly from NoiseFit APK metadata / method references

- `com.zhapp.ble.BerryBluetoothService` includes methods `checkBondByMac`, `createBond`, `connect`, `connectBerryBluetoothDevice`, and `reflectionCallCreateBond`.
- Related strings include `checkBondByMac false, call createBond`, `checkBondByMac true, call connect`, and bond-state success/failure logs.
- `BerryBluetoothService$BondConnectReceiver` observes `android.bluetooth.device.action.BOND_STATE_CHANGED` and records `BOND_BONDED` / pairing results.
- Android API method references include `BluetoothDevice.getBondState()`, `BluetoothDevice.createBond()`, `BluetoothDevice.connectGatt()`, and bond-state receiver registration. This supports NoiseFit’s system-level bond-check/create-bond-before-connect pattern.
- OpenFit now checks `getBondState()` before GATT connection, calls Android `createBond()` when needed, waits for the bond broadcast, then connects. If a GATT operation explicitly reports insufficient authentication/encryption, the app can request a system bond as a fallback. This implementation is in `app/src/main/java/com/openfit/app/MainActivity.java`.

### Useful but not target-validated third-party reference

- Public project `coolsteel712/VeryLoad` contains a reverse-engineering spec for an IDO/VeryFit-family IDW26 watch-face upload, not the user’s NoiseFit model or its health/pairing protocol: [PROTOCOL_SPEC.md](https://github.com/coolsteel712/VeryLoad/blob/main/docs/PROTOCOL_SPEC.md).
- That reference documents (for that IDW26 capture) vendor service `00000af0-0000-1000-8000-00805f9b34fb`, write characteristic `00000af6-0000-1000-8000-00805f9b34fb`, and notify characteristic `00000af7-0000-1000-8000-00805f9b34fb`.
- It documents V3 framing and D1 watch-face transfer commands, but its transfer checksum is partly unknown and the model/protocol scope is different. Do not use those UUIDs or commands against the user’s watch as assumptions; first inspect its actual GATT services and establish applicability. Watch-face transfer is outside the current OpenFit scope.
- Another public Noise ColorFit Icon Buzz diagnostic project is explicitly read-only and says its guessed FEE7/FEE8/FEE9 UUID family is unvalidated. Do not treat those UUIDs as confirmed.

### Still unknown / not implemented

- Exact GATT service/characteristic UUID roles for the user’s watch.
- Proprietary Ryeex/VeryFit application-level authentication handshake, cipher inputs/key derivation, protocol opcodes, protobuf envelopes, framing/checksums, notification sequence, and health/sleep transfer.
- Whether a successful Android bond alone allows this particular watch to expose useful services/data.
- Whether the standard Heart Rate Service is present on this specific model; if not, OpenFit currently cannot read watch heart rate.
- Proprietary NoiseFit QR provisioning; OpenFit’s QR flow only extracts a Bluetooth MAC address.
- Physical pairing and authenticated communication tests.

### Safety and provenance boundary

- User authorization permits implementing protocol support, but does not itself supply missing protocol facts. Do not send guessed writes/auth bytes to a watch or claim compatibility without validated evidence.
- Do not copy NoiseFit APK/native binary code into OpenFit. Implement independently based on confirmed protocol evidence.
- Static scans with bounded Python scripts were possible, but broad class/string/method scans timed out or were error-prone on this ARM64 low-resource host. The JADX MCP JAR exists at `/home/shiv/jadx-headless-mcp-0.7.1-all.jar` but the REA server is not configured with `REA_JADX_MCP_JAR`; Java/Javac are not on PATH. A JEB MCP server is also unavailable. `adb` exists but no device was connected.
- An Android 12+ phone must grant Nearby devices/Bluetooth scan/connect permissions; system bond may show Android pairing UI.

## Current OpenFit behavior

- Independent app package `com.openfit.app`; Java native Android app; min SDK 26; compile/target 35; AGP 8.6.1, Gradle 8.9; Java compatibility 11; CI JDK 17.
- BLE scan currently filters recognizable Noise/ColorFit names for convenience (not identity proof).
- Connect flow now attempts system bonding before GATT connection, following observed NoiseFit app behavior.
- GATT code only consumes standard Heart Rate Service `0x180D` / Measurement `0x2A37`; no proprietary writes are currently sent.
- Read-only GATT diagnostics display discovered UUIDs and characteristic properties.
- QR accepts a Bluetooth MAC only; no NoiseFit proprietary QR payload parser.
- Local dashboard has Home/Health/Devices/Sleep tabs, local received heart-rate summaries, and manually entered sleep duration; no cloud/account/analytics.

## Next practical steps

1. Install the latest debug APK from the successful GitHub Actions artifact for PR #2 and try adding the watch with it disconnected from NoiseFit. Keep watch awake/nearby and accept Android’s bond prompt.
2. Record the result, exact UI message, Android version, watch model/firmware, and GATT diagnostics. Do not send manual proprietary writes.
3. If system bond succeeds but standard HR service is absent, collect a user-authorized BLE HCI snoop or NoiseFit app-side trace while pairing/reading health data (with secrets/identifiers reviewed before sharing). A safe, authorized capture is needed to establish app-level authentication and data protocol.
4. Configure a proper JADX Java runtime/provider (or use a machine with JADX/JEB) and decompile only the relevant `BerryBluetoothService`, Ryeex GATT stack, protocol parser/serializer, and `BleCipher` call sites. Inspect parameter provenance and call order; do not blindly transplant proprietary code.
5. Implement independently only the verified protocol pieces; add parser/frame unit tests and rerun remote GitHub Actions after each push. Physical device validation remains necessary before calling it functional.

## Project notes

`CONTEXT.md` is the general project handoff. `README.md` describes user-facing features and limitations. This `info.md` is the detailed NoiseFit-specific evidence and investigation log.
