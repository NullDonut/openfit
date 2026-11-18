# OpenFit handoff — Noise/ColorFit protocol reverse engineering

Last updated: 2026-11-18
Branch: `feature/ble-connection-handling`
Latest code commit at time of writing: this commit (ZhBracelet/NavPlus bind on `00000002`,
Berry-over-BLE fallback on `aa01`, corrected Berry frame magics, inbound acks, link-capability
diagnostics, classic SPP removed, 68 unit tests).

The 2026-11-18 09:45 device run answered both SE account probes with `NOT_SUPPORT`
(`08 10 10 00 A0 06 01` and `08 11 10 00 A0 06 01`), which settles the SE question: the transport,
envelope and id numbering are right and this firmware does not implement the account bind there.

The 10:10 run of `bd6110e` bonded the watch (`10 -> 11 -> 12`) but the classic SPP socket failed
twice with `IOException · read failed, socket might closed or timeout, read ret: -1`. That is the
signature of a remote with no RFCOMM listener; the later capability log confirmed `device type
LE only (2)` and `SDP records none`, and the SPP code has been deleted. While tracing that, two
genuine Berry bugs surfaced: the phone→watch and watch→phone head magics were swapped (every
command was silently ignored), and the phone never acknowledged inbound frames although the
exchange is stop-and-wait. Both are fixed.

The 11:03 run then showed a clean SE exchange with real watch answers, but still `NOT_SUPPORT`
for ids 16/0/17, and BLE Berry stayed silent. The vendor APK explains why: `NavPlusConnectHandler`
binds ColorFit Ultra Buzz through the bundled `com.zjw.zhbraceletsdk` on the `00000001` service
(write `00000002`, notify `00000003`) with a plaintext key-108 bind frame. OpenFit now tries that
ZhBracelet bind first, then `aa01` BLE Berry.
Latest docs commit: this file (see `git log`)

This file is the single-page handoff: what was learned, what exists, how the machine is set up,
what is still unknown, and what to do next. The deeper references are:

| File | Contents |
|---|---|
| `PROTOCOL.md` | Authoritative recovered wire protocol, provenance hashes, open questions |
| `info.md` | Investigation log, including the 2026-11-17 decompilation pass and tooling notes |
| `CONTEXT.md` | Project-level handoff (features, safety boundary, build instructions) |
| `README.md` | User-facing description and limitations |
| `app/src/main/java/com/openfit/app/protocol/` | Android-free protocol implementation |
| `app/src/test/java/com/openfit/app/protocol/` | 68 JUnit tests for the Berry frames, SE transport/envelope, ZhBracelet bind frames and reassembly |

---

## 1. Objective

Make OpenFit (`com.openfit.app`, Android/Java) connect to and exchange data with the user's
**Noise ColorFit Ultra Buzz** watch, using only clean-room knowledge recovered from the supplied
NoiseFit 5.5.0 package. No NoiseFit code or binaries are copied into OpenFit.

The user explicitly authorized decompiling the supplied APK and reusing the recovered protocol
for pairing/interoperability. CI builds only; nothing is built or installed on the phone by the
agent.

---

## 2. Machine constraints and safety rules (important)

Host: ARM64 Void Linux (glibc), **5.6 GB RAM**, 4 cores, `/tmp` is tmpfs, only ~1.9 GB free on
`/`, no root ( `doas` needs a password), no `adb` device attached, no local Android SDK/Gradle.

**Two desktop-session crashes happened during this work:**

1. A 4-thread full-APK JADX decompile (18 DEX, 179 MB APK) exhausted RAM and took down the
   desktop session.
2. `inspect_android_package` on the same full APK (REA JADX provider) caused a RAM-clear crash
   that closed background apps.

Rules that follow from that, and that must be kept:

- **Never** run JADX / REA inspection on the full 179 MB APK on this host.
- Decompile **one DEX at a time**, single-threaded, low priority:
  `nice -n 19 jadx -r -j 1 --single-class <fqcn> -d <outdir> classesN.dex`
- Prefer static I/O scanners (Python, no JVM) to locate classes/strings first, then decompile
  only the few classes needed.
- Do not build or install on the user's phone. Builds go through GitHub Actions only.
- Do not run further local heavy analysis without telling the user first and giving paths.
- Everything under `/tmp/opencode` is tmpfs and is **lost on reboot**. Persistent copies live in
  the repo, in `/home/shiv/.local/share/jadx-jdk/`, and `/home/shiv/jadx-headless-mcp-0.7.1-all.jar`.

---

## 3. Environment setup (persistent, done 2026-11-17)

### 3.1 Shell variables

Appended (idempotently) to both `~/.profile` and `~/.bashrc`:

```sh
export JAVA_HOME="/home/shiv/.local/share/jadx-jdk/jdk-21.0.13+9"
case ":$PATH:" in
    *":$JAVA_HOME/bin:"*) ;;
    *) export PATH="$JAVA_HOME/bin:$PATH" ;;
esac
export REA_JADX_MCP_JAR="/home/shiv/jadx-headless-mcp-0.7.1-all.jar"
```

