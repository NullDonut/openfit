# OpenFit / NoiseFit BLE Research Notes

Last updated: 2026-11-18

## User request and current status

The user authorized adding NoiseFit-related implementations and asked that findings be saved in this file. The current goal is to make the watch connection functional without inventing an undocumented authentication exchange or copying proprietary NoiseFit implementation code.

## Repository and branch

- Repository: `NullDonut/openfit` (remote `git@github.com:NullDonut/openfit.git`).
- Feature branch: `feature/ble-connection-handling`.
- Existing PR: [#2, Improve BLE watch connection handling](https://github.com/NullDonut/openfit/pull/2), targeting `feat/openfit-android-starter`.
- CI builds the debug APK; the latest green build before this SE change was
  `openfit-debug-b982023`. CI on each push is green with 61 JVM unit tests.
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

- Whether the watch accepts the SE id-17/id-18 account bind and returns
  `bind_check_result=SUCCESS`; the transport is confirmed on hardware, the account reply is not
  yet observed.
- Whether the open BLE link needs an Android system bond for account commands. The vendor's SE
  code path has no bond check (bonding belongs to the Berry classic-SPP path), so OpenFit keeps
  bonding as an on-demand fallback only.
- Proprietary Ryeex/VeryFit application-level authentication handshake, cipher inputs/key derivation, protocol opcodes, protobuf envelopes, framing/checksums, notification sequence, and health/sleep transfer (the RC4 cipher itself is decoded; see `PROTOCOL.md`).
- Whether the standard Heart Rate Service is present on this specific model; if not, OpenFit currently cannot read watch heart rate.
- Proprietary NoiseFit QR provisioning; OpenFit’s QR flow only extracts a Bluetooth MAC address.

### Safety and provenance boundary

- User authorization permits implementing protocol support, but does not itself supply missing protocol facts. Do not send guessed writes/auth bytes to a watch or claim compatibility without validated evidence.
- Do not copy NoiseFit APK/native binary code into OpenFit. Implement independently based on confirmed protocol evidence.
- Static scans with bounded Python scripts were possible, but broad class/string/method scans timed out or were error-prone on this ARM64 low-resource host. The JADX MCP JAR exists at `/home/shiv/jadx-headless-mcp-0.7.1-all.jar` but the REA server is not configured with `REA_JADX_MCP_JAR`; Java/Javac are not on PATH. A JEB MCP server is also unavailable. `adb` exists but no device was connected.
- An Android 12+ phone must grant Nearby devices/Bluetooth scan/connect permissions; system bond may show Android pairing UI.

## Current OpenFit behavior

- Independent app package `com.openfit.app`; Java native Android app; min SDK 26; compile/target 35; AGP 8.6.1, Gradle 8.9; Java compatibility 11; CI JDK 17.
- BLE scan currently filters recognizable Noise/ColorFit names for convenience (not identity proof).
- Connect flow is connect-first: it connects directly, retries transient GATT failures, and starts
  Android system bonding only when the watch reports insufficient authentication/encryption.
- On a watch exposing `16186f00`, the app enables all five characteristics' notifications,
  negotiates MTU 247, and runs the vendor account bind over the SE ("Apricot") chunked-protobuf
  transport: id 0 link greeting → id 16 binding status → id 17 bind check → id 18 bind result, or
  id 19 token verification when already bound. Commands are announced and chunked at `MTU - 5`
  bytes per chunk; inbound announces are acknowledged with `00 00 01 01 00 00` and the chunks are
  reassembled into one `SEWear` protobuf.
- The Berry SPP frame/bind implementation is retained in the protocol package as the documented
  alternate transport, but it is not used on the GATT link.
- Standard Heart Rate Service `0x180D` / Measurement `0x2A37` is still the only heart-rate source.
- Read-only GATT diagnostics display discovered UUIDs and characteristic properties, and the
  `PAIRING LOG` records TX/RX hex, chunk counts and the bind state.
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
  magics `0x5A486666` (app→dev), `0x5A488888` (dev→app), `0x5A489999` (ack),
  `0x5A481111` (large file); `event` 1=protobuf / 3=private, `type` 3=protobuf / 2=private.
  *(Corrected 2026-11-18: this pass originally had the two direction magics the wrong way round;
  see the 2026-11-18 entry below.)*
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
  (`BerryFrame`, `BerryBinding`, `BerryFrameReader`, `BerrySewear`, `BerryProfile`), verified
  locally with javac + JUnit 4.13.2 and wired into `MainActivity` (MTU, chunked writes,
  reassembly, bind state machine, per-watch token).

Still open at that point: whether this watch answers the Berry bind over GATT or expects classic
SPP; inner field numbers of `SEFitness`/`SESettingMenu`/`SEFactory`; Ryeex opcodes and handshake.

## 2026-11-17/18 SE (Apricot) transport instead of Berry SPP

The first `16186f00` write attempt (build `b982023`) answered a Berry frame with
`00 00 01 02 00 00`, the SE stack's busy control frame. That re-opened `BluetoothService`
(`com.zhapp.ble`, `DEVICE_PROTOCOL_APRICOT`) and produced:

- Control frames `00 00 <code BE16> <payload LE16>` (`0100` received, `0101` ready, `0102` busy,
  `0103` done, `0105 n` resend).
- Outbound announce `00 00 00 00 <count LE16>` on `6f02`, then `<index LE16> <chunk>` × count;
  chunk data `MTU - 5` bytes (`k = MTU - 3`).
- Inbound announce on `6f01`, phone replies `00 00 01 01 00 00`, then indexed chunks, then
  `00 00 01 00 00 00`.
- The vendor SDK's first command on connect is `SEWear{id=16}` (`ZhConnectHandler.onConnectState`
  → `ControlBleTools.requestDeviceBindState` → `a.L0(16)`). The id-0
  `SEWear{ble_connect_parmeter_config{}}` (`9A 06 00`) is app-level MTU setup.

