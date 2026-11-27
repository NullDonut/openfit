# OpenFit protocol notes (from NoiseFit 5.5.0 static analysis)

Status: **Vendor pairing confirmed on hardware (2026-11-18).** The GATT layout, notification
setup, MTU negotiation and the SE announce/ready/chunk transport have been validated against a
physical ColorFit Ultra Buzz. The watch does not speak the Berry SPP frames on `16186f00`; it uses
the vendor's generic SE ("Apricot") chunked protobuf transport. The `SEFunctionId` account bind
(16 → 17 → 18, or 19 when already bound) is implemented in OpenFit but this firmware answers the
whole account group with `NOT_SUPPORT`. The bind that this model accepts is the ZhBracelet
("NavPlus") key-108 exchange on service `00000001`, confirmed on hardware on 2026-11-18.

## Provenance

| Item | Value |
|---|---|
| Supplied package | `/home/shiv/com.noisefit_5.5.0.xapk`, 312,617,971 bytes |
| XAPK SHA-256 | `cfda8cf8ce2a3d4e79d808979db3fedb7825faa090f3cb67cd29d6401d33adc7` |
| Base APK member | `com.noisefit.apk`, 179,508,504 bytes |
| Base APK SHA-256 | `5ae827e908fbe8730fc122b0d825ca140520808a6a8f6f73312aa928e8b4eae2` |
| Code | 18 DEX files, R8-minified but not name-obfuscated inside `com.zhapp.*`, `com.zjw.*`, `com.ryeex.*` |
| Decompiler | JADX CLI 1.5.1 (Temurin JDK 21), per-DEX target decompilation |
| Native | `libble-crypto.so` (10,896 bytes, stripped, NDK r24), read with `readelf`/`objdump` |

The app contains **three** watch stacks. A hardware GATT dump (see below) shows that the
ColorFit Ultra Buzz exposes the Berry service, and also the Ryeex base service. OpenFit selects a
profile by discovered GATT service, not by name; Berry wins when both are present.

| Stack | Package | Transport | Cipher |
|---|---|---|---|
| Berry | `com.zhapp.ble`, `com.zjw.zhbraceletsdk` | GATT `16186f00-…` (SE chunked protobuf) + optional SPP `00001101-…` | none observed |
| Ryeex | `com.ryeex.ble`, `com.ryeex.common.ble.stack` | GATT `00000001-…` | RC4 variant in `libble-crypto.so` |
| Realtek BBPro | `com.realsil.sdk.bbpro` | GATT (Realtek) | BBPro authentication (link keys) |

## Hardware validation (ColorFit Ultra Buzz, 2026-11-17)

First dump taken from the watch by OpenFit on Android 16 (API 36). The link was established
connect-first: the watch showed as **not bonded** in Android while the GATT link was connected.

| Service | Characteristics (properties) |
|---|---|
| `00001800-…` (GAP) | `2a00`, `2a01`, `2a04` (read) |
| `00001801-…` (GATT) | — |
| `0000aa01-…` | `aa02` (read/write), `aa03` (read/write/notify) |
| `0000fee7-…` | `fea1` (read/write/notify), `fec9` (read) |
| `16186f00-…-00807f9b34fb` (Berry/SE) | `6f01`–`6f05`, all read/write-no-response + notify |
| `00000001-…-00805f9b34fb` (ZhBracelet/NavPlus) | `00000002` (read/write, write-no-response), `00000003` (read/write/notify) |

Conclusions (updated after the successful 2026-11-18 bind):

- Service presence alone does not identify the protocol. This watch exposes **three** stacks: SE on
  `16186f00`, Berry-over-BLE on `aa01`, and ZhBracelet on `00000001`. Only the last one answers
  the vendor bind for this model.
- SE accepts the chunked-protobuf transport and returns well-formed replies, but the whole
  account group answers `NOT_SUPPORT` (`SEWear{id=16|0|17, error=1}`).
- Berry-over-BLE (`aa02`/`aa03`) stays silent; classic SPP is impossible (`device type LE only`,
  `SDP records none`).
- The ZhBracelet bind on `00000002` succeeds: `AB 00 00 06 00 00 00 00 01 00 6C 00 01 00` is
  answered with `AB 00 00 07 97 00 00 4E 03 00 34 00 02 00 01`, i.e. key 52, payload `00 01`,
  type 0 and result 1. The watch then leaves pairing mode and shows its home screen.
- Android bonding is not required for the GATT link (the watch was not bonded during the first
  successful connection), which supports OpenFit's connect-first ordering.
- `0000fee7` does not appear in the Berry, SE or ZhBracelet sources; the only bundled match is the
  CRP stack (`com.crrepa.ble`, `fee7`/`fee9`), used by other Noise models. It is not touched by
  OpenFit and the bind works without it.
- No proprietary write was sent during the GATT dump above. A later write attempt proved that
  `16186f00` speaks the SE chunked-protobuf transport, not the Berry SPP frames. OpenFit now
  implements the SE transport and the id 16/17/18 account bind on this service.
- A follow-up run (same phone/watch, MTU 247, build `cd68343`) confirmed the transport: the watch
  answered the announce with `01 01` (ready), accepted the chunk with `01 00` (received), and
  produced `01 02` (busy) when the next command was announced too early. The first request used
  the Berry SPP field numbers (`10 10`), which the watch read as `change_mtu=16`; the corrected
  BLE envelope (`08 10`) is in the current build. Build `975af4a` then produced the first real
  reply, `SEWear{id=0, change_mtu=0, error_code=NOT_SUPPORT}`, i.e. the watch rejects the id-0
  MTU config; id 16 itself is still unanswered. See the on-device status note below.


## Berry profile (alternate transport)

### GATT roles

Service (note the vendor base `…-00807f9b34fb`, not the SIG `…-00805f9b34fb`):

```
16186f00-0000-1000-8000-00807f9b34fb   Berry protobuf service
```

| Characteristic | Role observed in code |
|---|---|
| `16186f01-…` | bulk/large-data responses (`BluetoothService.b/c`) |
| `16186f02-…` | **primary write channel** — every `writeCmd` targets this |
| `16186f03-…` | large data + progress (`a(int,int)` writes here) |
| `16186f04-…` | large file data / "loses" reporting |
| `16186f05-…` | Alexa/side channel (`writeDataOfAlexa`) |

All five get `setCharacteristicNotification(true)` + CCCD `00002902-…` writes on discovery;
the app then waits for descriptor callbacks before marking the connection usable.

Other services the stack knows (all SIG base `0000805f9b34fb` unless noted):
`00000001/00000002/00000003` (base service/write/read), `00002001-00002003` (big data),
`00003e01/00003e03` (ECG), `00002c01/00002c03` (PPG), `00003d01/00003d02` (imager),
`00001601-00001603` (theme), `00001701/00001703` (log), `0000aa01-0000aa05`,
`00001800/00002a00` (GAP device name), `0000fe78-…` (scan record).

### Transport on `16186f00`: SE ("Apricot") chunked protobuf

**Hardware result, 2026-11-17:** this service does not accept Berry `66 66 48 5A` frames.
OpenFit's first attempt put a Berry frame on `6f02`; the watch read the first two bytes as packet
number `0x8888` and answered `00 00 01 02 00 00`, the SE stack's "busy" control frame. The vendor
app routes `16186f00` through `com.zhapp.ble.BluetoothService` (protocol string `Apricot`); the
Berry SPP classes are used only over classic SPP. OpenFit therefore implements the SE framing first
and keeps the Berry SPP bind below as the alternate transport.

Control frame: `00 00 <code:2 BE> <payload:2 LE>`. The codes handled by
`BluetoothService.j(byte[])` are:

| Code | Bytes | Meaning |
|---|---|---|
| `0x0100` | `00 00 01 00 00 00` | phone: reply fully received |
| `0x0101` | `00 00 01 01 00 00` | ready to receive; the phone also sends this to answer an announce |
| `0x0102` | `00 00 01 02 00 00` | device busy; drop or retry |
| `0x0103` | `00 00 01 03 00 00` | transfer complete |
| `0x0105` | `00 00 01 05 <n LE16>` | resend chunk `n` (1-based) |

Outbound command (`BluetoothService.g` CMD thread, `G()`, `c(int)`):

1. `6f02` ← `00 00 00 00 <chunkCount LE16>` (announce; the vendor writes the two-byte
   count as `<count lo>` then `00`, so counts stay below 256).
2. `6f02` ← `00 00 01 01 00 00` from the watch (ready).
3. `6f02` ← `<index LE16> <chunk>` for index 1..N. Chunk data is `MTU - 5` bytes
   (`k = MTU - 3`, data `k - 2`) and `chunkCount = ceil(length / (MTU - 5))`.
4. `6f02` ← `00 00 01 03 00 00` from the watch (done); `01 02` means busy and `01 05 n`
   asks for chunk `n` again.

Inbound reply (`BluetoothService.i`, handler for `6f01`):

1. `6f01` → phone: `00 00 00 00 <count LE16>` (announce).
2. phone → `6f01`: `00 00 01 01 00 00`.
3. `6f01` → phone: `<index LE16> <chunk>` × N.
4. phone → `6f01`: `00 00 01 00 00 00`; the concatenated chunks are one `SEWear` protobuf.

