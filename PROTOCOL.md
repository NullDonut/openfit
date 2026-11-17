# OpenFit protocol notes (from NoiseFit 5.5.0 static analysis)

Status: **static evidence only** — nothing here has been validated against a physical watch.
Everything below was read out of the supplied package; no code was sent to a device.

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

The app contains **three** watch stacks. Which one the ColorFit Ultra Buzz uses is still
unknown; OpenFit should select a profile by discovered GATT service, not by name.

| Stack | Package | Transport | Cipher |
|---|---|---|---|
| Berry | `com.zhapp.ble`, `com.zjw.zhbraceletsdk` | GATT `16186f00-…` + optional SPP `00001101-…` | none observed |
| Ryeex | `com.ryeex.ble`, `com.ryeex.common.ble.stack` | GATT `00000001-…` | RC4 variant in `libble-crypto.so` |
| Realtek BBPro | `com.realsil.sdk.bbpro` | GATT (Realtek) | BBPro authentication (link keys) |

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

### Frame format

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
| `BERRY_APP_REQ_DEV_HEAD_VALUE` | `0x5A488888` | `88 88 48 5A` | phone → watch |
| `BERRY_DEV_REQ_APP_HEAD_VALUE` | `0x5A486666` | `66 66 48 5A` | watch → phone |
| `BERRY_ASK_HEAD_VALUE` | `0x5A489999` | `99 99 48 5A` | ack/ask |
| `BERRY_DEV_LARGE_FILES_HEAD_VALUE` | `0x5A481111` | `11 11 48 5A` | large files |

Ack frame (`CmdAskBean`, answering a request): head `0x5A489999`, same serialNo,
`error` byte, two zero placeholders — i.e. 9-byte frame with empty payload.

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

### Bind (pairing) sequence, as implemented by NoiseFit

NoiseFit performs Android system bonding first (`BerryBluetoothService#checkBondByMac` →
`createBond` → `BOND_STATE_CHANGED` receiver → connect). OpenFit now connects first and keeps
that bond flow as a fallback for GATT 5/15, so the sequence below is only reached once a watch
actually accepts the link. On top of bonding the
Berry protocol uses private commands (`event=3`, `type=2`, payload `cmdId:2 len:2 data`):

| Step | Frame | Meaning |
|---|---|---|
| 1 | private `cmdId=3`, empty | `bindDevice()` — request bind |
| 2 | private `cmdId=2`, `UserIdBean` | user id/account data (`a(String,String,String)`) |
| 3 | private `cmdId=4`, `[0/1]` | `bindDeviceSuc(boolean)` — confirm |
| alt | SEWear `ACCOUNT(1)` id `0` | `requestDeviceBindState()` |
| alt | SEWear `FACTORY(12)` id `6` | `sendBlueToothName` (example of type/id pairing) |

No encryption, no challenge/response and no key material were observed on the Berry path:
the private/protobuf payloads are sent in clear inside the frame above. This is why the Berry
profile is implementable without copying any proprietary secret.

### Heart rate and keepalive

| Purpose | Command |
|---|---|
| Real-time HR stream on/off + frequency + auto-off | SEWear `SETTING_MENU(11)`/`SERealTimeHeartRateSettings` (`a(int,int,RealTimeHeartRateConfigBean)`) |
| Factory HR switch (measure now start/stop) | SEWear `FACTORY(12)`/`SEFactory.heartRateSwitch = HEART_RATE_START(1)/STOP(2)` |
| HR monitor mode (off/auto, continuous vs intelligent) | SEWear `SETTING_MENU(11)`/`SEHeartRateMonitor` |
| Link keepalive | after `SETTING_MENU` id `141` the app calls `startHeartBeat()` |

Incoming HR values surface through `RealTimeHeartRateCallback`; the parsers live in
`com.zhapp.ble.parsing.*` and key off the SEWear `type`/`id` of the response.

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

1. Which profile the ColorFit Ultra Buzz exposes — needs one `adb`-captured GATT dump or the
   app's own diagnostics screen against the real watch.
2. Berry MTU/segmentation: whether a frame larger than the negotiated MTU is split by the
   stack or must be chunked by the caller (the large-file path uses `CHAR_PROTOBUF_UUID_03`).
3. Exact `SEFitness`/`SESettingMenu` field numbers for HR responses (only command-side
   builders were read).
4. Ryeex application handshake, opcodes and protobuf envelopes.
5. Whether `bindDevice` (private cmd 3) needs prior `UserIdBean`, or the watch initiates bind.