Account bind ids (`SEFunctionId`): 16 binding status, 17 `bind_check{device_verify:true}`,
18 `bind_result{user_id}`, 19 `verify_user_number`, 23 unbind. The vendor sends id 16 on connect,
then 17 (unbound) or 19 (already bound), then 18 with a token shaped like
`(UUID + random + userId).substring(30)`. Note that `SEVerifyResult.verify_result_type` (reply to
id 19) is a **bool** where true means verified; the bind result fields are enums.

Device test of build `cd68343` (Android 16, `D4:27:1D:30:44:9B`): MTU 247, notifications on all
five chars, greeting and id 16 announced and chunked, watch replied `01 01` ready, `01 02` busy,
then `01 00` received — but no SEWear reply. Root cause: the vendor ships **two** `SEWear`
schemas. The BLE one is `com.zh.ble.wear.protobuf.SEWear` (`id=1`, `change_mtu=2`,
`bind_account=3`, `ble_connect_parmeter_config=99`, `error_code=100`, no type). The Berry SPP one
is `com.zh.ble.sa_wear.protobuf.SEWear` (`type=1`, `id=2`, `bind_account=4`). OpenFit had encoded
id 16 with the SPP numbers (`10 10`), which the watch read as `change_mtu=16`; the correct bytes
are `08 10`. The two codecs are now separate (`BerrySewear` for SPP, `SeWear` for BLE), with 59
protocol unit tests (all passing locally) and a successful full-app compile against API 35.

Device test of build `975af4a` (same phone/watch): the watch finally answered the first command:

```
RX 16186f01 · 010008001000A00601     (index 1, data 08 00 10 00 A0 06 01)
SEWear · SEWear{id=0, error=1}
```