`~/.bash_profile` sources `~/.bashrc`, so login and interactive shells both get them. Verified:

```sh
bash -lc 'echo $JAVA_HOME; command -v java; java -version'
```

### 3.2 Locations

| Item | Path | Persistent? |
|---|---|---|
| Temurin JDK 21.0.13+9 (full JDK) | `/home/shiv/.local/share/jadx-jdk/jdk-21.0.13+9` | yes (347 MB) |
| JADX MCP bridge JAR 0.7.1 | `/home/shiv/jadx-headless-mcp-0.7.1-all.jar` | yes (42 MB) |
| JADX CLI 1.5.1 | `/tmp/opencode/tools/jadx` | no (tmpfs) |
| JUnit 4.13.2 + hamcrest 1.3 | `/tmp/opencode/tools/lib/` | no (tmpfs) |
| Extracted DEX (18 files) | `/tmp/opencode/noisefit/dex/` | no (tmpfs) |
| Decompiled `classes9.dex` tree (`com.zhapp.ble`, `com.zjw.*`) | `/tmp/opencode/noisefit/out/d9/sources/` | no (tmpfs) |
| Extracted `SEWear` schema source | `/tmp/opencode/noisefit/out/sewear/.../WearProtos.java` | no (tmpfs) |
| XAPK supplied by user | `/home/shiv/com.noisefit_5.5.0.xapk` | yes |
| Base APK (extracted) | `/tmp/noisefit-main.apk` | no (tmpfs) |

### 3.3 OpenCode / REA MCP registration

Global config: `~/.config/opencode/opencode.json` (V2 shape). Current content:

```json
{
  "mcp": {
    "servers": {
      "rea": {
        "type": "local",
        "command": ["/usr/bin/node", "/usr/bin/rea", "mcp"],
        "environment": {
          "JAVA_HOME": "/home/shiv/.local/share/jadx-jdk/jdk-21.0.13+9",
          "REA_JADX_MCP_JAR": "/home/shiv/jadx-headless-mcp-0.7.1-all.jar"
        }
      }
    }
  }
}
```

Backup of the previous config: `~/.config/opencode/opencode.json.bak.20261117074430`.

Note: `opencode mcp list` printed "No MCP servers configured" when run from `/home/shiv`
(not a project), but prints `✓ rea  connected` when run from the repo. The config itself is
valid against `https://opencode.ai/config.json` (V2 uses `mcp.servers`, and the server object
accepts an `environment` map). The shell exports above are a second, independent path to the
same values.

Provider health was verified with the persistent paths:

```sh
JAVA_HOME=/home/shiv/.local/share/jadx-jdk/jdk-21.0.13+9 \
REA_JADX_MCP_JAR=/home/shiv/jadx-headless-mcp-0.7.1-all.jar \
rea doctor --provider jadx --json
# -> healthy: true, jadx: healthy, JAR validated
```

`rea doctor` also warns that a client registration may not record the two variables; the
`environment` block above fixes that for OpenCode.

### 3.4 After restarting OpenCode

The REA MCP server inherits the new environment only when it is (re)started. After restart:

1. `opencode mcp list` in the repo should show `✓ rea connected`.
2. Check that a `binary_session`-style JADX tool is available for the `jadx` provider.
3. Only ever open a **small** target (one DEX / one class), never the full APK.

---

## 4. Recovering the tooling after a reboot (tmpfs is gone)

No root is available, so everything installs under `$HOME` / `/tmp`:

```sh
mkdir -p /tmp/opencode/tools && cd /tmp/opencode/tools

# JDK (already persistent at ~/.local/share/jadx-jdk; re-download only if missing)
curl -LO https://github.com/adoptium/temurin21-binaries/releases/latest/download/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.13_9.tar.gz
tar xzf OpenJDK21U-jdk_aarch64_linux_hotspot_*.tar.gz -C /home/shiv/.local/share/jadx-jdk/

# JADX CLI
curl -LO https://github.com/skylot/jadx/releases/download/v1.5.1/jadx-1.5.1.zip
unzip -q jadx-1.5.1.zip -d jadx

# JUnit (for the pure-Java protocol tests)
mkdir -p lib && cd lib
curl -LO https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar
curl -LO https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
```

To get the DEX files again: the XAPK is a zip; the base APK is the `com.noisefit.apk` member;
DEX files are the `classes*.dex` entries inside that APK.

### 4.1 Decompile recipe (safe)

```sh
export JAVA_HOME=/home/shiv/.local/share/jadx-jdk/jdk-21.0.13+9
export PATH="$JAVA_HOME/bin:$PATH"

# one class from one DEX, single thread, low priority
nice -n 19 /tmp/opencode/tools/jadx/bin/jadx \
    -r -j 1 --single-class com.zhapp.ble.ControlBleTools \
    -d /tmp/opencode/noisefit/out/one \
    /tmp/opencode/noisefit/dex/classes9.dex
```

Useful Dart-free helpers built during this work (recreate if `/tmp` was wiped):

