package com.openfit.app.protocol;

/**
 * The script-style command channel of the bundled {@code com.zjw.zhbraceletsdk} ("NavPlus")
 * stack. NoiseFit routes ColorFit Ultra Buzz, ColorFit Vision Buzz and ColorFit Ultra 2 through
 * {@code NavPlusConnectHandler}, which binds them with this SDK instead of the SE/Berry stacks.
 *
 * <p>Frames travel on service {@code 00000001} (write {@code 00000002}, notify
 * {@code 00000003}). They are a fixed 13-byte header followed by a length-prefixed payload:
 *
 * <pre>
 * AB 00 00 LL 00 00 00 00 01 00 KK 00 PP [payload]
 * </pre>
 *
 * <ul>
 *   <li>{@code LL} — frame length minus 8, big-endian ({@code payload.length + 5}).</li>
 *   <li>bytes 4..7 are zero in every phone-to-watch request.</li>
 *   <li>{@code 01} — direction, phone to watch ({@code 03} comes back from the watch).</li>
 *   <li>{@code KK} — command key. {@link #KEY_BIND} = {@code 0x6C} starts a bind; the watch
 *       answers with {@link #KEY_BIND_INFO} = {@code 0x34}.</li>
 *   <li>{@code PP} — payload length. The bind payload is a single type byte.</li>
 * </ul>
 *
 * <p>The type byte is {@link #TYPE_BIND} on a first pairing and {@link #TYPE_INQUIRY} when the
 * phone only wants to know whether the watch already has an app binding. The reply repeats the
 * type and carries a result: {@code 1} means bound/accepted, {@code 0} means refused/unbound.
 */
public final class ZhBraceletBinding {
    public static final int MAGIC = 0xAB;
    public static final int DIRECTION_PHONE = 1;
    public static final int DIRECTION_WATCH = 3;

    public static final int KEY_TIME_SYNC = 0x01;
    /** Alarm list write: five packed bytes per alarm ({@code a.a(List<AlarmInfo>)}). */
    public static final int KEY_ALARM = 0x02;
    /** Daily step target: four big-endian bytes ({@code a.d(int)}). */
    public static final int KEY_USER_TARGET_STEP = 0x03;
    public static final int KEY_USER_INFO = 0x04;
    /** Sedentary reminder: four packed bytes ({@code a.a(SitInfo)}). */
    public static final int KEY_SEDENTARY = 0x07;
    /** Unit setting, one byte ({@code a.h(boolean)}). */
    public static final int KEY_UNIT = 0x08;
    /** 12/24-hour clock, one byte ({@code a.g(boolean)}). */
    public static final int KEY_TIME_FORMAT = 0x09;
    /** Bright screen, one byte ({@code a.d(boolean)}). */
    public static final int KEY_BRIGHT_SCREEN = 0x0A;
    /** Device info reply: battery, firmware and DND support ({@code a.a(b,byte[])}). */
    public static final int KEY_DEVICE_INFO = 0x08;
    public static final int KEY_STATUS = 0x0F;
    /** Find-watch: no payload, session flag {@code 0x80} in byte 9 ({@code a.f()}). */
    public static final int KEY_FIND_DEVICE = 0x18;
    /** Device-info request ({@code a.k()}); the watch answers on {@link #KEY_DEVICE_INFO}. */
    public static final int KEY_DEVICE_INFO_REQUEST = 0x1C;
    /** Do-not-disturb, one byte ({@code a.e(boolean)}). */
    public static final int KEY_DND = 0x20;
    /**
     * One-shot measurement result. The vendor's dispatcher maps key 11 to
     * "measurement returns heart rate" and reads the BPM from the first payload byte
     * ({@code service.c.h(byte[]) → service.a.a(byte[],int,int,int)}).
     */
    public static final int KEY_HEART_RATE = 0x0B;
    /** Daily heart-rate history: packed date then 288 five-minute samples. */
    public static final int KEY_HEART_RATE_HISTORY = 0x0D;
    /** Offline blood pressure: count byte then 7-byte records (time, pulse, SYS, DIA). */
    public static final int KEY_OFFLINE_BP = 0x10;
    /** Offline SpO2: count byte then 5-byte records (time, percent). */
    public static final int KEY_OFFLINE_SPO2 = 0x27;
    /** Offline body temperature: count and difference bytes, then 5-byte records. */
    public static final int KEY_OFFLINE_TEMP = 0x28;
    /** Activity duration: packed date, slot count, one byte of active minutes per slot. */
    public static final int KEY_ACTIVITY_DURATION = 0x37;
    /** Offline stress: count byte then 5-byte records (time, index). */
    public static final int KEY_OFFLINE_STRESS = 0x38;
    /**
     * Today's sport frame. Same value as the outgoing alarm key, so the parser checks the
     * direction byte; the watch sends this one, the phone never does.
     */
    public static final int KEY_SPORT_DATA = 0x02;
    /** Sleep frame. Same value as the outgoing step-target key; direction distinguishes them. */
    public static final int KEY_SLEEP_DATA = 0x03;
    /** {@code openMeasurement()} / {@code closeMeasurement()}: 1-byte payload, 1 = start. */
    public static final int KEY_MEASUREMENT = 0x19;
    /** {@code setContinuousHr(boolean)}: 1-byte payload, 1 = continuous HR on. */
    public static final int KEY_CONTINUOUS_HR = 0x29;
    /**
     * Command acknowledgement: payload {@code [command key] 00 [result]}. Observed on the
     * ColorFit Ultra Buzz answering the clock sync (key 1) and user info (key 4) after a bind.
     */
    public static final int KEY_COMMAND_ACK = 0x42;
    public static final int KEY_DEVICE_ANSWER = 0xAC;
    /** Private settings namespace: byte 13 selects the feature (battery saver, reminders, …). */
    public static final int KEY_PRIVATE_FEATURE = 0x7F;
    public static final int KEY_UNBIND = 0x33;

