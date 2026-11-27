package com.openfit.app.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Commands for the <b>SE / Apricot</b> watches (the Noise Watch Beat family) on the
 * {@code 16186f00} service. These are the commands NoiseFit sends when
 * {@code ControlBleTools.isApricotProtocol()} is true; the ColorFit Ultra Buzz uses the separate
 * ZhBracelet key protocol instead, so nothing here affects it.
 *
 * <p>Every frame is a {@code com.zh.ble.wear.protobuf.SEWear} envelope:
 * <pre>
 * 1   id             SEFunctionId
 * 4   device         SEDevice          (info / battery)
 * 5   system_time    SESystemTime      (id 48)
 * 6   user_settings  SEUserSettings    (id 68)
 * 15  setting_menu   SESettingMenu    (HR, sedentary, power saving, real-time HR)
 * 100 error_code
 * </pre>
 *
 * <p>Field numbers and enum values were read from the vendor's generated classes and its
 * encoders; the byte forms in {@link SeCommandsTest} pin them. These messages are proto2, so a
 * field the vendor sets to a default value (for example {@code on=false}) is still written.
 */
public final class SeCommands {
    // SEFunctionId values used for the settings surface.
    public static final int FUNCTION_DEVICE_INFO = 32;
    public static final int FUNCTION_DEVICE_BATTERY = 33;
    public static final int FUNCTION_SET_TIME = 48;
    public static final int FUNCTION_SET_USER_PROFILE = 68;
    public static final int FUNCTION_FIND_WEAR = 161;
    public static final int FUNCTION_GET_HEART_RATE_MONITOR = 214;
    public static final int FUNCTION_SET_HEART_RATE_MONITOR = 215;
    public static final int FUNCTION_GET_SEDENTARY = 217;
    public static final int FUNCTION_SET_SEDENTARY = 218;
    public static final int FUNCTION_GET_POWER_SAVING = 235;
    public static final int FUNCTION_SET_POWER_SAVING = 236;
    public static final int FUNCTION_GET_REAL_TIME_HEART_RATE = 730;
    public static final int FUNCTION_SET_REAL_TIME_HEART_RATE = 731;
    public static final int FUNCTION_REPORT_REAL_TIME_HEART_RATE = 733;

    // SEFunctionId values for the watch-face surface. Only the two read-only ids are wired so
    // far; the set / remove / install ids are listed here so the next step has one source of
    // truth. Nothing sends them yet.
    public static final int FUNCTION_GET_WATCH_FACE_LIST = 80;
    public static final int FUNCTION_SET_WATCH_FACE = 81;
    public static final int FUNCTION_REMOVE_WATCH_FACE = 82;
    public static final int FUNCTION_PREPARE_INSTALL_WATCH_FACE = 83;
    public static final int FUNCTION_REPORT_INSTALL_RESULT = 84;
    public static final int FUNCTION_REQUEST_DEVICE_WATCH_FACE_CONFIG = 85;
    public static final int FUNCTION_SET_DEVICE_WATCH_FACE_CONFIG = 86;

    // SEWear payload fields.
    public static final int WEAR_DEVICE = 4;
    public static final int WEAR_SYSTEM_TIME = 5;
    public static final int WEAR_USER_SETTINGS = 6;
    public static final int WEAR_WATCH_FACE = 7;
    public static final int WEAR_SETTING_MENU = 15;

    // SEWatchFace payload cases (oneof). 1 item_list, 7 watch_face_config.
    public static final int WATCH_FACE_ITEM_LIST = 1;
    public static final int WATCH_FACE_CONFIG = 7;

    // SEWatchFaceItem fields (inside SEWatchFaceItem.SEList).
    public static final int WATCH_FACE_ITEM = 1;
    public static final int WATCH_FACE_ITEM_ID = 1;
    public static final int WATCH_FACE_ITEM_IS_CURRENT = 2;
    public static final int WATCH_FACE_ITEM_IS_REMOVE = 3;

    // SEWatchFaceConfig fields.
    public static final int WATCH_FACE_CONFIG_ID = 1;
    public static final int WATCH_FACE_CONFIG_RULE_COUNT = 2;

