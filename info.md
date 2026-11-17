# OpenFit / NoiseFit BLE Research Notes

Last updated: 2026-11-17

## User request and current status

The user authorized adding NoiseFit-related implementations and asked that findings be saved in this file. The current goal is to make the watch connection functional without inventing an undocumented authentication exchange or copying proprietary NoiseFit implementation code.

## Repository and branch

- Repository: `NullDonut/openfit` (remote `git@github.com:NullDonut/openfit.git`).
- Feature branch: `feature/ble-connection-handling`.
- Existing PR: [#2, Improve BLE watch connection handling](https://github.com/NullDonut/openfit/pull/2), targeting `feat/openfit-android-starter`.
- CI builds the debug APK; the latest green build before this SE change was
  `openfit-debug-b982023`. CI on each push is green with 52 JVM unit tests.
- The protocol layer is compiled and tested locally with `javac` + JUnit 4.13.2 against JDK 21,
  and the whole app compiles against the API 35 `android.jar`. Local Gradle/Android SDK are
  still unavailable, so packaging and installation go through GitHub Actions; no physical
  watch is attached to the development host.

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

- Which of the three shipped stacks (Berry, Ryeex, Realtek BBPro) the user's watch exposes; the
  Berry profile is now fully documented in `PROTOCOL.md`, so a single GATT dump of the watch
  would settle it.
- Proprietary Ryeex/VeryFit application-level authentication handshake, cipher inputs/key derivation, protocol opcodes, protobuf envelopes, framing/checksums, notification sequence, and health/sleep transfer (the RC4 cipher itself is decoded; see `PROTOCOL.md`).
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

## 2026-11-17 decompilation pass (new evidence)

Tooling: no JVM existed on the host, and `doas` needs a password, so a Temurin JDK 21 and JADX
CLI 1.5.1 were installed **without root** into `/tmp/opencode/tools` (tmpfs, lost on reboot).
`REA_JADX_MCP_JAR` could not be set in the already-running MCP server, so the JADX provider was
bypassed and the CLI used directly. This is now fixed persistently: `JAVA_HOME` and
`REA_JADX_MCP_JAR` are exported from `~/.profile`/`~/.bashrc`, the JDK lives at
`/home/shiv/.local/share/jadx-jdk/jdk-21.0.13+9`, and `~/.config/opencode/opencode.json` sets
both in the `rea` server's `environment` block. See `HANDOFF.md`. Decompilation was done **one
DEX at a time with `-j 1`** — a 4-thread full-APK run exhausted memory and disturbed the user's
desktop session; do not repeat that.

Artifacts: XAPK SHA-256 `cfda8cf…3adc7`, base APK SHA-256 `5ae827e…b4eae2`
(`/tmp/noisefit-main.apk`, 18 DEX files).

Verified in this pass (details and wire tables in `PROTOCOL.md`):

- The app carries three independent watch stacks: Berry (`com.zhapp.ble`), Ryeex
  (`com.ryeex.*`, RC4 cipher), and Realtek BBPro (`com.realsil.sdk.bbpro`).
- Berry GATT profile: service `16186f00-0000-1000-8000-00807f9b34fb` (note vendor base
  `00807f9b34fb`), chars `…6f01`…`…6f05`; writes go to `…6f02`, all five notify.
- Berry frame: `head(4, LE) | serialNo(1) | event(1) | type(1) | len(2, LE) | payload`, with
  magics `0x5A488888` (app→dev), `0x5A486666` (dev→app), `0x5A489999` (ack),
  `0x5A481111` (large file); `event` 1=protobuf / 3=private, `type` 3=protobuf / 2=private.
- `SEWear` protobuf schema (type=1, id=2, payload fields 3..21, error_code=100).
- Bind sequence: private `cmdId=1` (protocol description) → `cmdId=2` (`UserIdBean` account
  identity) → `cmdId=3` (bind request, only if not already bound) → `cmdId=4` (confirmation
  ± flag). The watch answers cmd 3 with a bind status byte and cmd 4 with 0/1. **No encryption
  or challenge/response was found on the Berry path** — the earlier assumption that a proprietary
  encrypted handshake blocks pairing does not hold for this stack.
- `libble-crypto.so` is fully decoded: `nativeEncrypt` is RC4 seeded with a fixed 256-byte
  permutation from `.rodata`, keyed by the first array argument; `mixA`/`mixB` are byte
  permutations of MAC + 16-bit pid; `regA/regB/logA/logB/logC` are hard-coded ints.
- Implemented independently in `app/src/main/java/com/openfit/app/protocol/`
  (`BerryFrame`, `BerryBinding`, `BerryFrameReader`, `BerrySewear`, `BerryProfile`) with 28
  unit tests, verified locally with javac + JUnit 4.13.2 and wired into `MainActivity` (MTU,
  chunked writes, reassembly, bind state machine, per-watch token). The bind write path is not
  yet validated against a watch.

Still open: whether this watch answers the Berry bind over GATT or expects classic SPP; inner
field numbers of `SEFitness`/`SESettingMenu`/`SEFactory`; Ryeex opcodes and handshake.

## Project notes

`CONTEXT.md` is the general project handoff. `HANDOFF.md` is the current single-page handoff for machine setup, tooling paths, host limits and next steps. `README.md` describes user-facing features and limitations. `PROTOCOL.md` holds the recovered wire protocol. This `info.md` is the detailed NoiseFit-specific evidence and investigation log.