    /**
     * Private-feature selectors (byte 13 of a {@link #KEY_PRIVATE_FEATURE} frame). Every entry was
     * read from the vendor's {@code com.zjw.zhbraceletsdk.service.a} builders and confirmed against
     * the dispatcher in {@code service.c.b(byte[])}.
     */
    public static final int PRIVATE_QUICK_EYE = 0x02;
    public static final int PRIVATE_SEDENTARY = 0x12;
    public static final int PRIVATE_ALARMS = 0x15;
    public static final int PRIVATE_BATTERY_SAVER = 0x22;
    public static final int PRIVATE_MEAL_REMINDER = 0x27;
    public static final int PRIVATE_BODY_TEMP_UNIT = 0x2A;
    public static final int PRIVATE_TIME_ZONE = 0x69;
    public static final int PRIVATE_GOAL = 0x6C;

    public static final int KEY_BIND = 0x6C;
    public static final int KEY_BIND_INFO = 0x34;

    public static final int TYPE_BIND = 0;
    public static final int TYPE_INQUIRY = 1;

    private static final int HEADER_LENGTH = 13;

    private ZhBraceletBinding() {}

    /** Builds the bind/inquiry request the vendor SDK sends from {@code bindDevice(type)}. */
    public static byte[] bindRequest(int type) {
        return frame(KEY_BIND, new byte[] {(byte) type});
    }

    /**
     * Builds the time-sync frame the vendor sends first after the notifications are enabled
     * ({@code com.zjw.zhbraceletsdk.service.c.Q()} → {@code a.B()}). The payload packs the local
     * clock into four bytes:
     *
     * <pre>
     * p0 = yy &lt;&lt; 2 | MM &gt;&gt; 2
     * p1 = dd &lt;&lt; 1 | (MM &amp; 3) &lt;&lt; 6 | HH &gt;&gt; 4
     * p2 = (HH &amp; 15) &lt;&lt; 4 | mm &gt;&gt; 2
     * p3 = (mm &amp; 3) &lt;&lt; 6 | ss
     * </pre>
     */
    public static byte[] timeSyncFrame(int year, int month, int day,
                                       int hour, int minute, int second) {
        if (month < 1 || month > 12 || day < 1 || day > 31
                || hour < 0 || hour > 23 || minute < 0 || minute > 59
                || second < 0 || second > 59) {
            throw new IllegalArgumentException("invalid clock fields");
        }
        int yy = year % 100;
        byte[] payload = {
            (byte) ((yy << 2) | (month >> 2)),
            (byte) ((day << 1) | ((month & 3) << 6) | (hour >> 4)),
            (byte) (((hour & 15) << 4) | (minute >> 2)),
            (byte) (((minute & 3) << 6) | second)
        };
        return frame(KEY_TIME_SYNC, payload);
    }

    /**
     * Builds the user-info frame the vendor sends 50 ms after the time sync
     * ({@code c.setUserInfo} → {@code a.a(UserInfo)}). Bit 7 of the first payload byte is set for
     * a female profile ({@code !getSex()}), the low seven bits carry the age; height and weight
     * are one byte each in centimetres/kilograms, clamped to 1..255 like {@code h.setUserInfo}.
     */
    public static byte[] userInfoFrame(int age, int heightCm, int weightKg, boolean male) {
        byte[] payload = {
            (byte) (((male ? 0 : 1) << 7) | (age & 0x7F)),
            (byte) clampByte(heightCm),
            (byte) clampByte(weightKg),
            0
        };
        return frame(KEY_USER_INFO, payload);
    }

    /**
     * Builds {@code openMeasurement()} / {@code closeMeasurement()} (SDK {@code a.u()} and
     * {@code a.b()}): key {@link #KEY_MEASUREMENT} with a single 1/0 payload byte. Starting a
     * measurement makes the watch report a BPM on {@link #KEY_HEART_RATE}.
     */
    public static byte[] measurementFrame(boolean start) {
        return frame(KEY_MEASUREMENT, new byte[] {(byte) (start ? 1 : 0)});
    }

    /**
     * Builds {@code setContinuousHr(boolean)} (SDK {@code a.j(boolean)}): key
     * {@link #KEY_CONTINUOUS_HR} with a single 1/0 payload byte. This toggles the watch's own
     * continuous heart-rate mode rather than starting a one-shot measurement.
     */
    public static byte[] continuousHeartRateFrame(boolean on) {
        return frame(KEY_CONTINUOUS_HR, new byte[] {(byte) (on ? 1 : 0)});
    }

    /**
     * One-byte on/off setting. The vendor uses this shape for do-not-disturb
     * ({@code a.e(boolean)}), bright screen ({@code a.d(boolean)}), 24-hour time
     * ({@code a.g(boolean)}) and the unit system ({@code a.h(boolean)}).
     */
    public static byte[] toggleFrame(int key, boolean on) {
        return frame(key, new byte[] {(byte) (on ? 1 : 0)});
    }

    /**
     * Find-watch request ({@code a.f()}). It is the only request that sets bit 7 of header byte 9;
     * every other frame leaves that byte zero.
     */
    public static byte[] findDeviceFrame() {
        byte[] out = frame(KEY_FIND_DEVICE, null);
        out[9] = (byte) 0x80;
        return out;
    }

    /** Device-info request ({@code a.k()}); the watch answers on {@link #KEY_DEVICE_INFO}. */
    public static byte[] deviceInfoRequestFrame() {
        return frame(KEY_DEVICE_INFO_REQUEST, null);
    }

    /** Daily step target ({@code a.d(int)}), four big-endian payload bytes. */
    public static byte[] userTargetStepFrame(int steps) {
        return frame(KEY_USER_TARGET_STEP, be32(steps));
    }

    /**
     * Builds a private-feature frame: key {@link #KEY_PRIVATE_FEATURE} with the selector as the
     * first payload byte. The vendor's {@code a.b(byte[], byte, int)} helper writes exactly this.
     */
    public static byte[] privateFeatureFrame(int subKey, byte[] payload) {
        int length = payload == null ? 0 : payload.length;
        byte[] body = new byte[1 + length];
        body[0] = (byte) subKey;
        if (payload != null) System.arraycopy(payload, 0, body, 1, length);
        return frame(KEY_PRIVATE_FEATURE, body);
    }