    // SEDevice fields.
    public static final int DEVICE_INFO = 1;
    public static final int DEVICE_BATTERY = 2;

    // SEDeviceInfo fields.
    public static final int INFO_FIRMWARE = 1;
    public static final int INFO_EQUIPMENT_NUMBER = 2;
    public static final int INFO_MAC = 3;
    public static final int INFO_SERIAL = 4;
    public static final int INFO_BATTERY = 5;

    // SEDeviceBatteryStatus fields.
    public static final int BATTERY_CAPACITY = 1;
    public static final int BATTERY_CHARGE_STATUS = 2;

    // SESystemTime / SETimeSet fields.
    public static final int SYSTEM_TIME_SET = 1;
    public static final int TIME_TIMESTAMP = 1;
    public static final int TIME_OFFSET = 2;
    public static final int TIME_FORMAT = 3;

    // SEUserSettings / SEUserProfile fields.
    public static final int USER_PROFILE = 5;
    public static final int PROFILE_HEIGHT = 1;
    public static final int PROFILE_WEIGHT = 2;
    public static final int PROFILE_AGE = 4;
    public static final int PROFILE_SEX = 5;
    public static final int SEX_MALE = 1;
    public static final int SEX_FEMALE = 2;

    // SESettingMenu fields.
    public static final int MENU_HEART_RATE_MONITOR = 3;
    public static final int MENU_SEDENTARY = 4;
    public static final int MENU_POWER_SAVING = 10;
    public static final int MENU_REAL_TIME_SETTINGS = 38;
    public static final int MENU_REAL_TIME_DATA = 39;

    // SEHeartRateMonitor fields and enums.
    public static final int HR_MODE = 1;
    public static final int HR_FREQUENCY = 2;
    public static final int HR_WARNING = 3;
    public static final int HR_WARNING_VALUE = 4;
    public static final int HR_SPORT_WARNING = 5;
    public static final int HR_SPORT_WARNING_VALUE = 6;
    public static final int HR_CONTINUOUS_MODE = 7;
    public static final int HR_LOW_WARNING_VALUE = 8;
    public static final int HR_MODE_AUTO = 0;
    public static final int HR_MODE_OFF = 1;
    public static final int HR_CONTINUOUS_ALL_DAY = 0;
    public static final int HR_CONTINUOUS_INTELLIGENT = 1;

    // SESedentaryReminder fields.
    public static final int SEDENTARY_ON = 1;
    public static final int SEDENTARY_START = 2;
    public static final int SEDENTARY_END = 3;
    public static final int SEDENTARY_FREQUENCY = 4;
    public static final int SEDENTARY_NO_DISTURB = 5;

    // SESettingTime fields.
    public static final int SETTING_TIME_HOUR = 1;
    public static final int SETTING_TIME_MINUTE = 2;
    public static final int SETTING_TIME_SECOND = 3;
    public static final int SETTING_TIME_MILLIS = 4;

    // SEPowerSaving fields.
    public static final int POWER_SAVING_ON = 1;

    // SERealTimeHeartRateSettings fields.
    public static final int RT_SWITCH = 1;
    public static final int RT_FREQUENCY = 2;
    public static final int RT_AUTO_SHUTDOWN = 3;

    // SERealTimeHeartRateData fields.
    public static final int RT_DATA_TIMESTAMP = 1;
    public static final int RT_DATA_VALUE = 2;

    private SeCommands() {}

    // ------------------------------------------------------------------------------------
    // Encoders
    // ------------------------------------------------------------------------------------