- `/tmp/opencode/noisefit/dexscan.py` — minimal DEX parser; lists call sites of a target class.
  `python3 dexscan.py BleCipher dex/classes*.dex`
- `/tmp/opencode/noisefit/stringsites.py` — finds methods whose bytecode references a string.
  `python3 stringsites.py "Auth bind" dex/classes5.dex`

### 4.2 Pure-Java test run (no Gradle needed)

```sh
CP=/tmp/opencode/tools/lib/junit-4.13.2.jar:/tmp/opencode/tools/lib/hamcrest-core-1.3.jar
rm -rf /tmp/opencode/build
"$JAVA_HOME/bin/javac" -Xlint:all -d /tmp/opencode/build -cp "$CP" \
    app/src/main/java/com/openfit/app/protocol/*.java \
    app/src/test/java/com/openfit/app/protocol/*.java
"$JAVA_HOME/bin/java" -cp "/tmp/opencode/build:$CP" \
    org.junit.runner.JUnitCore \
    com.openfit.app.protocol.BerryFrameTest \
    com.openfit.app.protocol.BerryBindingTest \
    com.openfit.app.protocol.BerryFrameReaderTest
# -> OK (28 tests)
```

CI (`.github/workflows/android.yml`) runs `gradle :app:testDebugUnitTest :app:assembleDebug`,
uploads the debug APK, and publishes release assets for `v*` tags. Run **44058236613** for
`bd80c98` completed with `success` (artifact `openfit-debug-bd80c98`).

---

## 5. Artifacts and provenance

| Item | Value |
|---|---|
| XAPK | `/home/shiv/com.noisefit_5.5.0.xapk`, 312,617,971 bytes |
| XAPK SHA-256 | `cfda8cf8ce2a3d4e79d808979db3fedb7825faa090f3cb67cd29d6401d33adc7` |
| Base APK member | `com.noisefit.apk`, 179,508,504 bytes |
| Base APK SHA-256 | `5ae827e908fbe8730fc122b0d825ca140520808a6a8f6f73312aa928e8b4eae2` |
| Code | 18 DEX files; R8-minified but class/method names largely intact inside `com.zhapp.*`, `com.zjw.*`, `com.ryeex.*` |
| Native | `libble-crypto.so`, 10,896 bytes, stripped, NDK r24 |
| Decompiler | JADX CLI 1.5.1 + Temurin JDK 21 |

The app ships **three independent watch stacks**. Which one the ColorFit Ultra Buzz exposes is
still unknown and must be decided by a GATT dump of the real watch, not by name.

| Stack | Package | Transport | Application-layer crypto |
|---|---|---|---|
| Berry | `com.zhapp.ble` | GATT `16186f00-…` (+ optional SPP `00001101-…`) | **none observed** |
| ZhBracelet / NavPlus | `com.zjw.zhbraceletsdk` | GATT `16186f00-…` + `00000001-…` | **none observed** (plaintext `0xAB`) |
| Ryeex | `com.ryeex.*` | GATT `00000001-…` | RC4 variant in `libble-crypto.so` (decoded) |
| Realtek BBPro | `com.realsil.sdk.bbpro` | Realtek GATT | BBPro link-key authentication |

---

## 6. Berry and SE protocols (both recovered; SE is what the GATT service speaks)

### 6.1 GATT

Service `16186f00-0000-1000-8000-00807f9b34fb` — note the vendor base `00807f9b34fb`, **not**
the SIG base `00805f9b34fb`, so these UUIDs cannot be shortened to 16-bit form.

| Characteristic | Role |
|---|---|
| `…6f01` | bulk / large-data responses |
| `…6f02` | **primary write channel** (all `writeCmd` traffic) |
| `…6f03` | large data + progress |
| `…6f04` | large-file data / "loses" reporting |
| `…6f05` | Alexa side channel |

All five get `setCharacteristicNotification(true)` plus a CCCD (`00002902-…`) write on service
discovery; the reference implementation waits for descriptor callbacks before treating the
connection as usable.

### 6.2 Frame format

From `com.zhapp.ble.bean.berry.CmdDataBean#getCmdBytes`; multi-byte fields are little-endian
(`BleUtils.int2Bytes(value, size, false)` = LSB first).

```
offset  size  field
0       4     head magic (LE)
4       1     serialNo  rolling 0..255
5       1     event     1=protobuf, 2=big data, 3=private, 4=log
6       1     type      3 for protobuf, 2 for private
7       2     length    payload length, LE uint16
9       N     payload   SEWear protobuf OR private{cmdId:2, len:2, data}
```

Head magics (`BleCommonAttributes`; bytes shown as they appear on the wire):

| Constant | Value | Wire bytes | Direction |
|---|---|---|---|
| `BERRY_APP_REQ_DEV_HEAD_VALUE` | `0x5A486666` | `66 66 48 5A` | phone → watch |
| `BERRY_DEV_REQ_APP_HEAD_VALUE` | `0x5A488888` | `88 88 48 5A` | watch → phone |
| `BERRY_ASK_HEAD_VALUE` | `0x5A489999` | `99 99 48 5A` | ack / ask |
| `BERRY_DEV_LARGE_FILES_HEAD_VALUE` | `0x5A481111` | `11 11 48 5A` | large files |