`16186f02` is both the write channel and the channel that carries the control frames; `6f03`–`6f05`
use the same announce/chunk scheme for their own payloads.

The vendor SDK's first SE command after connection is the account probe, **not** the id-0 link
config: `ZhConnectHandler.onConnectState` → `ControlBleTools.requestDeviceBindState` →
`a.L0(16)`. The id-0 `SEWear{ble_connect_parmeter_config{}}` (`9A 06 00`) greeting belongs to the
app layer's MTU setup and is fire-and-forget; see the hardware note below.

#### SE `SEWear` envelope (different schema from the Berry SPP one)

The vendor ships **two** protobuf packages with a `SEWear` message. The BLE service uses
`com.zh.ble.wear.protobuf.SEWear`; the classic-SPP Berry path uses
`com.zh.ble.sa_wear.protobuf.SEWear`. Mixing their field numbers silently changes the meaning of
a command, which is exactly what happened in the first on-device test: `10 10`
(`sa_wear` encoding of id 16) was parsed by the watch as `change_mtu=16`, acknowledged with
`00 00 01 00 00 00` and otherwise ignored. The correct id-16 request is `08 10`.

| Field | Number | Notes |
|---|---|---|
| `id` | 1 | `SEFunctionId` |
| `change_mtu` | 2 | |
| `bind_account` | 3 | `SEBindAccount` |
| `device` | 4 | |
| `system_time` | 5 | |
| `user_settings` | 6 | |
| `watch_face` | 7 | |
| `sporting` | 8 | |
| `fitness` | 9 | |
| `weather` (`weasether` in the generated code) | 10 | |
| `large_file` | 11 | |
| `micro_function` | 12 | |
| `notification` | 13 | |
| `music` | 14 | |
| `setting_menu` | 15 | |
| `factory` | 16 | |
| `aleax` | 17 | |
| `ble_connect_parmeter_config` | 99 | |
| `error_code` | 100 | `CommonProtos.SEErrorCode` |

The BLE schema has **no `type` field**. `SEWear.bind_account` and the other payloads are message
fields, so an explicitly-set but empty payload still serializes as tag + length 0 (for example
id 18 with an empty result is `08 12 1A 00`). The `sa_wear` schema instead has `type=1`,
`id=2`, `bind_account=4`; OpenFit keeps that codec separately in `BerrySewear` for the SPP path.

#### Account bind over SE (`SEFunctionId` + `SEBindAccount`)

From `ControlBleTools` (`bindDevice`, `requestDeviceBindState`, `sendAppBindResult`,
`verifyUserId`) and `BindAccountProtos`:

| Id | Phone message | Watch reply |
|---|---|---|
| 0 | `SEWear{ble_connect_parmeter_config{}}` (`9A 06 00`) | `SEWear{id=0, change_mtu, error_code}`; this watch answers `NOT_SUPPORT` |
| 16 | `SEWear{id=16}` | `bind_account{request_binding_status}` |
| 17 | `SEWear{id=17, bind_account{bind_check{device_verify:true}}}` | `bind_check{bind_check_result, mac, serial_number, device_number, firmware_version, device_name}` |
| 18 | `SEWear{id=18, bind_account{bind_result{user_id:token}}}` | `bind_result{bind_result_type}` and/or `error_code` |
| 19 | `SEWear{id=19, bind_account{verify_user_number:token}}` | `bind_account{verify_result{verify_result_type}}` |
| 23 | `SEWear{id=23}` | unbind |

`SEBindAccount` fields: `request_binding_status=1`, `bind_check=2`, `bind_result=3`,
`inquiry_pairing_status=4`, `request_pairing_result=5`, `verify_user_number=6`,
`verify_result=7`, `classic_bluetooth_status=8`.

`SEBindCheck`: `device_verify=1`, `bind_random_key=2`, `bind_check_result=3`,
`device_number=4`, `mac=5`, `serial_number=6`, `firmware_version=7`, `device_name=8`.

`SEBindResult`: `bind_result_type=1`, `user_id=2`, `phone_type=3`.
`SEBindResultType`/`SEBindCheckResult`: 0=SUCCESS, 1=REFUSE, 2=OVER_TIME,
3=VERIFICATION_FAILED (the last only in `SEBindCheckResult`). `SEPhoneType`: ANDROID=0, IOS=1.

`SEVerifyResult`: `verify_result_type=1` is a **bool** and `binding_status=2` is a bool.
`verify_result_type=true` means the token was accepted; the vendor SDK converts false to a
failure state. It is not an enum, unlike the bind result fields.

Exact phone bytes: id 16 `08 10`; id 17 `08 11 1A 04 12 02 08 01`; id 18
`08 12 1A <len> 1A <len> 08 00 12 <len> <user id> 18 00`; id 19 `08 13 1A <len> 32 <len> <token>`.

These schemas are **proto2**, not proto3: the generated classes carry `hasBindResultType()` /
`hasPhoneType()` and an `isInitialized()` check, so a field set to its default value is still
serialized. The vendor encoder writes `bind_result_type=SUCCESS(0)` and `phone_type=ANDROID(0)`
explicitly, hence the `08 00` and `18 00` in the id-18 frame. Omitting them (a proto3-default
assumption) leaves the frame short; the watch still acknowledges it with `error_code=0` but reports
"pairing failed" on its own screen.

NoiseFit on connect: id 16; if not bound → id 17 with `device_verify:true`; on
`bind_check_result=SUCCESS` → id 18 with a random token; if already bound → id 19 with the token
from its local database. Its token is `(UUID + random(10..9999) + userId).substring(30)`; OpenFit
uses the same shape with a local placeholder user id and stores it per watch address.

The reply's `device_verify` flag reports whether the watch itself performed a device verification;
it is **not** a gate on the bind. The vendor order is "bind check SUCCESS → id 18". The Noise Watch
Beat answers id 17 with `bind_check_result=SUCCESS` and `device_verify` clear, so OpenFit now
continues to id 18 for both values and stops only when the result enum is not SUCCESS.

#### SE liveness probe (ids 32/33)

The account group and the rest of the SE command set are independent. A Buzz that answers ids 16-19
with `NOT_SUPPORT` may still implement ordinary SE commands, so OpenFit probes two read-only ids
that need no account before falling back to ZhBracelet:

| Id | Phone bytes | Purpose |
|---|---|---|
| 32 | `08 20` | `SEGetDeviceInfo` — model and firmware |
| 33 | `08 21` | battery status |

Both travel through the normal announce/chunk transport as soon as the account group is refused or
silent, and the probe is bounded to 4 s so it cannot delay the ZhBracelet fallback indefinitely. On a
saved-binding reconnect the SE exchange is skipped entirely, so the same two probes run once the
ZhBracelet bind is confirmed; that run only reports and never changes the pairing result. The result
is one line in the connection log:

- `SE command set is alive · N of 2 probes answered` — SE carries settings and heart rate.
- `SE command set refused the probes too` — the whole SE surface is stubbed out on this firmware.
- `SE command set silent` — no evidence either way; the transport itself may be the problem.

Reply payloads are logged as raw hex; the `SEGetDeviceInfo` / battery field numbers are not decoded
yet.

**Verdict on the ColorFit Ultra Buzz (2026-11-18 18:39):** silent. Ids 32 and 33 were both written
through the announce/chunk transport, the transport acknowledged both chunks, and the watch replied
to neither before the 4 s window closed (`SE command set silent · only the ZhBracelet channel
answers`). Combined with ids 16/17 answering `NOT_SUPPORT`, the SE surface on this firmware is
stubbed end to end. Heart rate therefore comes from the ZhBracelet channel (see below), not SE.

#### Noise Watch Beat (2026-11-18): the SE bind is the real path

A second physical device, the Noise Watch Beat (`00:00:00:7A:55:0F`, firmware 1.1.8, name
"NoiseFit Crew_550F"), exposes a different service set from the Ultra Buzz:

| Service | Notes |
|---|---|
| `16186f00-…` | the same five write-no-response + notify characteristics |
| `0000aa01-…` | `aa02` write, `aa03` read/write/notify (silent, as on the Buzz) |
| `000077d0-…` | extra: `77d1` write, `77d2` notify — frames not recovered, unused |
| `00000001-…` | **absent**, so there is no ZhBracelet channel and no NavPlus bind |

Unlike the Buzz, this firmware implements the SE account group. The exchange reached the watch and
was answered (MTU 247, announce → `01 01` ready, chunk → `01 00` received):

```
TX 08 10                                  id 16
RX 08 10 1A 02 08 00                      bind_account{request_binding_status:false}
TX 08 11 1A 04 12 02 08 01                id 17, bind_check{device_verify:true}
RX 08 11 1A 42 12 40 08 00 18 00 …        bind_check{device_verify:false,
                                             bind_check_result=SUCCESS,
                                             device_number="30026", mac="00:00:00:7a:55:0f",
                                             serial_number="10005", firmware_version="1.1.8",
                                             device_name="NoiseFit Crew_550F"}
```

