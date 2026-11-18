# OpenFit protocol notes (from NoiseFit 5.5.0 static analysis)

Status: **SE transport confirmed on hardware; account bind under test.** The GATT layout,
notification setup, MTU negotiation and the announce/ready/chunk transport have been validated
against a physical ColorFit Ultra Buzz. The watch does not speak the Berry SPP frames on
`16186f00`; it uses the vendor's generic SE ("Apricot") chunked protobuf transport. The
`SEFunctionId` account bind (16 → 17 → 18, or 19 when already bound) is implemented in OpenFit.
The first on-device run exposed an envelope-schema mix-up (Berry `sa_wear` field numbers on the
SE message); that is fixed, and the watch's reply to the account bind is still pending a new
physical test.

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
| `16186f00-…-00807f9b34fb` (Berry) | `6f01`–`6f05`, all read/write-no-response + notify |
| `00000001-…-00805f9b34fb` (Ryeex base) | `00000002` (read/write, write-no-response), `00000003` (read/write/notify) |

Conclusions:

- Berry is the active stack: its five characteristics match §GATT roles exactly, and OpenFit
  enabled notifications on all five without encryption or a challenge.
- The Ryeex base service is also exposed, so service presence alone does not identify the stack;
  Berry is preferred, as implemented.
- Android bonding is not required for the Berry GATT link (the watch was not bonded during the
  whole successful connection), which supports OpenFit's connect-first ordering.
- `0000aa01` is a service the Berry SDK also knows (`0000aa01-0000aa05`). `0000fee7` does not
  appear in the Berry or Ryeex sources; the only bundled match is the CRP stack
  (`com.crrepa.ble`, `fee7`/`fee9`), which is used by other Noise models. It is not touched by
  OpenFit and the watch's SE service works without it.
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


## Berry profile (primary candidate)

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
`08 12 1A <len> 1A <len> 12 <len> <user id>`; id 19 `08 13 1A <len> 32 <len> <token>`.

NoiseFit on connect: id 16; if not bound → id 17 with `device_verify:true`; on
`bind_check_result=SUCCESS` → id 18 with a random token; if already bound → id 19 with the token
from its local database. Its token is `(UUID + random(10..9999) + userId).substring(30)`; OpenFit
uses the same shape with a local placeholder user id and stores it per watch address.

The vendor requires `device_verify:true` in the id-17 reply before it sends id 18; OpenFit now
does the same.

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

Order of commands on connect (from `h.onServicesDiscovered`): bind first, then `Q()` (time sync,
key 1) and 50 ms later `setUserInfo`. A bind therefore does not depend on time sync or profile
data.

The RC4/"Ryeex" cipher documented below belongs to a different bundled SDK (`com.ryeex…`) that
shares the same base UUIDs but is not the stack used for Ultra Buzz. The zhbraceletsdk frames are
plaintext.

## Ryeex profile (alternate family)

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

1. ~~Which profile the ColorFit Ultra Buzz exposes~~ — answered by the hardware dump above:
   Berry (and the Ryeex base service) are present; Berry is selected.
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
   bonded and an unbonded link, so the SE account group is not implemented on this firmware.
8. ~~Whether account commands need an Android system bond~~ — answered for SE: no. The bond was
   obtained and the SE account commands still answered `NOT_SUPPORT`, while the classic socket
   stayed unusable, which points at an LE-only link rather than a permission problem.
9. Which Berry bearer this watch actually uses. `aa01` (aa02 write / aa03 notify) is present and is
   the Berry-Test shape minus aa05, so the build now writes the private cmd 2 there and accepts
   replies on aa03. The `device type` and `SDP records` lines decide whether the classic socket is
   even possible; a `DEVICE_TYPE_LE` watch with no SDP records rules SPP out permanently.