Ack frame (`CmdAskBean`): head `0x5A489999`, same serialNo, one `error` byte plus two zero
placeholders (8-byte frame, no length field, empty payload).

Large-file chunks use an extra wrapper (`BluetoothService#a(byte[])`):
`[1][len+4 hi][len+4 lo][payload…][CRC32 of preceding bytes]`.

### 6.3 SEWear envelope (`com.zh.ble.sa_wear.protobuf.WearProtos`)

```
message SEWear {
  Type type = 1;  int32 id = 2;
  ChangeMtu change_mtu = 3;      SEBindAccount bind_account = 4;
  SEDevice device = 5;           SESystemTime system_time = 6;
  SEUserSettings user_settings = 7; SESporting sporting = 8;
  SEFitness fitness = 9;         SEGnss gnss = 10;
  SEMicroFunction micro_function = 11; SENotification notification = 12;
  SEMusic music = 13;            SESettingMenu setting_menu = 14;
  SEFactory factory = 15;        SEAleax aleax = 16;
  SENewWatchFace new_watchface = 17; SENewWeather new_weather = 18;
  SEChildren children = 19;      SEAIFunction ai_function = 20;
  SETvRemote tv_remote = 21;
  int32 error_code = 100;        BLEConnectParameterConfig ble_connect_parmeter_config = 99;
}
```

`Type`: OTHER 0, ACCOUNT 1, DEVICE 2, SYSTEM_TIME 3, USER_SETTINGS 4, SPORTING 5, FITNESS 6,
GNSS 7, MICRO_FUNCTION 8, NOTIFICATION 9, MUSIC 10, SETTING_MENU 11, FACTORY 12, ALEAX 13,
NEW_WATCH_FACE 14, NEW_WEATHER 15, CHILDREN 16, AIFUNCTION 17, TVREMOTE 18.

### 6.4 Berry bind sequence (two bearers)

NoiseFit's Berry stack has **two bearers**, chosen by the device protocol the server returns:

| Protocol | Bearer | Service / socket | Phone writes | Phone reads |
|---|---|---|---|---|
| `Berry` | classic SPP after a system bond | `00001101-…` | socket | socket |
| `Berry-Test` | GATT (`BerryRingService`) | `aa01`, write `aa02` | `aa02` | `aa05` (data) / `aa03` (log) |
| `Berry-Ring` | GATT (`BerryRingService`) | `00006666`, write `0000005f` | `0000005f` | `0000005e` |

For `Berry`, `BerryBluetoothService#checkBondByMac` → `createBond` → `BOND_STATE_CHANGED` →
connect. For the GATT variants there is no bond step at all: discovery enables notifications and
`writeBerryCmd` pushes frames to the write characteristic. The account bind is byte-identical on
all three because both services end up in the shared `BerryParsing`. It uses private commands
(`event=3`, `type=2`, payload `cmdId:2 len:2 data`):

| Step | Frame | Meaning |
|---|---|---|
| 0 | private `cmdId=1`, empty | protocol description request (`internalGetProtocol`) |
| 1 | watch → private `cmdId=1`, `ProtocolBean` | device type, protocol version, platform |
| 2 | private `cmdId=2`, `UserIdBean` | account identity (`setUserIdByBerryProtocol`) |
| 3 | watch → private `cmdId=2`, `BerryDeviceInfoBean` | `isBind`, `isSameUser` |
| 4 | private `cmdId=3`, empty | only when `!isBind` (`bindDevice`) |
| 5 | watch → private `cmdId=3`, one status byte | `0` accepted, `1` bad token, `2` refused, `3` already bound, `4` already reset, `5` timeout, `6` parse failure |
| 6 | private `cmdId=4`, `[1]` | only when status is `0` (`bindDeviceSuc(true)`) |
| 7 | watch → private `cmdId=4`, one byte | `0` = bind confirmed |

If step 3 reports `isBind && isSameUser`, the watch is already paired with this account and
steps 4–7 are skipped; `isBind && !isSameUser` means it belongs to another account.

`UserIdBean` is `[len][token][0xBB][len][phoneName][len][version]`, with 100/255/50-character
truncation. The length bytes use `String.length()` (UTF-16 units), not UTF-8 byte counts. Phone
name is `Build.MODEL` with all whitespace removed; version is `Build.VERSION.RELEASE`. The token
is `(UUID.randomUUID() + random(10..9999) + userId).substring(30)` and is persisted per watch.

**No encryption, no challenge/response, no key material was observed on the Berry path.** The
frames above are sent in clear inside the 9-byte envelope. This invalidates the earlier
assumption that a proprietary handshake blocks pairing on this stack.

### 6.5 SE ("Apricot") transport and account bind (used on the GATT service)