Because `bind_check_result` is SUCCESS, the phone has to send id 18 to finish the bind. An earlier
OpenFit build treated the clear `device_verify` flag as a refusal, aborted, fell through to Berry
(silent) and reported "no reply to the account commands". That gate is removed;
`SePairing.allowsBindResult` encodes the vendor rule instead.

The id-18 frame must include the two zero-valued proto2 fields:
`08 12 1A <len> 1A <len> 08 00 12 <len> <token> 18 00`. The watch ACKs the shorter frame (without
`08 00`/`18 00`) with `08 12 A0 06 00` (`error_code=0`), so the phone believed the bind succeeded,
but the watch's own pairing screen reported "pairing failed" and a reconnect still said
`Watch bound state · no`. Matching the vendor encoder byte-for-byte is the current fix; on-device
confirmation is pending.

**On-device status.** The transport has been confirmed repeatedly on the ColorFit Ultra Buzz
(announce → `01 01` ready, chunks → `01 00` received, inbound announce → chunk → `0100` ack).

- Build `cd68343` sent id 16 as `10 10` (the `sa_wear` field numbers); the watch read that as
  `change_mtu=16` and stayed silent.
- Build `975af4a` sent `08 10` and the watch finally replied to the first command:
  `08 00 10 00 A0 06 01` = `SEWear{id=0, change_mtu=0, error_code=NOT_SUPPORT}`. Note that this
  is the **id-0 greeting's** reply, not a bind reply: the phone had sent
  `SEWear{id=0, ble_connect_parmeter_config{}}` immediately before id 16. `change_mtu=0` and
  `NOT_SUPPORT` mean this firmware does not implement MTU change over protobuf, even though the
  GATT MTU exchange itself succeeds (247).
- Id 16 still produced no reply after the greeting error, so the next build stopped sending the
  greeting, sent the id-16 probe first (as `ZhConnectHandler` does), and escalated:
  bare id 16 → id 16 with `request_binding_status:true` → the populated id-0 config
  (`9A 06 03 08 F7 01`, MTU 247) followed by id 16 again → id 17 `bindCheck{deviceVerify:true}`.
- Build `fef5963` (2026-11-18 09:45) answered the question: the watch replies to **both** account
  commands with a bare error, `08 10 10 00 A0 06 01` (`SEWear{id=16, error_code=NOT_SUPPORT}`) and
  `08 11 10 00 A0 06 01` (`id=17, NOT_SUPPORT`). One run therefore proves three things: the SE
  transport, the BLE `SEWear` envelope and the `SEFunctionId` numbering are all correct, and this
  firmware simply does not implement the account bind on the SE channel.

**Conclusion for this watch.** A device that parses the SE envelope correctly but refuses the
account function group is not an Apricot-bind device. NoiseFit's other pairing stack — `Berry` —
does not use GATT for the account at all: `BluetoothService.connect(...)` routes
`DEVICE_PROTOCOL_BERRY` to `BerryBluetoothService`, i.e. a bonded classic RFCOMM socket on the
standard SPP UUID `00001101-0000-1000-8000-00805F9B34FB`, and `ZhConnectHandler.onConnectState`
then calls `setUserIdByBerryProtocol(token, model, version)` instead of `requestDeviceBindState`.
That path is the private Berry command exchange (`private cmd 2 → 3 → 4`), which OpenFit already
encodes in `BerryBinding`.

OpenFit therefore treats a refused/ignored SE account exchange as "this is a Berry model":
it asks Android to bond the watch, opens the SPP socket, and runs the private 2/3/4 bind. The
build also sends the read-only id-32 `GET_DEVICE_INFO` probe alongside id 17, so a
`SEWear{id=32, …}` reply would show that the SE command set works and only the bind is missing,
while `NOT_SUPPORT` there confirms the protocol conclusion.

The remaining question is whether any of those account probes is answered, and with what.

#### Watch Beat command set over SE (2026-11-27)

Once the bind succeeds the Watch Beat uses the same `16186f00` transport for ordinary commands.
OpenFit keeps the transport open after the bind (`seTransportOpen`) instead of tearing it down at
the end of the pairing flow, and only watches with **no** ZhBracelet write characteristic
(`seCommandProfile`) take this route, so the ColorFit Ultra Buzz keeps its existing key protocol.

Every frame is `SEWear{id, <payload>, error_code}`; field numbers were read from the generated
protobuf classes and the exact bytes are pinned in `SeCommandsTest`:

| id | command | payload |
|---|---|---|
| 32 | `GET_DEVICE_INFO` | bare request; reply `device{device_info{firmware, serial, battery{capacity}}}` |
| 33 | `GET_DEVICE_BATTERY_STATUS` | bare request; reply `device{battery{capacity, charge_status}}` |
| 48 | `SET_SYSTEM_TIME` | `system_time{time_set{timestamp uint32, offset sint32 quarter-hours, time_format bool}}` |
| 68 | `SET_USER_PROFILE` | `user_settings{user_profile{height, weight float, age, sex}}` |
| 161 | `FIND_WEAR` | bare request, no reply |
| 214 / 215 | `GET/SET_HEART_RATE_MONITOR` | `setting_menu{heart_rate_monitor{mode AUTO=0/OFF=1, frequency 5 min, continuous ALL_DAY=0/INTELLIGENT=1, warnings}}` |
| 217 / 218 | `GET/SET_SEDENTARY_REMINDER` | `setting_menu{sedentary_reminder{on, start, end, frequency seconds, no_disturb}}` |
| 235 / 236 | `GET/SET_POWER_SAVING` | `setting_menu{power_saving{on}}` |
| 730 / 731 | `GET/SET_REAL_TIME_HR` | `setting_menu{real_time_settings{switch, frequency seconds, auto_shutdown seconds}}` |
| 733 | `REPORT_REAL_TIME_HR` | watch → phone `setting_menu{real_time_data{timestamp, bpm}}` |

Vendor details that matter:

- The user profile writes `age`, not `birth`, and the weight is a **float** field.
- Sedentary `frequency` is stored in **seconds** (`interval * 60`), and the vendor always sets
  `no_disturb_in_launch=true`.
- The HR frequency the vendor sends is **5 minutes**, matching the UI's five-minute cadence.
- The SE protobufs are **proto2**: the vendor's generated `writeTo` emits every field of the
  message it sets, so default values such as `mode=0` and `continuous=0` appear on the wire.

The Watch Beat has no one-shot measurement frame. OpenFit runs a bounded real-time HR burst
(id 731 on, 5 s interval, 30 s auto-shutdown) after connecting, records the first id-733 sample for
Home, and turns the stream off again.

#### Watch faces over SE (read-only discovery, 2026-11-27)

The face commands ride the same `SEWear` envelope in field 7 (`watch_face`). OpenFit currently
sends only the two read-only ids; the set/remove/install ids are recorded here but are not wired.
Exact request bytes and reply parsers are pinned in `SeCommandsTest`.

| id | `SEFunctionId` | OpenFit status |
|---|---|---|
| 80 | `GET_WATCH_FACE_LIST` | wired, read-only |
| 81 | `SET_WATCH_FACE` | not wired (phase 1) |
| 82 | `REMOVE_WATCH_FACE` | not wired (phase 1) |
| 83 | `PREPARE_INSTALL_WATCH_FACE` | not wired (custom upload) |
| 84 | `REPORT_INSTALL_RESULT` | not wired (custom upload) |
| 85 | `REQUEST_DEVICE_WATCH_FACE_CONFIG` | wired, read-only |
| 86 | `SET_DEVICE_WATCH_FACE_CONFIG` | not wired (custom upload) |

Request shapes (from `com.zhapp.ble.a` in NoiseFit 5.9):

- `getWatchFaceList()` calls `a.g() == a(80, null)`, i.e. a bare `SEWear{id=80}` → `08 50`.
- `getDiyWatchFaceConfig(id)` calls `a.a(id, 85)`, which builds
  `SEWear{id=85, watch_face{watch_face_config{id}}}`. With `id="abc"`:
  `08 55 3A 07 3A 05 0A 03 61 62 63`.
- `getDeviceWatchFace(id, fileSize, isReplace)` calls `a.a(id, fileSize, isReplace)` (id 83) and
  builds `SEWatchFacePrepareInfo{id, target_file_size, transfer_file_size, is_replaceable}`.

Reply shapes:

```
SEWear{ id:80, watch_face{ item_list{ 1: SEWatchFaceItem{ id, is_current, is_remove } ... } } }
SEWear{ id:85, watch_face{ watch_face_config{ id, rule_count, ... } } }
```

`SEWatchFace` is a oneof: case 1 is `SEWatchFaceItem.SEList` (field 1 repeated), case 7 is
`SEWatchFaceConfig`. `SEWatchFaceConfig` fields 3-8 carry the complex-function / background /
thumbnail / point / number file configs. OpenFit reads only `id` and `rule_count` for now; the
face file configs are left untouched until a real device reply pins their semantics.