    /**
     * {@code SEWear{id=48, system_time{time_set{timestamp, offset}}}}. The offset is in quarter
     * hours east of UTC, exactly as the vendor computes it from {@code SimpleDateFormat("Z")}.
     * {@code time_format} is omitted, matching {@code setTime(time, null)}.
     */
    public static byte[] timeSync(long epochSeconds, int offsetQuarterHours) {
        ByteArrayOutputStream timeSet = new ByteArrayOutputStream();
        writeUint(timeSet, TIME_TIMESTAMP, (int) epochSeconds, true);
        writeSint32(timeSet, TIME_OFFSET, offsetQuarterHours);
        ByteArrayOutputStream systemTime = new ByteArrayOutputStream();
        SeWear.writeMessage(systemTime, SYSTEM_TIME_SET, timeSet.toByteArray());
        return SeWear.wearMessage(FUNCTION_SET_TIME, WEAR_SYSTEM_TIME, systemTime.toByteArray());
    }

    /** {@code SEWear{id=68, user_settings{user_profile{height, weight, age, sex}}}}. */
    public static byte[] userProfile(int heightCm, float weightKg, int age, boolean male) {
        ByteArrayOutputStream profile = new ByteArrayOutputStream();
        writeUint(profile, PROFILE_HEIGHT, heightCm, true);
        writeFloat(profile, PROFILE_WEIGHT, weightKg);
        writeUint(profile, PROFILE_AGE, age, true);
        writeUint(profile, PROFILE_SEX, male ? SEX_MALE : SEX_FEMALE, true);
        ByteArrayOutputStream settings = new ByteArrayOutputStream();
        SeWear.writeMessage(settings, USER_PROFILE, profile.toByteArray());
        return SeWear.wearMessage(FUNCTION_SET_USER_PROFILE, WEAR_USER_SETTINGS,
                settings.toByteArray());
    }

    /**
     * {@code SEWear{id=215, setting_menu{heart_rate_monitor{...}}}}. The vendor writes all eight
     * fields on every call, so an all-day request is exactly the same shape as its own toggle.
     */
    public static byte[] heartRateMonitor(boolean on) {
        ByteArrayOutputStream monitor = new ByteArrayOutputStream();
        writeUint(monitor, HR_MODE, on ? HR_MODE_AUTO : HR_MODE_OFF, true);
        writeUint(monitor, HR_FREQUENCY, 5, true);
        writeBool(monitor, HR_WARNING, false);
        writeUint(monitor, HR_WARNING_VALUE, 0, true);
        writeBool(monitor, HR_SPORT_WARNING, false);
        writeUint(monitor, HR_SPORT_WARNING_VALUE, 0, true);
        writeUint(monitor, HR_CONTINUOUS_MODE,
                on ? HR_CONTINUOUS_ALL_DAY : HR_CONTINUOUS_INTELLIGENT, true);
        writeUint(monitor, HR_LOW_WARNING_VALUE, 0, true);
        ByteArrayOutputStream menu = new ByteArrayOutputStream();
        SeWear.writeMessage(menu, MENU_HEART_RATE_MONITOR, monitor.toByteArray());
        return SeWear.wearMessage(FUNCTION_SET_HEART_RATE_MONITOR, WEAR_SETTING_MENU,
                menu.toByteArray());
    }

    /**
     * {@code SEWear{id=218, setting_menu{sedentary_reminder{...}}}}. The vendor sends the interval
     * in <b>seconds</b> ({@code sedentaryData.getInterval() * 60}) and always enables the
     * lunch-break quiet window.
     */
    public static byte[] sedentary(boolean on, int intervalSeconds,
                                   int startHour, int startMinute,
                                   int endHour, int endMinute) {
        ByteArrayOutputStream sedentary = new ByteArrayOutputStream();
        writeBool(sedentary, SEDENTARY_ON, on);
        SeWear.writeMessage(sedentary, SEDENTARY_START, settingTime(startHour, startMinute));
        SeWear.writeMessage(sedentary, SEDENTARY_END, settingTime(endHour, endMinute));
        writeUint(sedentary, SEDENTARY_FREQUENCY, intervalSeconds, true);
        writeBool(sedentary, SEDENTARY_NO_DISTURB, true);
        ByteArrayOutputStream menu = new ByteArrayOutputStream();
        SeWear.writeMessage(menu, MENU_SEDENTARY, sedentary.toByteArray());
        return SeWear.wearMessage(FUNCTION_SET_SEDENTARY, WEAR_SETTING_MENU, menu.toByteArray());
    }