Decoded: `SEWear{id=0, change_mtu=0, error_code=1}` = `MTU_REQUEST_CHANGE`, MTU 0,
`NOT_SUPPORT`. That is the reply to the id-0 greeting the app sent immediately before id 16, not
a bind reply. It also proves the envelope encoding is now correct. Id 16 itself still got only
the transport ack (`01 00`) and no `SEWear` reply.

The next build removed the id-0 greeting, waited 400 ms after notification setup, sent the id-16
probe first (as the vendor SDK does), then the explicit form (`08 10 1A 02 08 01`), then the
populated id-0 MTU config (`9A 06 03 08 F7 01`, MTU 247) plus id 16, and finally id 17
`bindCheck{deviceVerify:true}`.

Device test of build `fef5963` (2026-11-18 09:45) answered the question. Both account probes came
back as a bare error:

```
RX 16186f01 · 010008101000A00601   SEWear · SEWear{id=16, error=1}
RX 16186f01 · 010008111000A00601   SEWear · SEWear{id=17, error=1}
```

`SEWear{id=16, error_code=NOT_SUPPORT}` and `SEWear{id=17, error_code=NOT_SUPPORT}`. So the SE
transport, the BLE `SEWear` envelope and the `SEFunctionId` numbering are all correct, and this
firmware does not implement the account bind on the SE channel at all.

NoiseFit's other pairing stack explains it. `BluetoothService.connect(name, address, protocol)`
routes `DEVICE_PROTOCOL_BERRY` to `BerryBluetoothService` — a **bonded classic RFCOMM socket on
the standard SPP UUID** — and `ZhConnectHandler.onConnectState` then calls
`setUserIdByBerryProtocol(token, model, version)` instead of `requestDeviceBindState`. That is the
private Berry command exchange (`private cmd 2` with `UserIdBean`, then `3`, then `4`), which
OpenFit already implements in `BerryBinding`; only the socket was missing.

OpenFit now treats a refused SE account exchange as "Berry model": it asks Android to bond, opens
the SPP socket (`BerrySpp`), and runs the private 2/3/4 exchange with the stored token. It also
sends the read-only id-32 `GET_DEVICE_INFO` probe next to id 17 so a `SEWear{id=32, …}` reply
would prove the SE command set works while the bind alone is unsupported.

`fee7` belongs to the bundled CRP stack (`com.crrepa.ble`) used by other Noise models; the Berry
SDK does not use it, and the SE channel works without it.

## 2026-11-18 10:10 — bonded, but no RFCOMM (build `bd6110e`)

The same phone/watch pair, with the classic fallback from that build. The bond was acquired
(`Bond state 10 -> 11`, then `11 -> 12`), so the system-pairing prompt is not the blocker. The
socket still failed, twice, about 7-13 s after it was opened:

```
bonded · opening the classic link
opening the classic SPP socket
IOException · read failed, socket might closed or timeout, read ret: -1
```

Android reports that text when the remote end has no RFCOMM listener. The watch was found by a BLE
scan and bonded as an LE device, so the most likely explanation is simply that it has no classic
radio to listen on. The build now logs `BluetoothDevice.getType()` and `getUuids()` (the cached SDP
records) at the start of the Berry phase so the next report settles it: `DEVICE_TYPE_LE` with no
SDP records makes SPP impossible and the step can be deleted.

### Three Berry bugs found while tracing it

Re-reading the vendor Berry code against OpenFit turned up three defects, two of which would have
made any Berry watch ignore the bind regardless of the bearer:

1. **Swapped head magics.** `BERRY_APP_REQ_DEV_HEAD_VALUE` is `0x5A486666`, i.e. the phone sends
   `66 66 48 5A`; `BERRY_DEV_REQ_APP_HEAD_VALUE` is `0x5A488888`, the watch's own magic
   (`BERRY_APP_REQ_DEV_HEAD = {102, 102, 72, 90}` confirms the byte order). OpenFit had the two
   constants exchanged, so every command carried the watch's magic in the wrong direction and was
   dropped without any error frame.