**Still unknown:** the custom-face *payload* (image resolution, format/compression, chunking,
CRC, max size, slot rules) and the large-data transfer frames. A captured NoiseFit custom-face
upload (btsnoop/HCI plus vendor logs and the exact source image) is required before ids 83/84/86
and the generic `startUploadBigData("watch", ...)` path can be implemented safely.

### Berry SPP frame format (alternate transport)

From `com.zhapp.ble.bean.berry.CmdDataBean#getCmdBytes` (multi-byte fields are
**little-endian** because `BleUtils.int2Bytes(value, size, false)` uses `false` = LSB-first):

```
offset  size  field
0       4     head           magic, see table
4       1     serialNo       rolling 0..255 counter (BerryBluetoothService#replaceIndex)
5       1     event          1=protobuf, 2=big data, 3=private, 4=log
6       1     type           3 for protobuf commands, 2 for private commands
7       2     length         payload length, LE uint16
9       N     payload        protobuf SEWear  OR  private{cmdId:2, len:2, data}
```

Head magics (`BleCommonAttributes`, written LE so the bytes on the wire are reversed):

| Constant | int | wire bytes | direction |
|---|---|---|---|
| `BERRY_APP_REQ_DEV_HEAD_VALUE` | `0x5A486666` | `66 66 48 5A` | phone → watch |
| `BERRY_DEV_REQ_APP_HEAD_VALUE` | `0x5A488888` | `88 88 48 5A` | watch → phone |
| `BERRY_ASK_HEAD_VALUE` | `0x5A489999` | `99 99 48 5A` | ack/ask |
| `BERRY_DEV_LARGE_FILES_HEAD_VALUE` | `0x5A481111` | `11 11 48 5A` | large files |

Cross-check: `BERRY_APP_REQ_DEV_HEAD_VALUE = 1514694246 = 0x5A486666` and
`BERRY_APP_REQ_DEV_HEAD = {102, 102, 72, 90}` = `66 66 48 5A`; `BERRY_DEV_REQ_APP_HEAD_VALUE =
1514702984 = 0x5A488888` and `BERRY_DEV_REQ_APP_HEAD = {-120, -120, 72, 90}` = `88 88 48 5A`.
Every outbound builder (`CmdDataBean.getCmdDataBean(BERRY_APP_REQ_DEV_HEAD_VALUE, …)`) therefore
puts `66 66 48 5A` on the wire. **OpenFit had these two heads swapped until 2026-11-18**, which
would make any Berry watch ignore every command without any error — see the device notes below.

Ack frame (`CmdAskBean`, answering a request): `head(4) 0x5A489999`, the **same serialNo**,
`error` byte, two zero placeholders. It is a bare 8-byte control frame — unlike command frames
it has **no event/type/length header**:

```
99 99 48 5A  <serialNo>  <error>  00 00
```

The exchange is stop-and-wait in both directions. `BerryBluetoothService#processingData` (and
the BLE twin `BerryRingService`) call `replyAsk(cmdDataBean.getAskCmd(0))` **before** parsing each
inbound frame, so the phone acks every command frame it receives, reusing that frame's serial.
`error` is the index of the sub-request being acknowledged (0 for a single command). OpenFit now
acks every inbound Berry command frame — omitting it makes the watch stop after the first reply.

Large-file chunks use a second wrapper (`BluetoothService#a(byte[])`):
`[1][len+4 hi][len+4 lo][payload…][CRC32 of the preceding bytes]`.

### SEWear schema (`com.zh.ble.sa_wear.protobuf.WearProtos`)

```
message SEWear {
  Type type = 1; int32 id = 2; ChangeMtu change_mtu = 3; SEBindAccount bind_account = 4;
  SEDevice device = 5; SESystemTime system_time = 6; SEUserSettings user_settings = 7;
  SESporting sporting = 8; SEFitness fitness = 9; SEGnss gnss = 10;
  SEMicroFunction micro_function = 11; SENotification notification = 12; SEMusic music = 13;
  SESettingMenu setting_menu = 14; SEFactory factory = 15; SEAleax aleax = 16;
  SENewWatchFace new_watchface = 17; SENewWeather new_weather = 18; SEChildren children = 19;
  SEAIFunction ai_function = 20; SETvRemote tv_remote = 21;
  int32 error_code = 100; BLEConnectParameterConfig ble_connect_parmeter_config = 99;
}
Type: OTHER=0 ACCOUNT=1 DEVICE=2 SYSTEM_TIME=3 USER_SETTINGS=4 SPORTING=5 FITNESS=6 GNSS=7
      MICRO_FUNCTION=8 NOTIFICATION=9 MUSIC=10 SETTING_MENU=11 FACTORY=12 ALEAX=13
      NEW_WATCH_FACE=14 NEW_WEATHER=15 CHILDREN=16 AIFUNCTION=17 TVREMOTE=18
```

### Berry SPP bind sequence (alternate transport)

NoiseFit normally performs Android system bonding first (`BerryBluetoothService#checkBondByMac` →
`createBond` → `BOND_STATE_CHANGED` receiver → connect) and then talks Berry over **classic
SPP** (`00001101-…`). OpenFit deliberately connects over **BLE GATT** first and keeps the system
bond as a fallback for GATT 5/15. The account bind itself is the same on both transports.

On top of bonding the Berry protocol uses private commands (`event=3`, `type=2`, payload
`cmdId:2 len:2 data`), in this order:

| Step | Frame | Meaning |
|---|---|---|
| 0 | private `cmdId=1`, empty | protocol description request (`internalGetProtocol`) |
| 1 | watch → private `cmdId=1`, `ProtocolBean` | device type, protocol version, platform |
| 2 | private `cmdId=2`, `UserIdBean` | account identity (`setUserIdByBerryProtocol`) |
| 3 | watch → private `cmdId=2`, `BerryDeviceInfoBean` | `isBind`, `isSameUser` |
| 4 | private `cmdId=3`, empty | only when `!isBind` (`bindDevice`) |
| 5 | watch → private `cmdId=3`, one status byte | see below |
| 6 | private `cmdId=4`, `[1]` | only when status is `0` (`bindDeviceSuc(true)`) |
| 7 | watch → private `cmdId=4`, one byte | `0` = bind confirmed |

If step 3 reports `isBind && isSameUser`, the watch is already paired with this account and
steps 4–7 are skipped. If it reports `isBind && !isSameUser`, the watch belongs to another
account and pairing stops.

`UserIdBean` (`com.zhapp.ble.bean.berry.UserIdBean#getCmdBytes`):

```
[len(userId):1][userId UTF-8][0xBB][len(phoneName):1][phoneName][len(version):1][version]
```

The length bytes come from `String.length()` (UTF-16 units), not the UTF-8 byte count; fields
are truncated to 100 / 255 / 50 characters. Phone name is `Build.MODEL` with all whitespace
removed, version is `Build.VERSION.RELEASE`.

Token rule (`ZhConnectHandler`, when the device has no stored token):

```
token = (UUID.randomUUID() + random(10..9999) + userId).substring(30)
```

The token is persisted per device so later connections can prove the same account.

Bind status byte from the watch (private `cmdId=3`):

| Value | Meaning |
|---|---|
| 0 | accepted — send `cmdId=4 [1]` |
| 1 | invalid token |
| 2 | bind refused |
| 3 | already bound (treated as success) |
| 4 | device was already reset |
| 5 | watch timed out waiting for confirmation |
| 6 | watch could not parse the request |

`BerryDeviceInfoBean` (`cmdId=2`) layout:

```
0..1    device type, LE
2..4    firmware version, printed as v[4].[3].[2]
5..10   device MAC, LE
11..16  Bluetooth MAC, LE
17      serial-number length
18..    serial number bytes, printed in reverse
+       flags: bit7 clear => same user, bit6 set => bound
```

No encryption, no challenge/response and no key material were observed on the Berry path:
the private/protobuf payloads are sent in clear inside the frame above. This is why the Berry
profile is implementable without copying any proprietary secret.

### Berry over BLE (`aa01`) — the second Berry bearer

The Berry account exchange is not tied to SPP. `BluetoothService.connect` routes
`DEVICE_PROTOCOL_BERRY_TEST` and `DEVICE_PROTOCOL_BERRY_RING` to `BerryRingService`, which is a
**GATT** client, and `BluetoothService.writeBerryCmd` sends the *same* `CmdDataBean` frames through
it. Only the bearer changes:

| Protocol | Service | Phone writes | Phone reads replies | Notes |
|---|---|---|---|---|
| `Berry-Ring` (`BPC1.241120.001`) | `00006666-…` | `0000005f-…` | `0000005e-…` | reads `00000050-…` once after discovery; `DEF_MTU = 517` |
| `Berry-Test` (`BPB1.240915.001`) | `0000aa01-…` | `0000aa02-…` | `0000aa05-…` (data), `0000aa03-…` (log) | no handshake; `setUserIdByBerryProtocol` writes private cmd 2 here |
| `Berry` (`BPA1.240915.001`) | — | SPP `00001101-…` | SPP | bonded classic socket |