    /** {@code SEWear{id=236, setting_menu{power_saving{on}}}}. */
    public static byte[] powerSaving(boolean on) {
        ByteArrayOutputStream saving = new ByteArrayOutputStream();
        writeBool(saving, POWER_SAVING_ON, on);
        ByteArrayOutputStream menu = new ByteArrayOutputStream();
        SeWear.writeMessage(menu, MENU_POWER_SAVING, saving.toByteArray());
        return SeWear.wearMessage(FUNCTION_SET_POWER_SAVING, WEAR_SETTING_MENU, menu.toByteArray());
    }

    /**
     * {@code SEWear{id=731, setting_menu{real_time_heart_rate_settings{switch, frequency,
     * auto_shutdown}}}}. The watch streams {@code SERealTimeHeartRateData} (id 733) while this is
     * on; the vendor enables it only while its heart-rate screen is visible.
     */
    public static byte[] realTimeHeartRate(boolean on, int frequencySeconds, int autoShutdownSeconds) {
        ByteArrayOutputStream realTime = new ByteArrayOutputStream();
        writeBool(realTime, RT_SWITCH, on);
        writeUint(realTime, RT_FREQUENCY, frequencySeconds, true);
        writeUint(realTime, RT_AUTO_SHUTDOWN, autoShutdownSeconds, true);
        ByteArrayOutputStream menu = new ByteArrayOutputStream();
        SeWear.writeMessage(menu, MENU_REAL_TIME_SETTINGS, realTime.toByteArray());
        return SeWear.wearMessage(FUNCTION_SET_REAL_TIME_HEART_RATE, WEAR_SETTING_MENU,
                menu.toByteArray());
    }

    /** {@code SEWear{id=161}} — the watch vibrates; there is no reply payload. */
    public static byte[] findWear() {
        return SeWear.wear(FUNCTION_FIND_WEAR);
    }

    /** {@code SEWear{id=214}} — read the heart-rate monitor settings. */
    public static byte[] requestHeartRateMonitor() {
        return SeWear.wear(FUNCTION_GET_HEART_RATE_MONITOR);
    }

    /** {@code SEWear{id=217}} — read the sedentary reminder. */
    public static byte[] requestSedentary() {
        return SeWear.wear(FUNCTION_GET_SEDENTARY);
    }

    /** {@code SEWear{id=235}} — read the battery-saver state. */
    public static byte[] requestPowerSaving() {
        return SeWear.wear(FUNCTION_GET_POWER_SAVING);
    }

    /** {@code SEWear{id=730}} — read the real-time heart-rate config. */
    public static byte[] requestRealTimeHeartRate() {
        return SeWear.wear(FUNCTION_GET_REAL_TIME_HEART_RATE);
    }

    /** {@code SEWear{id=32}} — firmware, serial and MAC. */
    public static byte[] deviceInfoRequest() {
        return SeWear.wear(FUNCTION_DEVICE_INFO);
    }

    /** {@code SEWear{id=33}} — battery percentage. */
    public static byte[] batteryRequest() {
        return SeWear.wear(FUNCTION_DEVICE_BATTERY);
    }

    /**
     * {@code SEWear{id=80}} — the vendor's {@code getWatchFaceList()} sends this bare frame
     * ({@code a.g() == a(80, null)}); the reply carries a {@code SEWatchFaceItem.SEList}.
     */
    public static byte[] requestWatchFaceList() {
        return SeWear.wear(FUNCTION_GET_WATCH_FACE_LIST);
    }

    /**
     * {@code SEWear{id=85, watch_face{watch_face_config{id}}}} — the vendor's
     * {@code getDiyWatchFaceConfig(id)} builds exactly this nested form ({@code a.a(str, 85)}).
     */
    public static byte[] requestWatchFaceConfig(String id) {
        ByteArrayOutputStream config = new ByteArrayOutputStream();
        writeString(config, WATCH_FACE_CONFIG_ID, id == null ? "" : id);
        ByteArrayOutputStream face = new ByteArrayOutputStream();
        SeWear.writeMessage(face, WATCH_FACE_CONFIG, config.toByteArray());
        return SeWear.wearMessage(FUNCTION_REQUEST_DEVICE_WATCH_FACE_CONFIG, WEAR_WATCH_FACE,
                face.toByteArray());
    }