A physical write attempt on `16186f00` settled the transport question: the watch interpreted the
Berry frame bytes as packet index `0x8888` and answered `00 00 01 02 00 00`, the SE stack's busy
control frame. The vendor app routes `16186f00` through `com.zhapp.ble.BluetoothService`
(`DEVICE_PROTOCOL_APRICOT`); the Berry SPP code above is only used over classic SPP.

Control frames are `00 00 <code:2 BE> <payload:2 LE>`:

| Code | Bytes | Meaning |
|---|---|---|
| `0x0100` | `00 00 01 00 00 00` | phone: reply received |
| `0x0101` | `00 00 01 01 00 00` | ready (also the phone's answer to an announce) |
| `0x0102` | `00 00 01 02 00 00` | busy |
| `0x0103` | `00 00 01 03 00 00` | transfer complete |
| `0x0105` | `00 00 01 05 <n LE16>` | resend chunk `n` |

Outbound: announce `00 00 00 00 <count LE16>` on `6f02` → wait for ready → `<index LE16>
<chunk>` × count on `6f02` → wait for done. Inbound replies: announce on `6f01` → phone answers
`00 00 01 01 00 00` on `6f01` → indexed chunks → phone answers `00 00 01 00 00 00` and parses the
concatenation as one `SEWear`. Chunk data is `MTU - 5` bytes; count is `ceil(length / (MTU - 5))`.

The vendor SDK's first command on connect is `SEWear{id=16}` (`ZhConnectHandler.onConnectState`
→ `requestDeviceBindState` → `a.L0(16)`). The id-0 `SEWear{ble_connect_parmeter_config{}}`
(`9A 06 00`) is app-level MTU setup, sent fire-and-forget. On this watch it is answered with
`08 00 10 00 A0 06 01` = `SEWear{id=0, change_mtu=0, error_code=NOT_SUPPORT}`, so OpenFit no
longer sends it.

**Two different `SEWear` schemas exist.** The BLE service uses
`com.zh.ble.wear.protobuf.SEWear`: `id=1`, `change_mtu=2`, `bind_account=3`, payloads 4..17,
`ble_connect_parmeter_config=99`, `error_code=100`, and **no type field**. The classic-SPP Berry
path uses `com.zh.ble.sa_wear.protobuf.SEWear`: `type=1`, `id=2`, `bind_account=4`.

The first device build sent the `sa_wear` byte sequence `10 10` for id 16. The watch read it as
`change_mtu=16`, acked with `01 00` and did nothing, which looked like a silent account refusal.
The corrected id-16 request is `08 10`; id 17 is `08 11 1A 04 12 02 08 01`; id 18 is
`08 12 1A <len> 1A <len> 12 <len> <user id>`; id 19 is `08 13 1A <len> 32 <len> <token>`.
The two codecs are kept apart in `BerrySewear` (Berry) and `SeWear` (`16186f00` / Apricot).

**Frame magics (corrected 2026-11-18).** `BERRY_APP_REQ_DEV_HEAD_VALUE = 0x5A486666` → the phone
sends `66 66 48 5A`; `BERRY_DEV_REQ_APP_HEAD_VALUE = 0x5A488888` → the watch sends `88 88 48 5A`.
OpenFit had these swapped, so every Berry command went out under the watch's own magic and was
ignored. The vendor also answers each inbound frame with a bare 8-byte
`99 99 48 5A <serial> 00 00 00` ack before parsing it (`replyAsk(getAskCmd(0))`); OpenFit now does
the same. Without the ack the watch stops after the first reply.

Account bind ids (`SEFunctionId`) and bodies (`BindAccountProtos`):

| Id | Phone message | Watch reply |
|---|---|---|
| 16 | `SEWear{id=16}` | `bind_account{request_binding_status}` |
| 17 | `bind_check{device_verify:true}` | `bind_check{bind_check_result, mac, serial_number, device_number, firmware_version, device_name}` |
| 18 | `bind_result{user_id:token}` | `bind_result{bind_result_type}` and/or `error_code` |
| 19 | `verify_user_number:token` | `verify_result{verify_result_type}` |
| 23 | `SEWear{id=23}` | unbind |

`SEBindAccount` fields: 1 request_binding_status, 2 bind_check, 3 bind_result,
4 inquiry_pairing_status, 5 request_pairing_result, 6 verify_user_number, 7 verify_result,
8 classic_bluetooth_status. `SEBindCheck`: 1 device_verify, 2 bind_random_key, 3 bind_check_result,
4 device_number, 5 mac, 6 serial_number, 7 firmware_version, 8 device_name. `SEBindResult`:
1 bind_result_type, 2 user_id, 3 phone_type. Result enum: 0 SUCCESS, 1 REFUSE, 2 OVER_TIME,
3 VERIFICATION_FAILED (only bind check). Phone type: ANDROID 0, IOS 1. `SEVerifyResult`:
`verify_result_type=1` is a **bool** (true = accepted) and `binding_status=2` is a bool.

Vendor order: id 16; if not bound → id 17 `device_verify:true`; on SUCCESS → id 18 with a random
token; if already bound → id 19 with the stored token. OpenFit follows this and persists the token
as `watch_token_<ADDRESS>`.

### 6.6 Heart rate and keepalive