`setUserIdByBerryProtocol` (in `ControlBleTools`) builds the account frame with
`a.C0001a.a(token, model, version)` — private `cmdId=2` carrying `UserIdBean`, head
`BERRY_APP_REQ_DEV_HEAD_VALUE`, `event=3`, `type=2`. The rest of the 2/3/4 exchange is identical
because it is parsed by the shared `BerryParsing`, which handles both the private commands and
`BindAccountProtos.SEWear` account messages.

The ColorFit Ultra Buzz tested here exposes **`aa01` with `aa02` (read/write) and `aa03`
(read/write/notify)** — the Berry-Test shape minus `aa05` — so OpenFit treats `aa02` as the write
channel and `aa03` as the reply channel. It also exposes `00000001/00000002/00000003`, the base
service used by the alternate (Ryeex) family, and `fee7`, which belongs to the bundled CRP stack;
neither is used by the zhapp Berry/Apricot code.

### Device status, 2026-11-18 (build `bd6110e`)

The watch is now **bonded** (Android reported `10 -> 11 -> 12`), so pairing at the OS level works.
The classic socket still failed on every attempt:

```
bond state bonded
opening the classic SPP socket
IOException · read failed, socket might closed or timeout, read ret: -1
```

That message is what Android reports when the remote end has no RFCOMM/SPP listener, and the bond
was obtained over LE from a BLE scan. Two conclusions:

1. The bond is not the problem; the transport is. A watch that answers no SDP/SPP request is
   effectively LE-only for us, and no amount of bonding makes an RFCOMM socket appear. The build
   now logs `BluetoothDevice.getType()` and the cached SDP UUID list at the start of the Berry
   phase so the next report settles this instead of guessing.
2. The Berry implementation could not have worked even over a healthy socket: the two head
   magics were swapped (see the frame-format table) and the phone never acknowledged inbound
   frames (see the ack note). Both are fixed.

The read-only id-32 `SEWear{id=32}` probe is no longer sent alongside id 17 — the two transmits
overlapped, the watch answered `control resend 1`, and the probe muddied the log. The SE
`SEWear{id=16|17, error_code=NOT_SUPPORT}` result is established twice and does not need repeating.

### Heart rate and keepalive

| Purpose | Command |
|---|---|
| Real-time HR stream on/off + frequency + auto-off | SEWear `SETTING_MENU(11)`/`SERealTimeHeartRateSettings` (`a(int,int,RealTimeHeartRateConfigBean)`) |
| Factory HR switch (measure now start/stop) | SEWear `FACTORY(12)`/`SEFactory.heartRateSwitch = HEART_RATE_START(1)/STOP(2)` |
| HR monitor mode (off/auto, continuous vs intelligent) | SEWear `SETTING_MENU(11)`/`SEHeartRateMonitor` |
| Link keepalive | after `SETTING_MENU` id `141` the app calls `startHeartBeat()` |

Incoming HR values surface through `RealTimeHeartRateCallback`; the parsers live in
`com.zhapp.ble.parsing.*` and key off the SEWear `type`/`id` of the response.

## ZhBracelet / NavPlus profile (the stack Ultra Buzz actually uses)

This is the missing link for the ColorFit Ultra Buzz. NoiseFit's `NavPlusConnectHandler`
(`com.noisefit_nav_plus.handler.connect`) does not use SE or Berry: it binds through the bundled
`com.zjw.zhbraceletsdk` (`ZhBraceletService`). `NavPlusQueryDeviceUnitsHandler` and
`DataUnitConverter` special-case exactly `COLORFIT_ULTRA_BUZZ`, `COLORFIT_VISION_BUZZ` and
`COLORFIT_ULTRA_2`, which confirms the assignment.

GATT roles (from `com.zjw.zhbraceletsdk.service.d`, all present on this watch):

| Role | UUID |
| --- | --- |
| base / system service | `00000001-0000-1000-8000-00805f9b34fb` |
| request characteristic | `00000002-…` (read/write/write-no-response) |
| reply characteristic | `00000003-…` (read/write/notify) |
| protobuf service (used after bind) | `16186f00-0000-1000-8000-00807f9b34fb` |
| big-data service (not on this watch) | `00002001-…` |

On `onServicesDiscovered` the SDK enables notifications on **every** characteristic of both
services, requests MTU 247, and only then calls `ConnectorListener.onConnect`. The handler then
calls `ZhBraceletService.bindDevice(type)`:

* `type = 0` — initiate a bind (first pairing);
* `type = 1` — ask whether an app binding already exists (reconnect).

`bindDevice` resolves to `c.c(int)` → `a.b(int)` → `a(byte[]{type}, 1, 108)`, a plaintext frame on
`00000002`:

```
AB 00 00 06 00 00 00 00 01 00 6C 00 01 TT
```

Header layout (`a.a(byte[], byte direction, int key)`):

```
AB            magic
00            reserved
LL LL         frame length - 8 (big endian)
00 00 00 00   session fields, zero in every phone request
01            direction: phone -> watch (03 = watch -> phone)
00            reserved
KK            command key (6C = 108 = bind)
00 PP         payload length
payload
```

The watch answers with key `0x34` (52), payload `TT RR`:

* `TT` repeats the request type;
* `RR = 1` → bound / accepted;
* `RR = 0` → for type 0 the watch refused (still bound to another phone); for type 1 it is not
  bound.

`com.zjw.zhbraceletsdk.service.c.q(byte[])` maps that to `BindDeviceListener.bindSuccess()`,
`deniedByDevice()` and `bindError()`. There is **no auth token and no encryption** in this bind:
the account token used by Berry/SE never appears. `ZhBraceletService.beBind()` only parses an
advertising service-data blob (`09 16 68 FE …`), not a GATT frame.

Order of commands on connect (from `h.onServicesDiscovered`): when notification setup completes the
SDK calls `Q()` (time sync, key 1) and 50 ms later `setUserInfo`; the bind is requested separately
from `ConnectorListener.onConnect`. A bind therefore does not depend on time sync or profile data.
OpenFit defers both follow-ups until the bind succeeds, so they never run on an unbound watch.

### Post-bind commands

`c.Q()` → `a.B()` sends the clock, key 1, payload length 4:

```
AB 00 00 09 00 00 00 00 01 00 01 00 04 p0 p1 p2 p3
```

with `yyyyMMddHHmmss` split as `yy MM dd HH mm ss`:

```
p0 = yy << 2 | MM >> 2
p1 = dd << 1 | (MM & 3) << 6 | HH >> 4
p2 = (HH & 15) << 4 | mm >> 2
p3 = (mm & 3) << 6 | ss
```

`c.setUserInfo()` → `a.a(UserInfo)` sends the profile, key 4, payload length 4:

```
AB 00 00 09 00 00 00 00 01 00 04 00 04 q0 q1 q2 00
```

```
q0 = (!getSex() ? 1 : 0) << 7 | age     (bit 7 set for female, low bits age)
q1 = height in cm
q2 = weight in kg
```

`h.setUserInfo` clamps height and weight to 1..255 before building the frame. Replies use three
shapes: key `0x0F` ("send status", payload byte 13 = 1 for success), key `0xAC` ("device answer",
byte 13 echoes the original key, byte 14 is the status), and key `0x42` (command acknowledgement,
payload `[key] 00 [result]`). OpenFit recognises all three and logs them as
`ZhBracelet ack · status · ok`, `device answer for key N · ok` and `command ack · time sync · ok`.

The `0x42` form is what the Buzz actually sends after the bind: at 17:45:39 it answered the clock
sync with `AB 00 00 08 97 00 00 4E 03 00 42 00 03 01 00 01` and the user info with
`… 42 00 03 04 00 01`, i.e. both accepted (`result=1`). The app now reports these as
"Clock sync acknowledged by the watch" / "User info acknowledged by the watch".

### Heart rate over ZhBracelet (recovered 2026-11-18)

SE cannot deliver health data on this firmware, so the ZhBracelet script channel is the only
route. The vendor SDK exposes three relevant entry points:

| SDK call | Frame | Meaning |
|---|---|---|
| `ZhBraceletService.openMeasurement()` → `c.J()` → `a.u()` | `AB 00 00 06 00 00 00 00 01 00 19 00 01 01` | start a one-shot measurement |
| `closeMeasurement()` → `c.e()` → `a.b()` | same, last byte `00` | stop it |
| `setContinuousHr(true/false)` → `c.g(z)` → `a.j(z)` | key `0x29`, last byte `01`/`00` | toggle the watch's own continuous mode |

The reply is key `0x0B` (11). `com.zjw.zhbraceletsdk.service.c.b(byte[])` maps key 11 to
"measurement returns heart rate" and calls `c.h(byte[])`, which reads the BPM from the first payload
byte:

```
AB 00 00 LL .. .. .. .. 03 00 0B 00 01 HH     HH = bpm, 0 = no reading yet
```

`service.a.a(byte[], int, int, int)` returns `new HeartInfo(bArr[13] & 255, …)`. The SBP/DBP fields
in that bean are computed from `UserCalibration`, **not measured** — the same key-11 frame carries
only the heart rate. OpenFit therefore ignores the pressure fields and treats `0x0B` as BPM only.