    // ------------------------------------------------------------------------------------
    // Parsers
    // ------------------------------------------------------------------------------------

    /** Battery percentage from an id-33 (or id-32) reply; -1 when absent. */
    public static int batteryLevel(byte[] sewear) {
        byte[] device = SeWear.readMessageField(sewear, WEAR_DEVICE);
        if (device == null) return -1;
        byte[] battery = SeWear.readMessageField(device, DEVICE_BATTERY);
        if (battery == null) {
            byte[] info = SeWear.readMessageField(device, DEVICE_INFO);
            if (info != null) battery = SeWear.readMessageField(info, INFO_BATTERY);
        }
        return battery == null ? -1 : (int) SeWear.readVarintField(battery, BATTERY_CAPACITY);
    }

    /** Decoded id-32 reply; absent strings are empty and {@code battery} is -1. */
    public static DeviceInfo deviceInfo(byte[] sewear) {
        byte[] device = SeWear.readMessageField(sewear, WEAR_DEVICE);
        byte[] info = device == null ? null : SeWear.readMessageField(device, DEVICE_INFO);
        if (info == null) return null;
        byte[] battery = SeWear.readMessageField(info, INFO_BATTERY);
        return new DeviceInfo(
                stringOrEmpty(info, INFO_FIRMWARE),
                stringOrEmpty(info, INFO_EQUIPMENT_NUMBER),
                stringOrEmpty(info, INFO_MAC),
                stringOrEmpty(info, INFO_SERIAL),
                battery == null ? -1 : (int) SeWear.readVarintField(battery, BATTERY_CAPACITY));
    }

    /**
     * Decodes an id-80 reply into the watch's face list, in the order the watch reported it.
     * An absent or malformed {@code item_list} yields an empty list rather than an exception,
     * because the face screen must stay usable while the protocol is still being verified.
     */
    public static List<WatchFace> watchFaceList(byte[] sewear) {
        List<WatchFace> faces = new ArrayList<>();
        byte[] face = SeWear.readMessageField(sewear, WEAR_WATCH_FACE);
        if (face == null) return faces;
        byte[] list = SeWear.readMessageField(face, WATCH_FACE_ITEM_LIST);
        if (list == null) return faces;
        for (byte[] raw : SeWear.readMessages(list, WATCH_FACE_ITEM)) {
            faces.add(new WatchFace(
                    stringOrEmpty(raw, WATCH_FACE_ITEM_ID),
                    SeWear.readVarintField(raw, WATCH_FACE_ITEM_IS_CURRENT) > 0,
                    SeWear.readVarintField(raw, WATCH_FACE_ITEM_IS_REMOVE) > 0));
        }
        return faces;
    }

    /**
     * Decodes the id-85 reply. Only {@code id} and {@code rule_count} are read for now; the file
     * configs (fields 3-8) are left untouched until a real device reply pins their semantics.
     * Returns {@code null} when the reply carries no {@code watch_face_config}.
     */
    public static WatchFaceConfig watchFaceConfig(byte[] sewear) {
        byte[] face = SeWear.readMessageField(sewear, WEAR_WATCH_FACE);
        byte[] config = face == null ? null : SeWear.readMessageField(face, WATCH_FACE_CONFIG);
        if (config == null) return null;
        long rules = SeWear.readVarintField(config, WATCH_FACE_CONFIG_RULE_COUNT);
        return new WatchFaceConfig(
                stringOrEmpty(config, WATCH_FACE_CONFIG_ID),
                rules < 0 ? -1 : (int) rules);
    }