| Purpose | Command |
|---|---|
| Real-time HR stream on/off, frequency, auto-off | SEWear `SETTING_MENU(11)` / `SERealTimeHeartRateSettings` |
| Factory HR switch (measure now start/stop) | SEWear `FACTORY(12)` / `SEFactory.heartRateSwitch = HEART_RATE_START(1)/STOP(2)` |
| HR monitor mode (off/auto, continuous vs intelligent) | SEWear `SETTING_MENU(11)` / `SEHeartRateMonitor` |
| Link keepalive | after `SETTING_MENU` id `141`, the app calls `startHeartBeat()` |

Incoming HR values surface through `RealTimeHeartRateCallback`; parsers live in
`com.zhapp.ble.parsing.*` and key off the SEWear `type`/`id` of the response.

---

### 6.7 ZhBracelet (NavPlus) bind — the stack ColorFit Ultra Buzz actually uses

`NavPlusConnectHandler` binds this model family through the bundled
`com.zjw.zhbraceletsdk`, not SE or Berry:

* base service `00000001-…`; write `00000002-…`, notify `00000003-…`;
* the SDK enables notifications on everything, requests MTU 247, then calls
  `ZhBraceletService.bindDevice(0)` (first bind) or `bindDevice(1)` (inquiry);
* `bindDevice` → `c.c(type)` → `a.b(type)` → `a(byte[]{type}, 1, 108)`, a plaintext frame:

```
AB 00 00 06 00 00 00 00 01 00 6C 00 01 TT
```

* `TT = 0` bind, `TT = 1` inquiry. The watch replies on `00000003` with key `0x34` (52) and
  payload `TT RR`; `RR = 1` accepted/bound, `RR = 0` refused (type 0) or unbound (type 1).
* No token, no encryption, no time sync before the bind.

OpenFit subscribes to `00000003` and, after the SE account group answers `NOT_SUPPORT`, sends
`TT = 0`, then `TT = 1` after 15 s, then falls back to Berry. See `PROTOCOL.md` for the exact
vendor call chain.

## 7. Ryeex family (alternate SDK, not used by Ultra Buzz)

Base service `00000001-0000-1000-8000-00805f9b34fb`, write `00000002-…`, read `00000003-…`.
`BleCipher` is RC4 with a **fixed initial permutation**, implemented in `libble-crypto.so`:

```
nativeEncrypt(in, data, out)          // out.length == data.length, in.length >= 1
    S[256] = fixed permutation P (not 0..255)
    j = 0
    for i in 0..255:                  // key schedule, key = in bytes cyclically
        j = (j + S[i] + in[i mod in.length]) & 0xFF
        swap(S[i], S[j])
    i = j = 0
    for n in 0..data.length-1:        // keystream, i/j carried over
        i = (i + 1) & 0xFF
        j = (j + S[i]) & 0xFF
        swap(S[i], S[j])
        out[n] = data[n] ^ S[(S[i] + S[j]) & 0xFF]
```

`P` is built from 16 blocks at `.rodata:0x760`:

```
base[16] = e0 70 20 f0 60 c0 d0 a0 90 b0 80 00 30 50 10 40
P[16*i + k] = (base[i] + k) & 0xFF
```

Pure byte shuffles, verified instruction by instruction:

```
mixA(mac[6], pid[2]) -> 8 bytes: out = a0 a2 a5 b0 b0 a4 a5 a1
mixB(mac[6], pid[2]) -> 8 bytes: out = a0 a2 a5 b1 a4 a0 a5 b0
   where a = mac bytes (0..5); b = pid little-endian (low, high)

regA = 0xde85ca90   regB = 0xfa54ab92
logA = 0xcd43bc00   logB = 0x93bfac09   logC = 0x369a58c9
```

`BleCipher.encrypt(key, data)` = `nativeEncrypt` with `key` as RC4 key material and `data` XORed
with the keystream. The Java consumer is R8-obfuscated (`R20000O000000o.*` in `classes.dex`);
Ryeex opcodes, handshake, envelope and framing were **not** recovered.

---

## 8. What is implemented in this repo