**The Buzz refuses the one-shot command.** On 2026-11-19 it answered `0x19` with
`AB 00 00 08 00 00 00 4E 03 00 42 00 03 19 00 00` — command ack, key `0x19`, result `0`. The SDK's
own ack handler (`service.c.K`, the `0x42` case) never mentions key 25; it does handle key 41
(`0x29`) as {@code onHeartRateWitch}, so continuous HR is the setting this firmware implements.
OpenFit now treats a refused measurement as a signal to send `0x29 = 01` instead, waits up to 45 s
for a `0x0B` reading and then turns the setting off. If the `0x29` ack is also 0, the log says so and
the wait is cancelled. After the run it became clear that `0x29` is the all-day setting (the watch
keeps sampling into its history) rather than a live stream; see the `0x7F`/SE correction below.

The SDK also has a protobuf path on the SE channel for watch-driven reporting:
`WearProtos.WearPacket.Type.FITNESS` with `Fitness.fitness_id`:

| FitnessID | Value | Notes |
|---|---|---|
| `GET_HEART_RATE_MONITOR` | 10 | read the monitor config |
| `SET_HEART_RATE_MONITOR` | 11 | `HeartRateMonitor{mode, frequency, warning, warning_value}`; mode `AUTO = 0`, `OFF = 1` |
| `REGISTER_BASIC_DATA_REPORT` | 45 | subscribe to `REPORT_BASIC_DATA` (47) |

`Fitness.basic_data` (field 39) is a `BasicData` message whose field 4 is `heart_rate`.

**Where those protobuf packets actually go (corrected 2026-11-19).** `service.c.a(byte[])` adds the
WearPacket bytes to the big-data queue and writes them to `d.j` = **`16186f02`**, the SE channel,
not to `00000002`. `service.a.b(byte[], byte, int)` is a *different* envelope used by key `0x7F`:

```
AB 00 00 LL 00 00 00 00 01 00 7F hi lo KK [payload ...]
```

`0x7F` is a **private feature namespace**, not protobuf: `KK` in byte 13 selects a feature
(`2` REM sleep switch, `18/19/20/21` reminders, `33` auxiliary sport support, `34` battery saver,
`38/39` meal reminder). `service.c.b(byte[])` dispatches those in its `b2 == 127` branch and never
parses them as WearPackets. The log therefore labels key `0x7F` as `private feature`.

So the protobuf heart-rate monitor (`SET_HEART_RATE_MONITOR`, FitnessID 11) is an **SE** command on
this firmware, and the Buzz's SE account group already answers `NOT_SUPPORT`. It is not a fallback
for live HR over `00000002`.

The vendor does use this SDK, but only one HR command: cross-dex tracing of the full NoiseFit APK
(`classes5` + `classes9`) shows `ZhBraceletService.setContinuousHr(boolean)` called from
`com.noisefit_nav_plus.handler.NavPlusUpdateDeviceUnitsHandler.D0`, and
`setHeartRateMonitor` likewise from the NavPlus service wrapper. `openMeasurement()` has **zero**
call sites in the whole app. So the Zh binding is the real path on this watch, and continuous HR
(key `0x29`) is the only live measurement the vendor asks for — there is no hidden on-demand
command to copy.

OpenFit's implemented path is one-shot key `0x19`, falling back to plain `0x29` when the watch
refuses it, with the request delayed 4 s so it does not race the post-bind burst. Both acks are
honoured; the key `0x42` command-ack handler never mentions key 25, which is why the Buzz's refusal
is expected.

OpenFit sends the measurement start **4 s** after the user-info frame, once the post-bind burst and
the user-info ack have had time to settle (the earlier 50 ms request raced the burst and was
refused). It logs `ZhBracelet heart rate · N bpm`, stores the reading and immediately stops
whichever heart-rate mode is open. If no reply arrives within 25 s (measurement) or 45 s
(continuous) it logs `No heart-rate reply from the watch · stopping …` and sends the stop anyway, so
the watch's optical sensor is never left running.

### Auto HR is a user setting, not a probe (2026-11-27)

The one-shot `0x19` path above is only a diagnostic. The user-visible **Auto HR** switch must
follow the vendor exactly, and the vendor's `NavPlusUpdateDeviceUnitsHandler.D0(HeartRateInterval)`
calls only `ZhBraceletService.setContinuousHr(status)` → `service.h.setContinuousHr` →
`service.c.g(boolean)` → `service.a.j(boolean)`, i.e. key `0x29` with one payload byte. The second
internal call, `service.b.g(boolean)`, only writes the local pref `woint_measurement_heart`; it
sends no frame. `D0` ignores the interval field, so no frequency command exists for Auto HR.

OpenFit previously treated `0x29` as a short probe: it enabled it after a refused measurement,
waited 45 s and then sent `0x29 = 00`. That meant a user-enabled Auto HR was silently switched
back off about 45 s after every connect, and the choice was not persisted. The fix is:

* `KEY_AUTO_HR` (`auto_hr_enabled`) stores the user's choice; `onCreate()` restores it.
* With Auto HR on, the post-bind step skips `0x19` and re-asserts `0x29 = 01` (vendor behaviour).
* `stopZhHeartRate()` never sends `0x29 = 00` while the setting is on, whether the stop came from
  a live `0x0B` reading or the probe watchdog.
* Turning the switch off writes `0x29 = 00` and persists the choice.

The enable frame is byte-identical to the vendor SDK's
`AB 00 00 06 00 00 00 00 01 00 29 00 01 01`, so the remaining evidence is whether the watch
actually starts recording; the pairing log now records `Auto HR on · re-asserting after connect`
and `Auto HR kept on · ...` to make that visible.

### Daily heart-rate history, key `0x0D` (recovered 2026-11-19)

The 292-byte `0x0D` frame the watch pushes after every bind (and which arrives split across ~20
notifications at MTU 23) is **today's sport/heart-rate history**. `service.c.b(byte[])` maps key 13
to `运动心率` and calls `c.p(byte[])` → `service.a.e(byte[])`, which builds a `WoHeartInfo`:

```
byte 13   20YY << 1 | month >> 3        (year = (b13 & 0x7E) >> 1)
byte 14   (month & 7) << 5 | day        (month = ((b13 & 1) << 3) | ((b14 & 0xE0) >> 5))
bytes 17..304   288 unsigned samples, one per five-minute slot of the day
```

* `frame[17 + i]` for `i = 0..287`; `0` means "no reading in that slot".
* The **last nonzero sample** is the most recent reading; the vendor exposes it as the current
  heart rate for the day.
* `i < 96` is the sleep window (00:00–08:00) used to derive sleep min/max/avg. OpenFit ignores the
  derived sleep fields for now but keeps them in mind for the sleep screen.
* The vendor rejects the entire array if any nonzero sample is below 45 bpm, treating it as corrupt
  (`service.e.a(List)` checks the range). OpenFit does the same.
* An otherwise valid frame with no nonzero samples is allowed; it just means no history yet.

Decoded example from 2026-11-19 08:36 (`AB 00 01 29 46 00 00 00 03 00 0D 01 24 35 73 …`):
date `2026-11-19`, samples `5F` (95 bpm) and `52` (82 bpm), latest `82`.

OpenFit logs `ZhBracelet HR history · YYYY-MM-DD · latest N bpm · X sample(s) · day min–max avg M`
and shows the latest value if it is nonzero. This gives a real heart rate on the first connection
even when neither `0x19` nor `0x29` produces a live `0x0B` frame.

**Writes must be serialised.** `c` keeps an outbound `List<byte[]>` and writes one frame at a time;
Android also refuses a second `writeCharacteristic` while one is pending. Right after a bind the
watch pushes a burst of notifications and the 50 ms post-bind frames can be refused, which is what
happened on 2026-11-19 with the user-info and measurement frames. OpenFit therefore queues every
Zh frame, pumps the next one from `onCharacteristicWrite`, retries a refused frame after 250 ms
and abandons a missing callback after 3 s. On the saved-binding fast path it also skips the SE
probe so the two channels never compete for the link.

### Read-only sensor history (recovered 2026-11-27)

The post-bind burst also carries the watch's offline health history. OpenFit **only reads these
frames**; it never requests them, so a parser bug cannot change watch state. Every parser lives in
`ZhBraceletBinding` and requires the watch direction byte (`0x03`), because keys `0x02` and `0x03`
are also used by outgoing alarm and step-target writes.

| Key | Vendor handler | Layout | OpenFit parser |
| --- | --- | --- | --- |
| `0x02` | `c.i` → `a.a(byte[],float,float)` | packed date `b13-14`, slot count `b16 & 31`, big-endian 16-bit step counts from `b17` | `parseSteps` |
| `0x03` | `c.o` → `a.d(byte[])` | packed date `b13-14`, record count `b15`, 2-byte stage records from `b16` | `parseSleep` |
| `0x10` | `c.k` → `a.b(byte[])` | count `b15`, 7-byte records from `b16`: time `b0-3`, pulse, SYS, DIA | `parseOfflineBloodPressure` |
| `0x27` | `c.F` | count `b15`, 5-byte records from `b16`: time `b0-3`, SpO2 percent | `parseSpo2` |
| `0x28` | `c.E` → `c.s` | difference `b14`, count `b15`, 5-byte records from `b16`: time `b0-3`, raw wrist temp | `parseBodyTemperature` |
| `0x37` | `c.t` | packed date `b13-14`, slot count `b16 & 31`, one active-minute byte per slot from `b17` | `parseActivity` |
| `0x38` | `c.G` | count `b15`, 5-byte records from `b16`: time `b0-3`, stress index | `parseStress` |