    /** True when the reply turns the all-day heart-rate monitor on. */
    public static Boolean heartRateMonitorOn(byte[] sewear) {
        byte[] menu = settingMenu(sewear);
        byte[] monitor = menu == null ? null : SeWear.readMessageField(menu, MENU_HEART_RATE_MONITOR);
        if (monitor == null) return null;
        long mode = SeWear.readVarintField(monitor, HR_MODE);
        return mode >= 0 && mode != HR_MODE_OFF;
    }

    /** True when the continuous mode is the intelligent one rather than all-day. */
    public static Boolean intelligentHeartRate(byte[] sewear) {
        byte[] menu = settingMenu(sewear);
        byte[] monitor = menu == null ? null : SeWear.readMessageField(menu, MENU_HEART_RATE_MONITOR);
        if (monitor == null) return null;
        long mode = SeWear.readVarintField(monitor, HR_CONTINUOUS_MODE);
        return mode >= 0 && mode == HR_CONTINUOUS_INTELLIGENT;
    }

    /** Decoded sedentary reminder; null when the reply carries none. */
    public static Sedentary sedentary(byte[] sewear) {
        byte[] menu = settingMenu(sewear);
        byte[] reminder = menu == null ? null : SeWear.readMessageField(menu, MENU_SEDENTARY);
        if (reminder == null) return null;
        byte[] start = SeWear.readMessageField(reminder, SEDENTARY_START);
        byte[] end = SeWear.readMessageField(reminder, SEDENTARY_END);
        long rawFrequency = SeWear.readVarintField(reminder, SEDENTARY_FREQUENCY);
        return new Sedentary(
                SeWear.readVarintField(reminder, SEDENTARY_ON) > 0,
                rawFrequency <= 0 ? -1 : (int) (rawFrequency / 60),
                start == null ? -1 : (int) SeWear.readVarintField(start, SETTING_TIME_HOUR),
                start == null ? -1 : (int) SeWear.readVarintField(start, SETTING_TIME_MINUTE),
                end == null ? -1 : (int) SeWear.readVarintField(end, SETTING_TIME_HOUR),
                end == null ? -1 : (int) SeWear.readVarintField(end, SETTING_TIME_MINUTE));
    }

    /** Battery-saver state from an id-235/236 reply; null when absent. */
    public static Boolean powerSavingOn(byte[] sewear) {
        byte[] menu = settingMenu(sewear);
        byte[] saving = menu == null ? null : SeWear.readMessageField(menu, MENU_POWER_SAVING);
        if (saving == null) return null;
        return SeWear.readVarintField(saving, POWER_SAVING_ON) > 0;
    }

    /** Real-time heart-rate settings from an id-730/731 reply; null when absent. */
    public static RealTimeSettings realTimeSettings(byte[] sewear) {
        byte[] menu = settingMenu(sewear);
        byte[] realTime = menu == null ? null
                : SeWear.readMessageField(menu, MENU_REAL_TIME_SETTINGS);
        if (realTime == null) return null;
        return new RealTimeSettings(
                SeWear.readVarintField(realTime, RT_SWITCH) > 0,
                (int) SeWear.readVarintField(realTime, RT_FREQUENCY),
                (int) SeWear.readVarintField(realTime, RT_AUTO_SHUTDOWN));
    }

    /** A streamed real-time heart-rate sample (id 733); {@code bpm} is -1 when absent. */
    public static RealTimeSample realTimeSample(byte[] sewear) {
        byte[] menu = settingMenu(sewear);
        byte[] data = menu == null ? null : SeWear.readMessageField(menu, MENU_REAL_TIME_DATA);
        if (data == null) return null;
        return new RealTimeSample(
                SeWear.readVarintField(data, RT_DATA_TIMESTAMP),
                (int) SeWear.readVarintField(data, RT_DATA_VALUE));
    }