| File | Purpose |
|---|---|
| `app/src/main/java/com/openfit/app/protocol/BerryFrame.java` | Frame encode/decode, magics, private command builder, 8-byte ack builder, serial wrap |
| `app/src/main/java/com/openfit/app/protocol/ZhBraceletBinding.java` | ZhBracelet/NavPlus bind frame (key 108) and key-52 result parsing |
| `app/src/main/java/com/openfit/app/protocol/ZhFrameReader.java` | Reassembles `0xAB` frames from split `00000003` notifications |
| `app/src/main/java/com/openfit/app/protocol/BerryProfile.java` | GATT identifiers incl. `aa01` Berry-over-BLE and the `00000001` ZhBracelet channel |
| `app/src/main/java/com/openfit/app/protocol/BerryBinding.java` | Vendor bind handshake: token rule, `UserIdBean`, protocol/device-info parsing, cmd 2/3/4 frames, status labels |
| `app/src/main/java/com/openfit/app/protocol/BerryFrameReader.java` | Reassembles frames from split notifications, skips noise, consumes 8-byte acks |
| `app/src/main/java/com/openfit/app/protocol/BerrySewear.java` | Proto3 codec for the Berry SPP `sa_wear.SEWear` envelope (`type=1`, `id=2`) |
| `app/src/main/java/com/openfit/app/protocol/SeWear.java` | Proto3 codec for the BLE `wear.SEWear` envelope (`id=1`, `bind_account=3`, greeting, error code) |
| `app/src/main/java/com/openfit/app/protocol/SeGatt.java` | SE transport: announce, indexed chunks, control frames, MTU chunk size, incoming transfer reassembler |
| `app/src/main/java/com/openfit/app/protocol/SePairing.java` | SE account bind: id 16/17/18/19 builders, `SEBindCheck`/`SEBindResult`/`SEVerifyResult` parsers |
| `app/src/main/java/com/openfit/app/protocol/BerryProfile.java` | Berry and Ryeex GATT UUID constants and profile detection |
| `app/src/test/java/com/openfit/app/protocol/BerryFrameTest.java` | 12 tests: golden bytes, round-trip, truncation rejection, serial wrap, private/ack framing |
| `app/src/test/java/com/openfit/app/protocol/BerryBindingTest.java` | 11 tests: `UserIdBean` layout/truncation, token rule, protocol/device-info parsing, status labels |
| `app/src/test/java/com/openfit/app/protocol/BerryFrameReaderTest.java` | 6 tests: split notifications, ack consumption, leading noise, reset |
| `app/src/test/java/com/openfit/app/protocol/SeGattTest.java` | 9 tests: chunk math, announce/control golden bytes, packet prefix, out-of-order transfer reassembly |
| `app/src/test/java/com/openfit/app/protocol/SePairingTest.java` | 13 tests: bind encoder golden bytes, bind-check parsing, result enums, malformed replies |
| `app/src/test/java/com/openfit/app/protocol/SeWearTest.java` | 4 tests: SE envelope field numbers, empty submessage presence, error code |
| `app/src/test/java/com/openfit/app/protocol/ZhBraceletBindingTest.java` | 6 tests: golden bind frames, declared length, key-52 replies, split frames, resync |

The protocol package is deliberately **Android-free** (plain Java 11), so it compiles and tests
with `javac` + JUnit without Gradle or the Android SDK (68 tests as of this commit). `MainActivity`
wires the package into the live GATT link: MTU negotiation, the SE announce/chunk write queue,
inbound announce/ack handling, reply reassembly, the bind state machine, and per-watch token
persistence. The SE bind write path itself has **not yet been confirmed by a physical watch**.

Connection layer added on 2026-11-17 (successful on hardware; the watch stayed unbonded):

- **Connect-first**: no `createBond()` precondition. The app calls `connectGatt(..., TRANSPORT_LE)`
  immediately, treats `STATE_CONNECTED` with a non-success status as a failure, and retries up
  to 4 times with a 1 s gap (handles the common GATT 133 right after pairing).
- **On-demand bonding**: only GATT 5/15 (connection, discovery or descriptor write) triggers
  `createBond()`; 45 s pairing timeout; reconnect through `BOND_STATE_CHANGED` on success. The
  receiver is registered with `RECEIVER_EXPORTED` on Android 13+: the Bluetooth stack is a
  privileged app under its own UID, so a `RECEIVER_NOT_EXPORTED` receiver can miss bond events
  and leave the old pairing flow waiting forever. If all direct attempts fail on an unbonded
  watch, the app falls back to the NoiseFit-style bond-then-connect path once.
- **Service discovery** distinguishes Berry, SIG heart-rate, Ryeex and unknown profiles, then
  enables every available notification/indication sequentially (5 s setup timeout).
- **On-device log**: the status line on Devices opens diagnostics containing Android version,
  watch address/bond state, profile, a timestamped connection log, and the GATT service dump.
  A **Copy** button puts the whole report on the clipboard, so a failed phone test can be
  pasted into the next session without adb.
- **Rotation** no longer recreates `MainActivity` (`configChanges`), so an active GATT link is
  not torn down by an orientation change.
- **QR scanner:** ZXing 4.3.0's POM omits `androidx.core`/`androidx.fragment` even though its
  bytecode uses them (`ContextCompat`, `ActivityCompat`, `Fragment`, `FragmentActivity`,
  `ActivityResultContract`), so the first QR build crashed with `NoClassDefFoundError`. The
  dependency-fix commit `1bb2036` **never produced an APK**: its CI build failed at
  `:app:checkDebugDuplicateClasses` (`kotlin-stdlib-jdk8` 1.6.21 vs `kotlin-stdlib` 1.8.22 both
  define `kotlin.collections.jdk8.CollectionsJDK8Kt`). The Kotlin 1.6 JDK7/JDK8 shim artifacts
  are now excluded (their classes live in `kotlin-stdlib`), so a fixed APK can build.
  `CaptureActivity` now keeps the library's own `zxing_CaptureTheme` (only the forced orientation
  lock is overridden), and the app checks for a camera before launching. **QR still needs a
  physical retest on a build after `16d8bba`.**