2. **Missing inbound ack.** Both Berry services call `replyAsk(cmdDataBean.getAskCmd(0))` *before*
   parsing each received frame: a bare 8-byte `99 99 48 5A <serial> 00 00 00`. The exchange is
   stop-and-wait, so a phone that never acks gets one frame and then silence. OpenFit now acks
   every inbound Berry command frame.
3. **No BLE bearer at all.** `Berry` is not the only Berry transport. `BluetoothService.connect`
   routes `DEVICE_PROTOCOL_BERRY_TEST` and `DEVICE_PROTOCOL_BERRY_RING` to `BerryRingService`,
   which is a plain `BluetoothGatt` client; `writeBerryCmd` pushes the *same* `CmdDataBean` frames.
   Berry-Test uses service `aa01` (write `aa02`, data on `aa05`, log on `aa03`) and Berry-Ring uses
   `00006666` (write `0000005f`, read `0000005e`).

The watch exposes `aa01` with `aa02` (read/write) and `aa03` (read/write/notify) — the Berry-Test
shape minus `aa05`. So the fallback now tries, in order: BLE Berry on `aa01` (private cmd 2, then
an `SEWear{type=ACCOUNT}` account query if that is silent), then the classic bond + SPP path. The
SE `id-32` probe was dropped from the hot path: sending it back-to-back with id 17 produced
`control resend 1` and no reply, which only added noise to an already settled conclusion.

`fee7` belongs to the bundled CRP stack (`com.crrepa.ble`) used by other Noise models; the Berry
SDK does not use it, and the SE channel works without it.

## 2026-11-18 10:57 — the announcement was rejected because GATT was still busy

This run was still on the previous build (`bd6110e`): the log has no `device type` / `SDP records`
/ `Berry-over-BLE channel` lines. It failed one step earlier than the 10:10 run, and the reason is
worth recording because it was an app-side race, not a protocol answer:

```
10:57:47  Services: profile=Noise, 6 service(s), 5 notifiable
10:57:52  Notification setup timed out
10:57:52  Connection ready · profile=Noise · notifications enabled
10:57:52  SE transport MTU negotiated · 247 bytes
10:57:52  TX binding-status · 0810
10:57:52  announce binding-status · 1 chunk(s)     (x5)
10:57:52  SE account exchange failed · the watch rejected the command announcement
```