    /** Private-feature on/off write: payload {@code [1, value]} ({@code a.a/f/c(boolean)}). */
    public static byte[] privateToggleFrame(int subKey, boolean on) {
        return privateFeatureFrame(subKey, new byte[] {1, (byte) (on ? 1 : 0)});
    }

    /** Private-feature read: payload {@code [0]} ({@code a.i()/a.q()/a.r()/a.h()}). */
    public static byte[] privateReadFrame(int subKey) {
        return privateFeatureFrame(subKey, new byte[] {0});
    }

    /**
     * Sedentary reminder ({@code a.a(SitInfo)}): key {@link #KEY_SEDENTARY} and four packed bytes.
     * {@code period} is the vendor's reminder interval code (1..4); the watch stores it verbatim.
     */
    public static byte[] sedentaryFrame(boolean enable, int period,
                                        int startHour, int startMin,
                                        int endHour, int endMin) {
        checkHour(startHour);
        checkHour(endHour);
        checkMinute(startMin);
        checkMinute(endMin);
        if (period < 0 || period > 7) throw new IllegalArgumentException("invalid period");
        byte[] payload = {
            (byte) (((enable ? 1 : 0) << 7) | (period << 4) | (startHour >> 1)),
            (byte) (((startHour & 1) << 7) | (startMin << 1) | (endHour >> 4)),
            (byte) (((endHour & 15) << 4) | (endMin >> 2)),
            (byte) ((endMin & 3) << 6)
        };
        return frame(KEY_SEDENTARY, payload);
    }

    /**
     * Alarm list ({@code a.a(List<AlarmInfo>)}): five packed bytes per alarm. Each record carries
     * the next occurrence date, then the hour, minute, id and repeat mask. {@code data == 128}
     * means a one-off alarm; any other value is the weekday mask the vendor repeats.
     */
    public static byte[] alarmsFrame(java.util.List<Alarm> alarms, java.util.Calendar now) {
        if (alarms == null) alarms = java.util.Collections.emptyList();
        byte[] payload = new byte[alarms.size() * 5];
        int nowHour = now.get(java.util.Calendar.HOUR_OF_DAY);
        int nowMinute = now.get(java.util.Calendar.MINUTE);
        for (int i = 0; i < alarms.size(); i++) {
            Alarm alarm = alarms.get(i);
            checkHour(alarm.hour);
            checkMinute(alarm.minute);
            if (alarm.id < 0 || alarm.id > 7) throw new IllegalArgumentException("invalid alarm id");
            boolean laterToday = alarm.hour > nowHour
                    || (alarm.hour == nowHour && alarm.minute >= nowMinute);
            java.util.Calendar date = (java.util.Calendar) now.clone();
            if (!laterToday) date.add(java.util.Calendar.DAY_OF_MONTH, 1);
            int yy = date.get(java.util.Calendar.YEAR) % 100;
            int month = date.get(java.util.Calendar.MONTH) + 1;
            int day = date.get(java.util.Calendar.DAY_OF_MONTH);
            // The vendor decrements the year for repeating alarms; mirror it byte for byte so the
            // watch schedules exactly as it does for the official app.
            if (alarm.data != 128 && alarm.data != 0) yy--;
            int at = i * 5;
            payload[at] = (byte) ((yy << 2) | (month >> 2));
            payload[at + 1] = (byte) (((month & 3) << 6) | (day << 1) | (alarm.hour >> 4));
            payload[at + 2] = (byte) (((alarm.hour & 15) << 4) | (alarm.minute >> 2));
            payload[at + 3] = (byte) (((alarm.minute & 3) << 6) | (alarm.id << 3));
            payload[at + 4] = (byte) alarm.data;
        }
        return frame(KEY_ALARM, payload);
    }

    /** Convenience overload using the current local time. */
    public static byte[] alarmsFrame(java.util.List<Alarm> alarms) {
        return alarmsFrame(alarms, java.util.Calendar.getInstance());
    }

    /** Decodes a sedentary reply ({@code c.L(byte[])}), or {@code null} for another frame. */
    public static SitReminder parseSedentary(byte[] frame) {
        if (!isPrivateReply(frame, PRIVATE_SEDENTARY) || frame.length < 19) return null;
        if (frame[14] != 0) return null;
        int b2 = frame[15] & 0xFF;
        int b3 = frame[16] & 0xFF;
        int b4 = frame[17] & 0xFF;
        int b5 = frame[18] & 0xFF;
        boolean enable = (b2 >> 7) == 1;
        int period = (b2 & 0x70) >> 4;
        int startHour = ((b2 & 15) << 1) + ((b3 & 0x80) >> 7);
        int startMin = (b3 & 0x7E) >> 1;
        int endHour = ((b3 & 1) << 4) + ((b4 & 0xF0) >> 4);
        int endMin = ((b4 & 15) << 2) + ((b5 & 0xC0) >> 6);
        return new SitReminder(enable, period, startHour, startMin, endHour, endMin);
    }

    /** Decodes an alarm-list reply ({@code c.u(byte[])}), or {@code null} for another frame. */
    public static java.util.List<Alarm> parseAlarms(byte[] frame) {
        if (!isPrivateReply(frame, PRIVATE_ALARMS) || frame.length < 15) return null;
        if (frame[14] != 0) return null;
        int declared = ((frame[11] & 0xFF) << 8) | (frame[12] & 0xFF);
        int count = (declared - 2) / 5;
        if (count < 0) return null;
        java.util.List<Alarm> alarms = new java.util.ArrayList<>();
        int at = 15;
        for (int i = 0; i < count && at + 5 <= frame.length; i++, at += 5) {
            int b3 = frame[at + 1] & 0xFF;
            int b4 = frame[at + 2] & 0xFF;
            int b5 = frame[at + 3] & 0xFF;
            int data = frame[at + 4] & 0xFF;
            int hour = ((b3 & 1) << 4) | (b4 >> 4);
            int minute = ((b4 & 15) << 2) | (b5 >> 6);
            int id = (b5 & 56) >> 3;
            alarms.add(new Alarm(id, hour, minute, data));
        }
        return alarms;
    }