    /** One-line, log-safe summary of a command reply. */
    public static String describe(byte[] sewear) {
        if (sewear == null) return "null";
        int id = SeWear.readId(sewear);
        StringBuilder out = new StringBuilder("SEWear{id=").append(id)
                .append(", error=").append(SeWear.readErrorCode(sewear));
        if (id == FUNCTION_DEVICE_INFO) {
            DeviceInfo info = deviceInfo(sewear);
            if (info != null) out.append(", fw=").append(info.firmware)
                    .append(", serial=").append(info.serialNumber)
                    .append(", battery=").append(info.battery);
        } else if (id == FUNCTION_DEVICE_BATTERY) {
            out.append(", battery=").append(batteryLevel(sewear));
        } else if (id == FUNCTION_GET_WATCH_FACE_LIST) {
            List<WatchFace> faces = watchFaceList(sewear);
            out.append(", faces=").append(faces.size());
            for (WatchFace face : faces) {
                if (face.current) out.append(", current=").append(face.id);
            }
        } else if (id == FUNCTION_REQUEST_DEVICE_WATCH_FACE_CONFIG) {
            WatchFaceConfig config = watchFaceConfig(sewear);
            if (config != null) out.append(", face=").append(config.id)
                    .append(", rules=").append(config.ruleCount);
        } else if (id == FUNCTION_GET_HEART_RATE_MONITOR
                || id == FUNCTION_SET_HEART_RATE_MONITOR) {
            out.append(", hrOn=").append(heartRateMonitorOn(sewear));
        } else if (id == FUNCTION_GET_SEDENTARY || id == FUNCTION_SET_SEDENTARY) {
            Sedentary sedentary = sedentary(sewear);
            if (sedentary != null) out.append(", ").append(sedentary);
        } else if (id == FUNCTION_GET_POWER_SAVING || id == FUNCTION_SET_POWER_SAVING) {
            out.append(", powerSaving=").append(powerSavingOn(sewear));
        } else if (id == FUNCTION_GET_REAL_TIME_HEART_RATE
                || id == FUNCTION_SET_REAL_TIME_HEART_RATE) {
            out.append(", ").append(realTimeSettings(sewear));
        } else if (id == FUNCTION_REPORT_REAL_TIME_HEART_RATE) {
            out.append(", ").append(realTimeSample(sewear));
        }
        return out.append('}').toString();
    }

    public static boolean isCommandReply(int id) {
        return id == FUNCTION_DEVICE_INFO
                || id == FUNCTION_DEVICE_BATTERY
                || id == FUNCTION_SET_TIME
                || id == FUNCTION_SET_USER_PROFILE
                || id == FUNCTION_GET_WATCH_FACE_LIST
                || id == FUNCTION_REQUEST_DEVICE_WATCH_FACE_CONFIG
                || id == FUNCTION_GET_HEART_RATE_MONITOR
                || id == FUNCTION_SET_HEART_RATE_MONITOR
                || id == FUNCTION_GET_SEDENTARY
                || id == FUNCTION_SET_SEDENTARY
                || id == FUNCTION_GET_POWER_SAVING
                || id == FUNCTION_SET_POWER_SAVING
                || id == FUNCTION_GET_REAL_TIME_HEART_RATE
                || id == FUNCTION_SET_REAL_TIME_HEART_RATE
                || id == FUNCTION_REPORT_REAL_TIME_HEART_RATE;
    }

    // ------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------

    private static byte[] settingTime(int hour, int minute) {
        ByteArrayOutputStream time = new ByteArrayOutputStream();
        writeUint(time, SETTING_TIME_HOUR, hour, true);
        writeUint(time, SETTING_TIME_MINUTE, minute, true);
        writeUint(time, SETTING_TIME_SECOND, 0, true);
        writeUint(time, SETTING_TIME_MILLIS, 0, true);
        return time.toByteArray();
    }

    private static byte[] settingMenu(byte[] sewear) {
        return SeWear.readMessageField(sewear, WEAR_SETTING_MENU);
    }

    private static String stringOrEmpty(byte[] message, int field) {
        String value = SeWear.readStringField(message, field);
        return value == null ? "" : value;
    }

    /** Writes a varint field even when the value is zero (proto2 explicit presence). */
    private static void writeUint(ByteArrayOutputStream out, int field, int value,
                                  boolean always) {
        if (!always && value == 0) return;
        SeWear.writeTag(out, field, 0);
        SeWear.writeVarint(out, value);
    }