- **On-device crash capture:** `OpenFitApp` installs an uncaught-exception handler that appends
  the stack trace to `last-crash.txt` in app storage, and caught QR-launch failures are recorded
  too. The diagnostics dialog shows a `LAST APP CRASH` section (with a Clear button) next to the
  connection log, so the next failure can be reported by Copy without adb.
- **Hardware evidence (2026-11-17):** the watch stayed `not bonded` while connected, exposed six
  services (Berry `16186f00-…`, Ryeex base `00000001-…`, `1800`, `1801`, `aa01`, `fee7`), and all
  five Berry characteristics accepted notification setup. Berry is therefore the active stack.
- **SE pairing on connect (new, awaits hardware test):** the first build's Berry-frame writes were
  answered with `00 00 01 02 00 00` (SE busy), proving `16186f00` uses the SE transport. The app
  now requests MTU 247 (falling back to 23), announces and chunks commands with `MTU - 5` data
  bytes per chunk, answers inbound announces, reassembles replies, and runs id 16 (binding
  status) → id 17 (`bind_check{device_verify:true}`) → id 18 (`bind_result{user_id}`), or id 19
  (verify the saved token) when the watch is already bound. There is no id-0 greeting, and if
  id 16 stays unanswered the app probes the explicit `request_binding_status:true` form, then
  the populated id-0 MTU config, then sends id 17 directly. The token is stored as
  `watch_token_<ADDRESS>`; a connection retry restarts pairing but keeps that token. Diagnostics
  gained a `PAIRING LOG` with TX/RX hex, chunk counts and the bind step/state.

Existing app behavior: local dashboard, BLE discovery by recognizable Noise/ColorFit names,
saved-watch reconnect, QR scanning for a Bluetooth MAC, SIG heart-rate measurement parsing,
manual sleep notes. No account, analytics, network permission or cloud sync.

---

## 9. Open questions / next steps

1. ~~**Which stack does the watch expose?**~~ **Answered 2026-11-18:** the physical ColorFit
   Ultra Buzz exposes `16186f00-…` (SE protobuf), `aa01` (Berry-over-BLE) and `00000001-…`
   (ZhBracelet/NavPlus). The vendor binds this model through `com.zjw.zhbraceletsdk` on
   `00000001`; SE answers `NOT_SUPPORT` for the account group. See `PROTOCOL.md`.
2. ~~**Berry MTU / segmentation**~~ **Answered:** the reference GATT stack requests MTU 517 and
   splits frames into `MTU - 3` chunks (20 fallback). OpenFit mirrors this with a 247 request,
   paced no-response writes, and a reassembly reader.
3. **Inner field numbers** of `SEFitness`, `SESettingMenu`, `SEFactory` HR messages — only the
   command-side builders were read. This is the next decompile target (small, one class at a
   time).
4. **Ryeex** application handshake, opcodes, protobuf envelopes.
5. ~~Whether private `cmdId=3` (bind) needs a prior `UserIdBean`~~ **Answered:** yes — protocol
   (cmd 1), user id (cmd 2), bind (cmd 3, only if unbound), confirm (cmd 4).
6. **Post-bind wiring:** `MainActivity` performs connect → discovery → profile detection →
   notification setup → MTU → SE probe (ids 0/16/17) → ZhBracelet bind. The pairing path is done.
   Next: send the ZhBracelet time sync and user info after a successful bind, and implement device
   info/health commands.
7. **Retest QR on the fixed APK:** the 2026-11-17 on-device run only exercised Bluetooth
   (connect + Berry notification setup, both successful). Tap "Scan a pairing QR code" on a build
   after `16d8bba`; if it still fails, open Devices → status line → **Copy**. The report now
   includes the last crash stack trace even though the scanner runs in its own activity.
8. ~~**Validate the ZhBracelet bind on hardware.**~~ **Validated 2026-11-18 11:49 (build
   `b701e4a`):**

   ```
   TX zh-bind type 0 · AB0000060000000001006C000100
   RX 00000003 · AB0000079700004E03003400020001
   ZhBracelet bind info · type=0 result=1
   PAIRED  ZhBracelet bind accepted (type 0)
   ```

   The watch left pairing mode and showed its home screen. The app now persists that result and
   follows the vendor's reconnect form: type 1 (inquiry) when a saved binding exists, type 0 for a
   first pairing, with one rebind if the inquiry reports unbound. Verify that a reconnect gets
   `type=1 result=1`. SE still answers `NOT_SUPPORT`, so its ~20 s probe can be demoted for this
   model later.

### Suggested order from here

```
pair on the watch (done, build b701e4a)
    -> reconnect and check type 1 answers result=1 (binding persisted)
    -> decompile the ZhBracelet time-sync (key 1) and user-info commands
    -> wire post-bind sync: time, user info, device info
    -> parse device data on 16186f00 for health screens
```

The QR result-to-connection gap and QR on-device retest remain separate, lower-priority items.