    /** Decodes a private on/off reply; {@code null} when the frame is a different feature. */
    public static Boolean parsePrivateToggle(byte[] frame, int subKey) {
        if (!isPrivateReply(frame, subKey) || frame.length < 16) return null;
        if (frame[14] != 0) return null;
        return (frame[15] & 0xFF) == 1;
    }

    /** Decodes the device-info reply ({@code a.a(b,byte[])}): battery and firmware string. */
    public static DeviceInfo parseDeviceInfo(byte[] frame) {
        if (frame == null || frame.length < 16) return null;
        if ((frame[0] & 0xFF) != MAGIC) return null;
        if ((frame[8] & 0xFF) != DIRECTION_WATCH) return null;
        if ((frame[10] & 0xFF) != KEY_DEVICE_INFO) return null;
        int battery = frame[13] & 0xFF;
        int versionNumber = ((frame[7] & 0xFF) << 8) | (frame[14] & 0xFF);
        int versionTail = frame[15] & 0xFF;
        return new DeviceInfo(battery, versionNumber, formatVersion(versionNumber, versionTail));
    }

    // ---------------------------------------------------------------------------------------
    // Sensor history frames (read-only; the watch pushes these after a bind)
    // ---------------------------------------------------------------------------------------

    /**
     * Today's sport frame ({@code c.i(byte[])} → {@code a.a(byte[],float,float)}). The payload
     * carries the packed date in bytes 13-14, a slot count in byte 16 and that many big-endian
     * 16-bit step counts from byte 17. The vendor sums the slots into the daily step total.
     */
    public static Steps parseSteps(byte[] frame) {
        if (!isWatchFrame(frame, KEY_SPORT_DATA) || frame.length < 17) return null;
        int count = frame[16] & 31;
        if (frame.length < 17 + count * 2) return null;
        String date = parsePackedDate(frame);
        if (date == null) return null;
        int total = 0;
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            int value = ((frame[17 + i * 2] & 0xFF) << 8) | (frame[18 + i * 2] & 0xFF);
            slots[i] = value;
            total += value;
        }
        return new Steps(date, total, slots);
    }

    /**
     * Activity-duration frame ({@code c.t(byte[])}): the same packed date, a slot count in byte
     * 16 and one unsigned byte of active minutes per slot from byte 17.
     */
    public static Activity parseActivity(byte[] frame) {
        if (!isWatchFrame(frame, KEY_ACTIVITY_DURATION) || frame.length < 17) return null;
        int count = frame[16] & 31;
        if (frame.length < 17 + count) return null;
        String date = parsePackedDate(frame);
        if (date == null) return null;
        int total = 0;
        int[] slots = new int[count];
        for (int i = 0; i < count; i++) {
            int value = frame[17 + i] & 0xFF;
            slots[i] = value;
            total += value;
        }
        return new Activity(date, total, slots);
    }

    /**
     * Sleep frame ({@code c.o(byte[])} → {@code a.d(byte[])}). Byte 15 is the record count; each
     * record is two bytes holding a start time and a stage code. A record's stage runs until the
     * next record's start, so the last record is the end marker. Stages: 1 awake, 2 light,
     * 3 deep, 4 REM, 6 stay-up. The vendor's headline sleep total is deep + light; REM is
     * reported separately, so {@link SleepSummary#total} matches it rather than adding REM in.
     * The date is the packed date plus one day, as the vendor labels the morning.
     */
    public static SleepSummary parseSleep(byte[] frame) {
        if (!isWatchFrame(frame, KEY_SLEEP_DATA) || frame.length < 16) return null;
        int count = frame[15] & 0xFF;
        if (frame.length < 16 + count * 2) return null;
        String date = parseSleepDate(frame);
        if (date == null) return null;
        int deep = 0, light = 0, rem = 0, awake = 0, stayUp = 0;
        java.util.List<SleepStage> stages = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < count; i++) {
            int at = 16 + i * 2;
            int next = at + 2;
            int start = recordMinutes(frame[at], frame[at + 1]);
            int end = recordMinutes(frame[next], frame[next + 1]);
            int minutes = end - start;
            if (minutes < 0) minutes += 24 * 60;
            int stage = frame[at + 1] & 0x0F;
            stages.add(new SleepStage(start, stage));
            switch (stage) {
                case 1: awake += minutes; break;
                case 2: light += minutes; break;
                case 3: deep += minutes; break;
                case 4: rem += minutes; break;
                case 6: stayUp += minutes; break;
                default: break;
            }
        }
        int startMinute = stages.isEmpty() ? 0 : stages.get(0).startMinute;
        int lastAt = 16 + (count - 1) * 2;
        int endMinute = count == 0 ? 0
                : recordMinutes(frame[lastAt], frame[lastAt + 1]);
        return new SleepSummary(date, deep + light, deep, light, rem, awake, stayUp,
                startMinute, endMinute, stages);
    }

    /**
     * Encodes the ordered stage runs as {@code start:stage} pairs joined by commas so the phone can
     * persist the night's shape and redraw a hypnogram later without the watch frame.
     */
    public static String encodeSleepStages(java.util.List<SleepStage> stages) {
        if (stages == null || stages.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        for (SleepStage stage : stages) {
            if (out.length() > 0) out.append(',');
            out.append(stage.startMinute).append(':').append(stage.stage);
        }
        return out.toString();
    }

    /** Reverses {@link #encodeSleepStages}; malformed entries are skipped rather than failing. */
    public static java.util.List<SleepStage> decodeSleepStages(String csv) {
        java.util.List<SleepStage> stages = new java.util.ArrayList<>();
        if (csv == null || csv.isEmpty()) return stages;
        for (String part : csv.split(",")) {
            int colon = part.indexOf(':');
            if (colon < 0) continue;
            try {
                int start = Integer.parseInt(part.substring(0, colon).trim());
                int stage = Integer.parseInt(part.substring(colon + 1).trim());
                stages.add(new SleepStage(start, stage));
            } catch (NumberFormatException ignored) {
                // A corrupt entry cannot produce a stage run, so drop just that one.
            }
        }
        return stages;
    }

    /** One sleep stage run: [startMinute] is minutes since midnight, [stage] the vendor code. */
    public static final class SleepStage {
        public final int startMinute;
        public final int stage;

        public SleepStage(int startMinute, int stage) {
            this.startMinute = startMinute;
            this.stage = stage;
        }

        @Override public String toString() {
            return startMinute + ":" + stage;
        }
    }

    /** Offline SpO2 frame ({@code c.F(byte[])}): 5-byte records of time then percentage. */
    public static java.util.List<SensorSample> parseSpo2(byte[] frame) {
        return parseFiveByteSamples(frame, KEY_OFFLINE_SPO2);
    }

    /** Offline stress frame ({@code c.G(byte[])}): the same 5-byte record shape. */
    public static java.util.List<SensorSample> parseStress(byte[] frame) {
        return parseFiveByteSamples(frame, KEY_OFFLINE_STRESS);
    }

    /**
     * Offline body-temperature frame ({@code c.E(byte[])}): byte 14 is the vendor's difference
     * value, byte 15 the count, then 5-byte records of time and a raw wrist-temperature byte.
     */
    public static java.util.List<TempSample> parseBodyTemperature(byte[] frame) {
        if (!isWatchFrame(frame, KEY_OFFLINE_TEMP) || frame.length < 16) return null;
        int count = frame[15] & 0xFF;
        int difference = frame[14] & 0xFF;
        if (frame.length < 16 + count * 5) return null;
        java.util.List<TempSample> samples = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int at = 16 + i * 5;
            String time = parseRecordTime(frame[at], frame[at + 1], frame[at + 2], frame[at + 3]);
            if (time == null) continue;
            samples.add(new TempSample(time, frame[at + 4] & 0xFF, difference));
        }
        return samples;
    }

    /**
     * Offline blood-pressure frame ({@code a.b(byte[])}): count byte then 7-byte records of time,
     * pulse, systolic and diastolic. The vendor drops records with a zero field.
     */
    public static java.util.List<BloodPressureSample> parseOfflineBloodPressure(byte[] frame) {
        if (!isWatchFrame(frame, KEY_OFFLINE_BP) || frame.length < 16) return null;
        int count = frame[15] & 0xFF;
        if (frame.length < 16 + count * 7) return null;
        java.util.List<BloodPressureSample> samples = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int at = 16 + i * 7;
            String time = parseRecordTime(frame[at], frame[at + 1], frame[at + 2], frame[at + 3]);
            int pulse = frame[at + 4] & 0xFF;
            int systolic = frame[at + 5] & 0xFF;
            int diastolic = frame[at + 6] & 0xFF;
            if (time == null || pulse <= 0 || systolic <= 0 || diastolic <= 0) continue;
            samples.add(new BloodPressureSample(time, pulse, systolic, diastolic));
        }
        return samples;
    }

    private static java.util.List<SensorSample> parseFiveByteSamples(byte[] frame, int key) {
        if (!isWatchFrame(frame, key) || frame.length < 16) return null;
        int count = frame[15] & 0xFF;
        if (frame.length < 16 + count * 5) return null;
        java.util.List<SensorSample> samples = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int at = 16 + i * 5;
            String time = parseRecordTime(frame[at], frame[at + 1], frame[at + 2], frame[at + 3]);
            if (time == null) continue;
            samples.add(new SensorSample(time, frame[at + 4] & 0xFF));
        }
        return samples;
    }

    private static boolean isWatchFrame(byte[] frame, int key) {
        return frame != null && frame.length >= 13
                && (frame[0] & 0xFF) == MAGIC
                && (frame[8] & 0xFF) == DIRECTION_WATCH
                && (frame[10] & 0xFF) == key;
    }

    /** Packed date in bytes 13-14 ({@code e.e(byte[])}), or {@code null} when invalid. */
    private static String parsePackedDate(byte[] frame) {
        int year = 2000 + ((frame[13] & 0x7E) >> 1);
        int month = ((frame[13] & 1) << 3) | ((frame[14] & 0xE0) >> 5);
        int day = frame[14] & 0x1F;
        if (month < 1 || month > 12 || day < 1 || day > 31) return null;
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    /**
     * Sleep date, which the vendor reports as the packed date plus one day
     * ({@code e.d(e.e(byte[]))}): the frame carries the night's start, the app labels the morning.
     */
    private static String parseSleepDate(byte[] frame) {
        int year = 2000 + ((frame[13] & 0x7E) >> 1);
        int month = ((frame[13] & 1) << 3) | ((frame[14] & 0xE0) >> 5);
        int day = frame[14] & 0x1F;
        if (month < 1 || month > 12 || day < 1 || day > 31) return null;
        int[] monthDays = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        boolean leap = (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0));
        int maxDay = monthDays[month - 1] + (month == 2 && leap ? 1 : 0);
        day++;
        if (day > maxDay) {
            day = 1;
            month++;
            if (month > 12) { month = 1; year++; }
        }
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    /** Start of a sleep record, in minutes since midnight. */
    private static int recordMinutes(byte b0, byte b1) {
        return ((b0 & 0xF8) >> 3) * 60 + (((b0 & 7) << 3) | ((b1 & 0xE0) >> 5));
    }

    /**
     * Timestamp of a 5- or 7-byte sensor record ({@code c.r(byte[])}), or {@code null} when the
     * packed fields are out of range.
     */
    private static String parseRecordTime(byte b0, byte b1, byte b2, byte b3) {
        int year = 2000 + ((b0 & 0xFF) >> 2);
        int month = ((b0 & 3) << 2) | ((b1 & 0xFF) >> 6);
        int day = (b1 & 0x3E) >> 1;
        int hour = ((b1 & 1) << 4) | ((b2 & 0xFF) >> 4);
        int minute = ((b2 & 15) << 2) | ((b3 & 0xFF) >> 6);
        int second = b3 & 63;
        if (month < 1 || month > 12 || day < 1 || day > 31
                || hour > 23 || minute > 59 || second > 59) {
            return null;
        }
        return String.format(java.util.Locale.US, "%04d-%02d-%02d %02d:%02d:%02d",
                year, month, day, hour, minute, second);
    }

    private static String formatVersion(int number, int tail) {
        String prefix;
        int body;
        if (number > 10000 && number < 20000) {
            prefix = "R";
            body = number - 10000;
        } else if (number > 20000 && number < 30000) {
            prefix = "D";
            body = number - 20000;
        } else {
            prefix = "N";
            body = number;
        }
        if (body < 1 || tail < 1) return "1.1.0";
        return prefix + body + "." + ((tail / 10) + 1) + "." + (tail % 10);
    }

    private static boolean isPrivateReply(byte[] frame, int subKey) {
        if (frame == null || frame.length < 15) return false;
        if ((frame[0] & 0xFF) != MAGIC) return false;
        if ((frame[8] & 0xFF) != DIRECTION_WATCH) return false;
        if ((frame[10] & 0xFF) != KEY_PRIVATE_FEATURE) return false;
        return (frame[13] & 0xFF) == subKey;
    }

    private static byte[] be32(int value) {
        return new byte[] {
            (byte) ((value >> 24) & 0xFF),
            (byte) ((value >> 16) & 0xFF),
            (byte) ((value >> 8) & 0xFF),
            (byte) (value & 0xFF)
        };
    }

    private static void checkHour(int hour) {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("invalid hour");
    }

    private static void checkMinute(int minute) {
        if (minute < 0 || minute > 59) throw new IllegalArgumentException("invalid minute");
    }

    /** One sedentary-reminder configuration, decoded from {@link #PRIVATE_SEDENTARY}. */
    public static final class SitReminder {
        public final boolean enable;
        public final int period;
        public final int startHour;
        public final int startMin;
        public final int endHour;
        public final int endMin;

        public SitReminder(boolean enable, int period,
                           int startHour, int startMin, int endHour, int endMin) {
            this.enable = enable;
            this.period = period;
            this.startHour = startHour;
            this.startMin = startMin;
            this.endHour = endHour;
            this.endMin = endMin;
        }

        @Override public String toString() {
            return "SitReminder{enable=" + enable + ", period=" + period
                    + ", start=" + startHour + ":" + startMin
                    + ", end=" + endHour + ":" + endMin + '}';
        }
    }

    /** One alarm record. {@code data == 128} means one-off, otherwise it is the weekday mask. */
    public static final class Alarm {
        public final int id;
        public final int hour;
        public final int minute;
        public final int data;

        public Alarm(int id, int hour, int minute, int data) {
            this.id = id;
            this.hour = hour;
            this.minute = minute;
            this.data = data;
        }

        public boolean repeats() {
            return data != 128;
        }

        @Override public String toString() {
            return String.format(java.util.Locale.US, "Alarm{id=%d, %02d:%02d, data=%d}",
                    id, hour, minute, data);
        }
    }

    /** Battery percentage and firmware string from {@link #KEY_DEVICE_INFO}. */
    public static final class DeviceInfo {
        public final int battery;
        public final int versionNumber;
        public final String versionName;

        public DeviceInfo(int battery, int versionNumber, String versionName) {
            this.battery = battery;
            this.versionNumber = versionNumber;
            this.versionName = versionName;
        }

        @Override public String toString() {
            return "DeviceInfo{battery=" + battery + ", version=" + versionName + '}';
        }
    }

    /**
     * Daily heart-rate history, key {@code 0x0D} (13, "sport heart rate"). The vendor's
     * {@code c.p(byte[])} → {@code a.e(byte[])} reads a packed date from bytes 13-14 and then
     * 288 unsigned samples, one per five-minute slot of the day:
     *
     * <pre>
     * frame[13] = 20YY &lt;&lt; 1 | month &gt;&gt; 3
     * frame[14] = (month &amp; 7) &lt;&lt; 5 | day
     * frame[17 .. 304] = 288 samples, 0 = no reading
     * </pre>
     *
     * The first 96 slots are the sleep window for the sleep statistics. The vendor drops the
     * whole payload when any nonzero sample is below 45 bpm, which filters corrupt arrays.
     */
    public static HeartRateHistory parseHeartRateHistory(byte[] frame) {
        if (frame == null || frame.length < HEADER_LENGTH + 292) return null;
        if ((frame[0] & 0xFF) != MAGIC) return null;
        if ((frame[8] & 0xFF) != DIRECTION_WATCH) return null;
        if ((frame[10] & 0xFF) != KEY_HEART_RATE_HISTORY) return null;
        int year = (frame[13] & 0x7E) >> 1;
        int month = ((frame[13] & 1) << 3) | ((frame[14] & 0xE0) >> 5);
        int day = frame[14] & 0x1F;
        if (year < 17 || month <= 0 || day <= 0) return null;
        int recent = 0;
        int dayMax = 0;
        int dayMin = 0;
        int daySum = 0;
        int dayCount = 0;
        int[] all = new int[288];
        int[] slots = new int[288];
        for (int i = 0; i < 288; i++) {
            int bpm = frame[17 + i] & 0xFF;
            slots[i] = bpm;
            if (bpm == 0) continue;
            if (bpm < 45) return null; // the vendor treats this as a corrupt array
            recent = bpm;
            if (bpm > dayMax) dayMax = bpm;
            if (dayMin == 0 || bpm < dayMin) dayMin = bpm;
            daySum += bpm;
            all[dayCount++] = bpm;
        }
        String date = String.format(java.util.Locale.US, "20%02d-%02d-%02d", year, month, day);
        return new HeartRateHistory(date, recent, dayMax, dayMin,
                dayCount == 0 ? 0 : daySum / dayCount, dayCount,
                java.util.Arrays.copyOf(all, dayCount), slots);
    }

    /** One decoded {@link #KEY_HEART_RATE_HISTORY} frame. */
    public static final class HeartRateHistory {
        public final String date;
        /** Last nonzero sample of the day, i.e. the most recent reading (0 if none). */
        public final int recent;
        public final int dayMax;
        public final int dayMin;
        public final int dayAverage;
        public final int sampleCount;
        /** Nonzero readings in slot order, for the Home sparkline. */
        public final int[] samples;
        /** All 288 five-minute slots (zeros kept) so the graph can place readings in time. */
        public final int[] slots;

        public HeartRateHistory(String date, int recent, int dayMax, int dayMin,
                                int dayAverage, int sampleCount, int[] samples) {
            this(date, recent, dayMax, dayMin, dayAverage, sampleCount, samples, null);
        }

        public HeartRateHistory(String date, int recent, int dayMax, int dayMin,
                                int dayAverage, int sampleCount, int[] samples, int[] slots) {
            this.date = date;
            this.recent = recent;
            this.dayMax = dayMax;
            this.dayMin = dayMin;
            this.dayAverage = dayAverage;
            this.sampleCount = sampleCount;
            this.samples = samples;
            this.slots = slots;
        }

        @Override public String toString() {
            return "HeartRateHistory{date=" + date + ", recent=" + recent
                    + ", min=" + dayMin + ", max=" + dayMax + ", avg=" + dayAverage
                    + ", samples=" + sampleCount + '}';
        }
    }

    /** One decoded {@link #KEY_SPORT_DATA} frame: daily step total plus the per-slot counts. */
    public static final class Steps {
        public final String date;
        public final int total;
        public final int[] slots;

        public Steps(String date, int total, int[] slots) {
            this.date = date;
            this.total = total;
            this.slots = slots;
        }

        @Override public String toString() {
            return "Steps{date=" + date + ", total=" + total + ", slots=" + slots.length + '}';
        }
    }

    /** One decoded {@link #KEY_ACTIVITY_DURATION} frame: active minutes plus per-slot values. */
    public static final class Activity {
        public final String date;
        public final int totalMinutes;
        public final int[] slots;

        public Activity(String date, int totalMinutes, int[] slots) {
            this.date = date;
            this.totalMinutes = totalMinutes;
            this.slots = slots;
        }

        @Override public String toString() {
            return "Activity{date=" + date + ", minutes=" + totalMinutes
                    + ", slots=" + slots.length + '}';
        }
    }

    /** One decoded {@link #KEY_SLEEP_DATA} frame; every duration field is in minutes. */
    public static final class SleepSummary {
        public final String date;
        /** Deep + light, matching the vendor's headline sleep duration (REM is separate). */
        public final int total;
        public final int deep;
        public final int light;
        public final int rem;
        public final int awake;
        public final int stayUp;
        /** Minutes since midnight of the first stage run (the night's start). */
        public final int startMinute;
        /** Minutes since midnight of the final record, i.e. the morning wake marker. */
        public final int endMinute;
        /** The ordered stage runs; each lasts until the next one begins. */
        public final java.util.List<SleepStage> stages;

        public SleepSummary(String date, int total, int deep, int light, int rem,
                            int awake, int stayUp, int startMinute, int endMinute,
                            java.util.List<SleepStage> stages) {
            this.date = date;
            this.total = total;
            this.deep = deep;
            this.light = light;
            this.rem = rem;
            this.awake = awake;
            this.stayUp = stayUp;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            this.stages = stages == null ? java.util.Collections.emptyList() : stages;
        }

        @Override public String toString() {
            return "SleepSummary{date=" + date + ", total=" + total + ", deep=" + deep
                    + ", light=" + light + ", rem=" + rem + ", awake=" + awake
                    + ", stages=" + stages.size() + '}';
        }
    }

    /** One timestamped single-value sensor record: SpO2 percent or stress index. */
    public static final class SensorSample {
        /** Local wall-clock time of the reading, {@code yyyy-MM-dd HH:mm:ss}. */
        public final String time;
        public final int value;

        public SensorSample(String time, int value) {
            this.time = time;
            this.value = value;
        }

        @Override public String toString() {
            return "SensorSample{" + time + " = " + value + '}';
        }
    }

    /** One timestamped wrist-temperature record. {@code raw} is the watch byte, un-scaled. */
    public static final class TempSample {
        public final String time;
        public final int raw;
        /** Vendor difference byte from the frame header, kept for the display scale. */
        public final int difference;

        public TempSample(String time, int raw, int difference) {
            this.time = time;
            this.raw = raw;
            this.difference = difference;
        }

        @Override public String toString() {
            return "TempSample{" + time + " raw=" + raw + ", diff=" + difference + '}';
        }
    }

    /** One timestamped offline blood-pressure record. */
    public static final class BloodPressureSample {
        public final String time;
        public final int pulse;
        public final int systolic;
        public final int diastolic;

        public BloodPressureSample(String time, int pulse, int systolic, int diastolic) {
            this.time = time;
            this.pulse = pulse;
            this.systolic = systolic;
            this.diastolic = diastolic;
        }

        @Override public String toString() {
            return "BloodPressureSample{" + time + ", " + systolic + '/' + diastolic
                    + ", pulse=" + pulse + '}';
        }
    }

    private static int clampByte(int value) {
        if (value < 1) return 1;
        return Math.min(value, 255);
    }

    /**
     * Parses a {@link #KEY_HEART_RATE} reply and returns the BPM, or {@code -1} when this is not a
     * heart-rate frame or the watch reported nothing ({@code 0}). The vendor reads exactly
     * {@code frame[13] & 0xFF} ({@code service.a.a(byte[],int,int,int)}).
     */
    public static int parseHeartRate(byte[] frame) {
        if (frame == null || frame.length < 14) return -1;
        if ((frame[0] & 0xFF) != MAGIC) return -1;
        if ((frame[8] & 0xFF) != DIRECTION_WATCH) return -1;
        if ((frame[10] & 0xFF) != KEY_HEART_RATE) return -1;
        int bpm = frame[13] & 0xFF;
        return bpm > 0 ? bpm : -1;
    }

    /** Parses one acknowledgement reply, or returns {@code null} for any other frame. */
    public static Ack parseAck(byte[] frame) {
        if (frame == null || frame.length < 14) return null;
        if ((frame[0] & 0xFF) != MAGIC) return null;
        if ((frame[8] & 0xFF) != DIRECTION_WATCH) return null;
        int key = frame[10] & 0xFF;
        if (key == KEY_STATUS) {
            return new Ack(key, -1, (frame[13] & 0xFF) == 1);
        }
        if (key == KEY_COMMAND_ACK && frame.length >= 16) {
            // Payload: the acknowledged command key, one reserved byte, then the result.
            return new Ack(key, frame[13] & 0xFF, (frame[15] & 0xFF) == 1);
        }
        if (key == KEY_DEVICE_ANSWER && frame.length >= 15) {
            return new Ack(key, frame[13] & 0xFF, (frame[14] & 0xFF) == 1);
        }
        return null;
    }

    /** Builds one phone-to-watch frame with the vendor's header layout. */
    public static byte[] frame(int key, byte[] payload) {
        int payloadLength = payload == null ? 0 : payload.length;
        byte[] out = new byte[HEADER_LENGTH + payloadLength];
        out[0] = (byte) MAGIC;
        out[1] = 0;
        int bodyLength = out.length - 8;
        out[2] = (byte) ((bodyLength >> 8) & 0xFF);
        out[3] = (byte) (bodyLength & 0xFF);
        // out[4..7] stay zero: the vendor leaves the session fields empty on every request.
        out[8] = (byte) DIRECTION_PHONE;
        out[9] = 0;
        out[10] = (byte) key;
        out[11] = (byte) ((payloadLength >> 8) & 0xFF);
        out[12] = (byte) (payloadLength & 0xFF);
        if (payload != null) System.arraycopy(payload, 0, out, HEADER_LENGTH, payload.length);
        return out;
    }

    /** Total frame length declared by the header, or {@code -1} when the header is malformed. */
    public static int declaredLength(byte[] frame) {
        if (frame == null || frame.length < 4) return -1;
        if ((frame[0] & 0xFF) != MAGIC) return -1;
        int body = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        int total = body + 8;
        return total < HEADER_LENGTH ? -1 : total;
    }

    /** Returns the bind-info reply, or {@code null} when this is not one. */
    public static BindInfo parseBindInfo(byte[] frame) {
        if (frame == null || frame.length < HEADER_LENGTH + 2) return null;
        if ((frame[0] & 0xFF) != MAGIC) return null;
        if ((frame[8] & 0xFF) != DIRECTION_WATCH) return null;
        if ((frame[10] & 0xFF) != KEY_BIND_INFO) return null;
        return new BindInfo(frame[13] & 0xFF, frame[14] & 0xFF);
    }

    /** One {@code KEY_BIND_INFO} reply: the type that was asked about and the watch's result. */
    public static final class BindInfo {
        public final int type;
        public final int result;

        public BindInfo(int type, int result) {
            this.type = type;
            this.result = result;
        }

        public boolean accepted() {
            return result == 1;
        }

        @Override public String toString() {
            return "BindInfo{type=" + type + ", result=" + result + '}';
        }
    }

    /** Human-readable name for a command key, for the on-device log. */
    public static String keyLabel(int key) {
        switch (key) {
            case KEY_TIME_SYNC: return "time sync";
            case KEY_ALARM: return "alarms";
            case KEY_USER_TARGET_STEP: return "step target";
            case KEY_USER_INFO: return "user info";
            case KEY_SEDENTARY: return "sedentary reminder";
            case KEY_UNIT: return "unit / device info";
            case KEY_TIME_FORMAT: return "24-hour time";
            case KEY_BRIGHT_SCREEN: return "bright screen";
            case KEY_STATUS: return "status";
            case KEY_FIND_DEVICE: return "find watch";
            case KEY_DEVICE_INFO_REQUEST: return "device info request";
            case KEY_DND: return "do not disturb";
            case KEY_UNBIND: return "unbind";
            case KEY_HEART_RATE: return "heart rate";
            case KEY_HEART_RATE_HISTORY: return "heart-rate history";
            case KEY_MEASUREMENT: return "measurement";
            case KEY_CONTINUOUS_HR: return "continuous HR";
            case KEY_BIND_INFO: return "bind info";
            case KEY_BIND: return "bind";
            case KEY_COMMAND_ACK: return "command ack";
            case KEY_DEVICE_ANSWER: return "device answer";
            case KEY_PRIVATE_FEATURE: return "private feature";
            default:
                return String.format(java.util.Locale.US, "key 0x%02X", key & 0xFF);
        }
    }

    /** Human-readable name for a private-feature selector, for the on-device log. */
    public static String privateFeatureLabel(int subKey) {
        switch (subKey) {
            case PRIVATE_QUICK_EYE: return "quick-eye switch";
            case PRIVATE_SEDENTARY: return "sedentary reminder";
            case PRIVATE_ALARMS: return "alarm list";
            case PRIVATE_BATTERY_SAVER: return "battery saver";
            case PRIVATE_MEAL_REMINDER: return "meal reminders";
            case PRIVATE_BODY_TEMP_UNIT: return "body-temperature unit";
            case PRIVATE_TIME_ZONE: return "time zone";
            case PRIVATE_GOAL: return "goal";
            default:
                return String.format(java.util.Locale.US, "private 0x%02X", subKey & 0xFF);
        }
    }

    /**
     * A generic acknowledgement: {@code KEY_STATUS} (0x0F) answers any set command, while
     * {@code KEY_DEVICE_ANSWER} (0xAC) echoes the original command key and its own status byte.
     */
    public static final class Ack {
        /** The reply key: {@link #KEY_STATUS} or {@link #KEY_DEVICE_ANSWER}. */
        public final int key;
        /** Original command key for {@code KEY_DEVICE_ANSWER}, else {@code -1}. */
        public final int refersTo;
        public final boolean success;

        public Ack(int key, int refersTo, boolean success) {
            this.key = key;
            this.refersTo = refersTo;
            this.success = success;
        }

        public String describe() {
            if (key == KEY_COMMAND_ACK) {
                return "command ack · " + ZhBraceletBinding.keyLabel(refersTo)
                        + " · " + (success ? "ok" : "refused");
            }
            String name = key == KEY_STATUS ? "status" : "device answer";
            String target = refersTo < 0 ? "" : " for key " + refersTo;
            return name + target + " · " + (success ? "ok" : "refused");
        }
    }
}