    private static void writeBool(ByteArrayOutputStream out, int field, boolean value) {
        SeWear.writeTag(out, field, 0);
        SeWear.writeVarint(out, value ? 1 : 0);
    }

    private static void writeSint32(ByteArrayOutputStream out, int field, int value) {
        SeWear.writeTag(out, field, 0);
        SeWear.writeVarint(out, (value << 1) ^ (value >> 31));
    }

    private static void writeFloat(ByteArrayOutputStream out, int field, float value) {
        SeWear.writeTag(out, field, 5);
        int bits = Float.floatToIntBits(value);
        out.write(bits & 0xff);
        out.write((bits >>> 8) & 0xff);
        out.write((bits >>> 16) & 0xff);
        out.write((bits >>> 24) & 0xff);
    }

    private static void writeString(ByteArrayOutputStream out, int field, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        SeWear.writeTag(out, field, 2);
        SeWear.writeVarint(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    // ------------------------------------------------------------------------------------
    // Value types
    // ------------------------------------------------------------------------------------

    public static final class DeviceInfo {
        public final String firmware;
        public final String equipmentNumber;
        public final String mac;
        public final String serialNumber;
        public final int battery;

        DeviceInfo(String firmware, String equipmentNumber, String mac, String serialNumber,
                   int battery) {
            this.firmware = firmware;
            this.equipmentNumber = equipmentNumber;
            this.mac = mac;
            this.serialNumber = serialNumber;
            this.battery = battery;
        }
    }

    public static final class Sedentary {
        public final boolean on;
        public final int periodMinutes;
        public final int startHour;
        public final int startMinute;
        public final int endHour;
        public final int endMinute;

        Sedentary(boolean on, int periodMinutes, int startHour, int startMinute,
                  int endHour, int endMinute) {
            this.on = on;
            this.periodMinutes = periodMinutes;
            this.startHour = startHour;
            this.startMinute = startMinute;
            this.endHour = endHour;
            this.endMinute = endMinute;
        }

        @Override public String toString() {
            return "sedentary{on=" + on + ", every=" + periodMinutes + "m, "
                    + startHour + ":" + String.format(java.util.Locale.US, "%02d", startMinute)
                    + "-" + endHour + ":" + String.format(java.util.Locale.US, "%02d", endMinute)
                    + "}";
        }
    }

    public static final class RealTimeSettings {
        public final boolean on;
        public final int frequencySeconds;
        public final int autoShutdownSeconds;

        RealTimeSettings(boolean on, int frequencySeconds, int autoShutdownSeconds) {
            this.on = on;
            this.frequencySeconds = frequencySeconds;
            this.autoShutdownSeconds = autoShutdownSeconds;
        }

        @Override public String toString() {
            return "realTimeHr{on=" + on + ", every=" + frequencySeconds + "s}";
        }
    }

    public static final class RealTimeSample {
        public final long timestamp;
        public final int bpm;

        RealTimeSample(long timestamp, int bpm) {
            this.timestamp = timestamp;
            this.bpm = bpm;
        }

        @Override public String toString() {
            return "bpm=" + bpm + " @" + timestamp;
        }
    }

    /** One entry of the watch's face list (id 80). {@code id} is the vendor's opaque string. */
    public static final class WatchFace {
        public final String id;
        public final boolean current;
        public final boolean removable;

        WatchFace(String id, boolean current, boolean removable) {
            this.id = id;
            this.current = current;
            this.removable = removable;
        }

        @Override public String toString() {
            StringBuilder out = new StringBuilder("face{id=").append(id);
            if (current) out.append(", current");
            if (removable) out.append(", removable");
            return out.append('}').toString();
        }
    }

    /** The subset of {@code SEWatchFaceConfig} decoded from an id-85 reply. */
    public static final class WatchFaceConfig {
        public final String id;
        public final int ruleCount;

        WatchFaceConfig(String id, int ruleCount) {
            this.id = id;
            this.ruleCount = ruleCount;
        }

        @Override public String toString() {
            return "faceConfig{id=" + id + ", rules=" + ruleCount + "}";
        }
    }
}