Shared helpers:

* **Packed date** (`e.e(byte[])`): `year = 2000 + ((b13 & 0x7E) >> 1)`,
  `month = ((b13 & 1) << 3) | ((b14 & 0xE0) >> 5)`, `day = b14 & 0x1F`.
* **Sleep date** (`e.d(e.e(...))`): the vendor adds one day to the packed date, so the frame carries
  the night's start and the app labels the morning. OpenFit does the same.
* **Record time** (`c.r(byte[])`): `year = 2000 + (b0 >> 2)`,
  `month = ((b0 & 3) << 2) | (b1 >> 6)`, `day = (b1 >> 1) & 31`,
  `hour = ((b1 & 1) << 4) | (b2 >> 4)`, `minute = ((b2 & 15) << 2) | (b3 >> 6)`,
  `second = b3 & 63`.
* **Sleep record**: `hour = (b0 & 0xF8) >> 3`, `minute = ((b0 & 7) << 3) | ((b1 & 0xE0) >> 5)`,
  `type = b1 & 0x0F`. A stage runs from its record to the next record, so the last record is the
  end marker. Types: `1` awake, `2` light, `3` deep, `4` REM, `6` stay-up. The vendor's headline
  `SleepTotalTime` is **deep + light**; REM is a separate field, so OpenFit keeps `total` as
  deep + light and shows REM beside it.

Notes and limits:

* The vendor CRC-checks `0x02`, `0x03`, `0x0D` and `0x10` (`e.a(frame, G)`); OpenFit does not, but
  every parser bounds-checks the declared count against the frame length and rejects invalid dates.
* The body-temperature byte is kept **raw**. The vendor stores it as an unscaled integer string
  (`c.s` → `measureWristTemp`), and no published scale exists, so the Health card labels it `raw`
  instead of guessing °C.
* Blood-pressure records with any zero field are dropped, matching `a.b(byte[])`.
* Steps, activity, sleep and BP carry a date; SpO2, stress and temperature carry a full timestamp.
  The Health tab shows the latest record plus the date or time it came from.

### Settings commands over ZhBracelet (recovered 2026-11-27)

`com.zjw.zhbraceletsdk.service.a` builds every request and `service.c.b(byte[])` dispatches every
reply. The constants in `ZhBraceletBinding` mirror those builders byte for byte, and
`ZhBraceletBindingTest` pins each one. Requests use the same 13-byte header:

```
AB 00 00 LL 00 00 00 00 01 00 KK PP_HI PP_LO [payload]
```

#### Outgoing keys

| Key | Payload | SDK builder | Meaning |
|---|---|---|---|
| `0x01` | 4 packed bytes | `a.a(Calendar)` | clock sync |
| `0x02` | 5 bytes per alarm | `a.a(List<AlarmInfo>)` | replace the whole alarm list |
| `0x03` | 4 bytes big-endian | `a.d(int)` | daily step target |
| `0x04` | age/height/weight | `a.a(UserInfo)` | user profile |
| `0x07` | 4 packed bytes | `a.a(SitInfo)` | sedentary reminder |
| `0x08` | 1 byte | `a.h(boolean)` | unit system |
| `0x09` | 1 byte | `a.g(boolean)` | 12/24-hour clock |
| `0x0A` | 1 byte | `a.d(boolean)` | bright screen |
| `0x18` | none, byte 9 = `0x80` | `a.f()` | find watch |
| `0x1C` | none | `a.k()` | device-info request |
| `0x20` | 1 byte | `a.e(boolean)` | do not disturb |
| `0x29` | 1 byte | `a.j(boolean)` | continuous HR |

`0x18` is the only request that sets bit 7 of header byte 9; every other frame leaves it zero.

`0x0A` (bright screen) and `0x20` (do not disturb) are built by `ZhBraceletBinding` but are **not
shown in the Settings tab**. The Buzz acknowledges or silently ignores both frames and does not
change its behaviour, so the rows were removed; the builders and their golden-byte tests stay for
firmware that implements them. The SDK does read a bright-screen flag back, but stores it under an
unrelated preference key, which is why an ack alone is not evidence that the setting took effect.

#### Incoming keys used by the Settings tab

| Key | Shape | Meaning |
|---|---|---|
| `0x08` | battery `[13]`, version `(([7]&255)<<8)|([14]&255)`, tail `[15]` | device info |
| `0x42` | `[command key] 00 [result]` | set-state ack (`1` accepted, `0` refused) |
| `0x7F` | `[13]=selector, [14]=status, [15..]=value` | private feature |

The device-info version string is `e.a(versionNumber, tail)`:
`N` for `<= 20000`, `R` for `10000..19999`, `D` for `20000..29999`, formatted
`<prefix><body>.<tail/10+1>.<tail%10>` (Buzz: `N1.1.8`). Battery is the percentage in byte 13.

#### Private selectors (`0x7F`)

| Selector | Read | Write | Meaning |
|---|---|---|---|
| `0x02` | `[02 00]` | `[02 01 xx]` | quick-eye / raise-to-wake |
| `0x12` | `[12 00]` | — | sedentary reminder (key `0x07` writes it) |
| `0x15` | `[15 00]` | — | alarm list (key `0x02` writes it) |
| `0x22` | `[22 00]` | `[22 01 xx]` | battery saver |
| `0x27` | `[27 00]` | `[27 01 …]` | meal reminders |
| `0x2A` | `[2A 00]` | `[2A 01 xx]` | body-temperature unit |
| `0x69` | `[69 00]` | `[69 01 …]` | time zone |
| `0x6C` | `[6C 00]` | `[6C 01 …]` | daily goal |

Private replies carry the selector in byte 13 and a status in byte 14: `0` means "read result"
(the value follows in byte 15), `1` means accepted, `2` means refused. A toggle therefore resolves
from status `1`/`2`, while a read resolves from status `0` plus its parser.

#### Packed layouts

Sedentary (`0x07`), four bytes:

```
p0 = enable << 7 | period << 4 | startHour >> 1
p1 = (startHour & 1) << 7 | startMin << 1 | endHour >> 4
p2 = (endHour & 15) << 4 | endMin >> 2
p3 = (endMin & 3) << 6
```

`period` is the vendor interval code (1..4), stored verbatim. The read reply repeats the same four
bytes after the selector/status pair.

Alarm record (`0x02`), five bytes:

```
p0 = yy << 2 | month >> 2
p1 = (month & 3) << 6 | day << 1 | hour >> 4
p2 = (hour & 15) << 4 | minute >> 2
p3 = (minute & 3) << 6 | id << 3
p4 = repeat byte
```

`p4 = 128` is a one-off alarm; any other value is the weekday mask. The date is the next
occurrence: today if the time is still ahead, otherwise tomorrow. The vendor decrements `yy` for a
repeating alarm, and OpenFit mirrors that so the watch schedules identically. The whole list is
replaced in one frame, so OpenFit preserves each alarm's `p4` when it adds or removes one.

#### What the Settings tab does

Every control is optimistic and serialised one command at a time. The app sets the switch, queues
the frame and arms a 5 s watchdog; a `0x42` ack (direct keys) or a `0x7F` status (private features)
resolves it, and a refusal or silence restores the previous value. Device info is requested on
every connect; the stored sedentary reminder and alarm list are read only when the user taps
Refresh, so the post-bind burst is never raced. Find-watch has no ack and simply vibrates.

### Post-bind push burst (2026-11-18 17:45)

Once the bind is accepted the watch pushes a burst on `00000003` with no request from the phone:
key `0x31` (payload `00 F7 00 3F 01 FC`; the same frame also arrives on connect before the bind),
`0x02` (52 bytes, mostly zero), `0x37` (28 bytes), `0x0D` (292 bytes, spread over several
notifications), `0x27` and `0x38` (3 bytes each), `0x08` (22 bytes), `0x09` (6 bytes containing
the raw watch MAC `D4 27 1D 30 44 9B`), then the key-4 echo and the two key-0x42 acks. The `0x0D`
frame is decoded as today's heart-rate history and `0x02`/`0x03`/`0x10`/`0x27`/`0x28`/`0x37`/`0x38`
as read-only sensor history (see above). The remaining frames are plaintext status/settings blobs
that OpenFit logs and ignores. The `0x09` MAC reply is confirmed and could be used to validate the
saved binding.

### Validated on hardware (2026-11-18 11:49, build `b701e4a`)

```
11:49:04  notifications: enabled 00000003
11:49:05  Connection ready · profile=Noise · notifications enabled
11:49:22  TX zh-bind type 0 · AB0000060000000001006C000100
11:49:25  RX 00000003 · AB0000079700004E03003400020001
11:49:25  ZhBracelet bind info · type=0 result=1
11:49:25  PAIRED  ZhBracelet bind accepted (type 0)
```

