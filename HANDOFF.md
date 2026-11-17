# OpenFit handoff — Noise/ColorFit protocol reverse engineering

Last updated: 2026-11-17
Branch: `feature/ble-connection-handling`
Latest code commit at time of writing: `abad148` (Berry protocol + frame/protobuf layer)
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
| `app/src/test/java/com/openfit/app/protocol/BerryFrameTest.java` | 10 unit tests for the frame/protobuf layer |

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
    app/src/test/java/com/openfit/app/protocol/BerryFrameTest.java
"$JAVA_HOME/bin/java" -cp "/tmp/opencode/build:$CP" \
    org.junit.runner.JUnitCore com.openfit.app.protocol.BerryFrameTest
# -> OK (10 tests)
```

CI (`.github/workflows/android.yml`) runs `gradle :app:testDebugUnitTest :app:assembleDebug`,
uploads the debug APK, and publishes release assets for `v*` tags. Run 44038772215 for
`abad148` completed with `success`.

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
| Berry | `com.zhapp.ble`, `com.zjw.zhbraceletsdk` | GATT `16186f00-…` (+ optional SPP `00001101-…`) | **none observed** |
| Ryeex | `com.ryeex.*` | GATT `00000001-…` | RC4 variant in `libble-crypto.so` (decoded) |
| Realtek BBPro | `com.realsil.sdk.bbpro` | Realtek GATT | BBPro link-key authentication |

---

## 6. Berry protocol (primary candidate, fully documented)

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
| `BERRY_APP_REQ_DEV_HEAD_VALUE` | `0x5A488888` | `88 88 48 5A` | phone → watch |
| `BERRY_DEV_REQ_APP_HEAD_VALUE` | `0x5A486666` | `66 66 48 5A` | watch → phone |
| `BERRY_ASK_HEAD_VALUE` | `0x5A489999` | `99 99 48 5A` | ack / ask |
| `BERRY_DEV_LARGE_FILES_HEAD_VALUE` | `0x5A481111` | `11 11 48 5A` | large files |

Ack frame (`CmdAskBean`): head `0x5A489999`, same serialNo, one `error` byte plus two zero
placeholders (9-byte frame, empty payload).

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

### 6.4 Bind (pairing) sequence

Android system bond happens first in NoiseFit (`checkBondByMac` → `createBond` →
`BOND_STATE_CHANGED` receiver → connect). OpenFit deliberately reversed that ordering on
2026-11-17: it connects first, retries transient GATT failures, and starts the same bond flow
only when the watch reports GATT 5/15 (see §8). On top of bonding, Berry uses private commands
(`event=3`, `type=2`, payload `cmdId:2 len:2 data`):

| Step | Frame | Meaning |
|---|---|---|
| 1 | private `cmdId=3`, empty | `bindDevice()` — request bind |
| 2 | private `cmdId=2`, `UserIdBean` | user id / account data |
| 3 | private `cmdId=4`, `[0/1]` | `bindDeviceSuc(boolean)` — confirm |
| alt | SEWear `ACCOUNT(1)`, id `0` | `requestDeviceBindState()` |
| alt | SEWear `FACTORY(12)`, id `6` | `sendBlueToothName` (example type/id pair) |

**No encryption, no challenge/response, no key material was observed on the Berry path.** The
frames above are sent in clear inside the 9-byte envelope. This invalidates the earlier
assumption that a proprietary handshake blocks pairing on this stack.

### 6.5 Heart rate and keepalive

| Purpose | Command |
|---|---|
| Real-time HR stream on/off, frequency, auto-off | SEWear `SETTING_MENU(11)` / `SERealTimeHeartRateSettings` |
| Factory HR switch (measure now start/stop) | SEWear `FACTORY(12)` / `SEFactory.heartRateSwitch = HEART_RATE_START(1)/STOP(2)` |
| HR monitor mode (off/auto, continuous vs intelligent) | SEWear `SETTING_MENU(11)` / `SEHeartRateMonitor` |
| Link keepalive | after `SETTING_MENU` id `141`, the app calls `startHeartBeat()` |

Incoming HR values surface through `RealTimeHeartRateCallback`; parsers live in
`com.zhapp.ble.parsing.*` and key off the SEWear `type`/`id` of the response.

---

## 7. Ryeex family (alternate, crypto decoded, framing unknown)

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
| `app/src/main/java/com/openfit/app/protocol/BerryFrame.java` | Frame encode/decode, magics, private/ack builders, serial wrap |
| `app/src/main/java/com/openfit/app/protocol/BerrySewear.java` | Minimal proto3 writer/reader for the SEWear envelope, type enum, private cmd ids |
| `app/src/main/java/com/openfit/app/protocol/BerryProfile.java` | Berry and Ryeex GATT UUID constants and profile detection |
| `app/src/test/java/com/openfit/app/protocol/BerryFrameTest.java` | 10 tests: golden bind bytes, round-trip, truncation rejection, serial wrap, nested payload |

The protocol package is deliberately **Android-free** (plain Java 11), so it compiles and tests
with `javac` + JUnit without Gradle or the Android SDK. `BerryProfile` is now used by
`MainActivity` for profile detection; `BerryFrame`/`BerrySewear` are **not yet used** to send
commands, and the protocol has **not been validated against hardware**.

Connection layer added on 2026-11-17 (in `MainActivity`, not yet device-tested):

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

Existing app behavior: local dashboard, BLE discovery by recognizable Noise/ColorFit names,
saved-watch reconnect, QR scanning for a Bluetooth MAC, SIG heart-rate measurement parsing,
manual sleep notes. No account, analytics, network permission or cloud sync.

---

## 9. Open questions / next steps

1. **Which stack does the watch expose?** One GATT service dump settles it. Look for
   `16186f00-…` (Berry), `00000001-…` (Ryeex), or Realtek BBPro services.
2. **Berry MTU / segmentation:** whether frames larger than the negotiated MTU are split by the
   stack or must be chunked by the caller (large-file path uses `…6f03`).
3. **Inner field numbers** of `SEFitness`, `SESettingMenu`, `SEFactory` HR messages — only the
   command-side builders were read. This is the next decompile target (small, one class at a
   time).
4. **Ryeex** application handshake, opcodes, protobuf envelopes.
5. Whether private `cmdId=3` (bind) needs a prior `UserIdBean`, or the watch initiates bind.
6. **Wiring (partly done):** `MainActivity` now performs connect → discovery → profile detection
   → notification setup. Still needed: send Berry bind (`private cmdId=3`, then optional
   `UserIdBean`/confirm) and the HR control message, then parse `…6f01`/`…6f02` notifications.
7. **First phone test of the new connection layer:** install the CI APK, tap Connect (or scan the
   QR), and if it does not reach "Connected", open the status line on Devices → **Copy** and
   share the report. It contains the exact GATT status, bond state and discovered profile.
8. Do not send unvalidated writes to the watch; confirm each step with a device test. The Berry
   bind frame is the next write and should be gated on the device test above.

### Suggested order from here

```
device test of connect-first flow  ->  read the copied diagnostics log
    ->  if connected with Berry profile: send bind (private cmdId=3) and re-test
    ->  extract SEFactory / SESettingMenu / SEFitness inner fields (single-class jadx)
    ->  wire HR control + parse incoming Berry frames
    ->  device test, capture logs
```