The watch answered nothing because nothing was sent. The notification budget was a single 5 s for
all five CCCDs; it expired while a descriptor write was still outstanding, and the app started the
SE exchange anyway. Android allows one GATT operation at a time, so every `writeCharacteristic` on
`6f02` was rejected locally (the app's `writeSe` returned false five times in the same second).
The pending descriptor callback then arrived at 10:57:57, which is why `Connection ready` appeared
twice. Nothing was wrong with the SE channel or with the watch.

Fixes in this build:

* the notification budget is 15 s and the log now names each characteristic as its descriptor is
  written (`notifications: enabling 6f02`, `notifications: enabled 6f02`);
* `onConnectionReady` is idempotent, so a late descriptor callback cannot start a second setup;
* a rejected announce is retried five times at 350 ms instead of 80 ms, which lets an in-flight
  GATT operation finish.

The same GATT report confirms the BLE Berry shape: `aa02` is `READ WRITE` (so the Berry channel
uses acknowledged writes and always gets a callback), `aa03` is `READ WRITE NOTIFY`, and
`16186f02` is `READ WRITE_NO_RESPONSE NOTIFY` as assumed.

## 2026-11-18 — the Ultra Buzz bind is ZhBracelet (NavPlus), not SE or Berry

The 11:03 device run finally exercised the fixed notification setup: all five `16186f0x`
characteristics plus `aa03` were subscribed before pairing started, the SE announce/chunk exchange
ran cleanly, and the watch answered every frame. The answer was still authoritative:
`SEWear{id=16, error=1}`, `{id=0, error=1}`, `{id=17, error=1}` — `NOT_SUPPORT` for the whole
account group. BLE Berry on `aa02` stayed silent, and the classic fallback cannot exist on an
LE-only watch (`device type LE only (2)`, `SDP records none`).

The missing evidence was in the vendor APK, not on the wire. `NavPlusConnectHandler` (the handler
for this model family) binds through a different bundled SDK:

```
com.noisefit_nav_plus.handler.connect.NavPlusConnectHandler
    → com.zjw.zhbraceletsdk.service.ZhBraceletService
    → bindDevice(0) on first pairing, bindDevice(1) on reconnect
    → com.zjw.zhbraceletsdk.service.c.c(type) → a.b(type)
    → a(byte[]{type}, 1, 108)
```

`com.zjw.zhbraceletsdk.service.d` names the services and they are exactly the two GATT blocks the
watch exposes that OpenFit had not used:

```
00000001-0000-1000-8000-00805f9b34fb   system service
00000002-…                            request  (read/write/write-no-response)
00000003-…                            reply    (read/write/notify)
16186f00-0000-1000-8000-00807f9b34fb   protobuf service (used after bind)
```

The bind frame is plaintext on `00000002`:

```
AB 00 00 06 00 00 00 00 01 00 6C 00 01 00
```

`AB` = magic, `00 06` = length-8, `01` = phone→watch, `6C` = command 108 (bind), `00 01` =
one-byte payload, `00` = bind type. The watch replies on `00000003` with command key `0x34` (52)
and payload `TT RR`: type and result (`1` = accepted/bound, `0` = refused/unbound). `q(byte[])`
in the vendor's `BleProtocol` maps that straight to `BindDeviceListener.bindSuccess()` /
`deniedByDevice()` / `bindError()`. No token, no encryption, no time sync is needed first.

OpenFit now:

* subscribes to `00000003` during connection setup;
* after SE refuses, tries the ZhBracelet bind (`type 0`, then `type 1` after 15 s) before Berry;
* logs `TX zh-bind type …`, `RX 00000003 · …` and `ZhBracelet bind info · type=… result=…`;
* no longer starts the classic SPP path at all (the file was removed).

`NavPlusQueryDeviceUnitsHandler`/`DataUnitConverter` special-case `COLORFIT_ULTRA_BUZZ`,
`COLORFIT_VISION_BUZZ` and `COLORFIT_ULTRA_2`, which ties this stack to the user's watch.

**Validated on the watch on 2026-11-18 11:49 (build `b701e4a`):**

```
11:49:04  notifications: enabled 00000003
11:49:05  Connection ready · profile=Noise · notifications enabled
11:49:22  TX zh-bind type 0 · AB0000060000000001006C000100
11:49:25  RX 00000003 · AB0000079700004E03003400020001
11:49:25  ZhBracelet bind info · type=0 result=1
11:49:25  PAIRED  ZhBracelet bind accepted (type 0)
```

The watch left pairing mode and reached its home screen/clock. The reply carries `97 00 00 4E` in
header bytes 4..7 (not a standard CRC32 of the payload or frame; the vendor parser ignores it for
key 52). This is the first confirmed vendor-level pairing. SE still answers `NOT_SUPPORT` on the
same link, so the SE prober can eventually be demoted or skipped for this model to save ~20 s.
Still pending: confirming that the binding survives a reconnect (type 1 should then answer
"Bound") and implementing the post-bind Zh commands (time sync, user info, device info).

## Project notes

`CONTEXT.md` is the general project handoff. `HANDOFF.md` is the current single-page handoff for machine setup, tooling paths, host limits and next steps. `README.md` describes user-facing features and limitations. `PROTOCOL.md` holds the recovered wire protocol. This `info.md` is the detailed NoiseFit-specific evidence and investigation log.