The watch left pairing mode and showed its home screen. The reply's bytes 4..7 are not zero
(`97 00 00 4E`); they are not a standard CRC32 of the payload or of the frame, and the vendor
parser ignores them for key 52. Treat them as session data. Requests still send zeros there.

Reconnect was validated on build `91777d7` at 12:07–12:09: the saved binding makes OpenFit send the
vendor's type-1 inquiry, and the watch answered `type=1 result=1` on two consecutive connections:

```
12:09:02  saved binding found · checking it (type 1)
12:09:02  TX zh-bind type 1 · AB0000060000000001006C000101
12:09:02  RX 00000003 · AB0000074400004E03003400020101
12:09:02  ZhBracelet bind info · type=1 result=1
12:09:02  PAIRED  ZhBracelet bind accepted (type 1)
```

Only the session bytes and the echoed type differ from the first bind, so the binding survives an
app restart and a GATT reconnect.

The RC4/"Ryeex" cipher documented below belongs to a different bundled SDK (`com.ryeex…`) that
shares the same base UUIDs but is not the stack used for Ultra Buzz. The zhbraceletsdk frames are
plaintext.

### Watch faces over the ZhBracelet protobuf channel

The Buzz's watch-face traffic is **not** a `0xAB` script frame. NoiseFit's
`NavPlusUpdateDeviceUnitsHandler` calls `ZhBraceletService.getDeviceProtoWatchStatusEvent(...)`
(`→ service.h → service.c.a(String,int) → n.m.a`), which builds a
`com.zh.wear.protobuf.WearProtos.WearPacket` and queues it through the **same chunked big-data
transport as the SE command set**: service `16186f00`, outbound `16186f02`, inbound `16186f01`
(announce `00 00 00 00 <count LE16>`, ready `00 00 01 01`, chunk `<index LE16><data>`, done
`00 00 01 03`). OpenFit's `SeGatt` already implements that framing, so the Buzz face read reuses it
with a different payload (`BuzzFace`).

`WearPacket` (proto2/nanopb): `1 type` (enum; `WATCH_FACE = 4`), `2 id` (uint32
`WatchFace.WatchFaceID`), `6 watch_face` (`WatchFace`).

`WatchFace` oneof: `1 watch_face_list` (`WatchFaceItem.List`), `2 id`, `3 path`, `4 success`,
`5 prepare_status`, `6 prepare_info`, `7 install_result`, `8 info_list` (`WatchFaceInfo.List`),
`11 support_data_list`, `12 edit_request`, `13 edit_response`, `14 bg_image_result`.

`WatchFaceItem`: `1 id` (required string), `2 name` (required string), `3 is_current` (required
bool), `4 can_remove`, `5 version_code`, `6 can_edit`, `7 background_color`, `8 background_image`,
`9 style`, `10 data_list`. `WatchFaceItem.List { repeated WatchFaceItem list = 1 }`.
`WatchFaceInfo { 1 id }`; `WatchFaceInfo.List { repeated WatchFaceInfo list = 1 }`.

`WatchFaceID`: `GET_INSTALLED_LIST=0`, `SET_WATCH_FACE=1`, `REMOVE_WATCH_FACE=2`,
`REMOVE_WATCH_FACE_PHOTO=3`, `PREPARE_INSTALL_WATCH_FACE=4`, `REPORT_INSTALL_RESULT=5`,
`REMOVE_MULTI_WATCH_FACE=6`, `GET_SUPPORT_DATA=10`, `EDIT_WATCH_FACE=11`, `BG_IMAGE_RESULT=12`.

OpenFit sends the read-only request `WearPacket{type=WATCH_FACE, id=GET_INSTALLED_LIST}`
(`08 04 10 00`) only when the user opens Watch Faces. The bundled SDK's own inbound handler parses
the reply from `watch_face_list` (field 1); OpenFit also accepts `info_list` (field 8) in case the
firmware reports ids only.

**Status (2026-11-27):** the message layout is decoded, but the Buzz firmware's answer to
`GET_INSTALLED_LIST` has not been observed on hardware — the vendor app only ever installs a face
(`PREPARE_INSTALL_WATCH_FACE`). Treat the read as a probe until a device confirms it.

## Ryeex profile (different SDK, same base UUIDs, not used by Ultra Buzz)

Base service `00000001-0000-1000-8000-00805f9b34fb`, write `00000002-…`, read `00000003-…`.
Payloads are encrypted with `BleCipher`, which is **RC4 with a fixed initial permutation**,
implemented in `libble-crypto.so` and fully decoded from the disassembly:

```
nativeEncrypt(in, data, out)          // out.length must equal data.length, in.length >= 1
    S[256] = fixed permutation P (see below)      // not 0..255
    j = 0
    for i in 0..255:                              // key schedule, key = in bytes cyclically
        j = (j + S[i] + in[i mod in.length]) & 0xFF
        swap(S[i], S[j])
    i = j = 0
    for n in 0..data.length-1:                    // stream, same j/i carried over
        i = (i + 1) & 0xFF
        j = (j + S[i]) & 0xFF
        swap(S[i], S[j])
        out[n] = data[n] ^ S[(S[i] + S[j]) & 0xFF]
```

`P` is the 256-byte table at `.rodata:0x760`, built from 16 blocks whose base byte is the
block's high nibble:

```
base[16] = e0 70 20 f0 60 c0 d0 a0 90 b0 80 00 30 50 10 40
P[16*i + k] = base[i] + k
```

Helper functions (all pure byte shuffles, verified instruction by instruction):

```
mixA(mac[6], pid[2]) -> 8 bytes: out = a0 a2 a5 b0 b0 a4 a5 a1
mixB(mac[6], pid[2]) -> 8 bytes: out = a0 a2 a5 b1 a4 a0 a5 b0
   where a = mac bytes (0..5), b = pid little-endian (pid low, pid high)
regA() = 0xde85ca90   regB() = 0xfa54ab92
logA() = 0xcd43bc00   logB() = 0x93bfac09   logC() = 0x369a58c9
```

`BleCipher.encrypt(key, data)` = `nativeEncrypt` with `key` as RC4 key material and `data` as
the buffer that is XORed — i.e. `ciphertext[i] = plaintext[i] ^ RC4(key)[i]`. Frame layout,
command opcodes and the Ryeex handshake were **not** recovered in this pass: the Java side
that consumes `mixA`/`encrypt` is R8-obfuscated (`R20000O000000o.*` in `classes.dex`) and was
not yet decompiled.

## Open questions

1. ~~Which profile the ColorFit Ultra Buzz exposes~~ — **answered 2026-11-18:** it exposes SE
   (`16186f00`), Berry-over-BLE (`aa01`) and ZhBracelet (`00000001`). The vendor binds this model
   through ZhBracelet; SE answers `NOT_SUPPORT` and Berry is silent.
2. ~~Berry MTU/segmentation~~ — answered: the reference GATT stack (`BerryRingService`) requests
   MTU 517 and splits every outbound frame into `MTU - 3` chunks (20 when negotiation fails).
   OpenFit mirrors that with a 247-byte request and no-response write pacing.
3. Exact `SEFitness`/`SESettingMenu` field numbers for HR responses (only command-side
   builders were read).
4. Ryeex application handshake, opcodes and protobuf envelopes.
5. ~~Whether `bindDevice` (private cmd 3) needs prior `UserIdBean`~~ — answered: yes. The order is
   protocol (cmd 1) → user id (cmd 2) → bind (cmd 3, only if not bound) → confirm (cmd 4). This is
   the classic-SPP alternate path, kept for a firmware that exposes SPP.
6. ~~Whether `16186f00` speaks Berry frames or classic SPP~~ — answered on hardware: it answers a
   Berry frame with the SE "busy" control frame. The service uses SE chunked protobuf; the Berry
   SPP path remains documented for the alternate transport.
7. ~~Whether the watch answers id 17 `bindCheck`~~ — answered: it answers `NOT_SUPPORT`, on a
   bonded and an unbonded link, so the SE account group is not implemented on this firmware. What
   that does **not** answer is whether the rest of the SE command set works; OpenFit now probes ids
   32 (device info) and 33 (battery) before falling back to ZhBracelet, and the answer picks the
   route for heart rate.
8. ~~Whether account commands need an Android system bond~~ — answered for SE: no. The bond was
   obtained and the SE account commands still answered `NOT_SUPPORT`, while the classic socket
   stayed unusable, which points at an LE-only link rather than a permission problem.
9. ~~Which Berry bearer this watch actually uses~~ — answered: neither. `aa01` is present but
   silent, and the watch is LE-only (`device type 2`, `SDP records none`), so classic SPP cannot
   work. The Berry code remains as an alternate and is tried after ZhBracelet.
10. ~~Whether a reconnect makes the type-1 inquiry answer `result=1`~~ — **answered 2026-11-18:**
    yes, twice in a row (`AB0000074400004E03003400020101`). The binding persists. Time sync
    (key 1) and user info (key 4) are now implemented and sent 50 ms apart after the bind; their
    on-device acknowledgement and the device-info command (key 8) are the next protocol steps.
