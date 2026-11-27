package com.openfit.app.protocol;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ZhBraceletBindingTest {
    private static final byte[] BIND_TYPE_0 = {
        (byte) 0xAB, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x6C, 0x00, 0x01, 0x00
    };

    private static final byte[] BIND_TYPE_1 = {
        (byte) 0xAB, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x00,
        0x01, 0x00, 0x6C, 0x00, 0x01, 0x01
    };

    @Test public void bindRequestMatchesTheVendorSdkFrame() {
        // com.zjw.zhbraceletsdk a.b(type) with key 108 (0x6C) and a one-byte payload.
        assertArrayEquals(BIND_TYPE_0, ZhBraceletBinding.bindRequest(ZhBraceletBinding.TYPE_BIND));
        assertArrayEquals(BIND_TYPE_1, ZhBraceletBinding.bindRequest(ZhBraceletBinding.TYPE_INQUIRY));
    }

    @Test public void declaredLengthFollowsTheHeaderField() {
        assertEquals(14, ZhBraceletBinding.declaredLength(BIND_TYPE_0));
        assertEquals(-1, ZhBraceletBinding.declaredLength(new byte[] {0x00, 0x00, 0x00, 0x00}));
    }

    @Test public void parseBindInfoAcceptsAResultFrame() {
        byte[] reply = {
            (byte) 0xAB, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x00,
            0x03, 0x00, 0x34, 0x00, 0x01, 0x00, 0x01
        };
        ZhBraceletBinding.BindInfo info = ZhBraceletBinding.parseBindInfo(reply);
        assertNotNull(info);
        assertEquals(0, info.type);
        assertEquals(1, info.result);
        assertTrue(info.accepted());
    }

    @Test public void parseBindInfoAcceptsTheObservedDeviceReply() {
        // 2026-11-18 11:49, ColorFit Ultra Buzz, build b701e4a: the watch accepted the bind.
        byte[] reply = {
            (byte) 0xAB, 0x00, 0x00, 0x07, (byte) 0x97, 0x00, 0x00, 0x4E,
            0x03, 0x00, 0x34, 0x00, 0x02, 0x00, 0x01
        };
        assertEquals(15, ZhBraceletBinding.declaredLength(reply));
        ZhBraceletBinding.BindInfo info = ZhBraceletBinding.parseBindInfo(reply);
        assertNotNull(info);
        assertEquals(ZhBraceletBinding.TYPE_BIND, info.type);
        assertEquals(1, info.result);
        assertTrue(info.accepted());
    }

    @Test public void parseBindInfoRejectsOtherFrames() {
        assertNull(ZhBraceletBinding.parseBindInfo(BIND_TYPE_0)); // phone-to-watch echo
        byte[] otherKey = {
            (byte) 0xAB, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x00,
            0x03, 0x00, 0x08, 0x00, 0x01, 0x00, 0x01
        };
        assertNull(ZhBraceletBinding.parseBindInfo(otherKey));
    }

    @Test public void readerReassemblesSplitFrames() {
        ZhFrameReader reader = new ZhFrameReader();
        assertTrue(reader.append(new byte[] {BIND_TYPE_0[0], BIND_TYPE_0[1], BIND_TYPE_0[2]}).isEmpty());
        List<byte[]> frames = reader.append(java.util.Arrays.copyOfRange(BIND_TYPE_0, 3, BIND_TYPE_0.length));
        assertEquals(1, frames.size());
        assertArrayEquals(BIND_TYPE_0, frames.get(0));
    }

    @Test public void readerResynchronisesAfterGarbage() {
        ZhFrameReader reader = new ZhFrameReader();
        byte[] noisy = new byte[BIND_TYPE_0.length + 2];
        noisy[0] = 0x12;
        noisy[1] = 0x34;
        System.arraycopy(BIND_TYPE_0, 0, noisy, 2, BIND_TYPE_0.length);
        List<byte[]> frames = reader.append(noisy);
        assertEquals(1, frames.size());
        assertArrayEquals(BIND_TYPE_0, frames.get(0));
        assertFalse(ZhBraceletBinding.parseBindInfo(frames.get(0)) != null);
    }

    @Test public void timeSyncMatchesTheVendorSdkFrame() {
        // com.zjw.zhbraceletsdk a.B() for 2026-11-18 12:34:56, key 1, four packed bytes.
        byte[] expected = {
            (byte) 0xAB, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x01, 0x00, 0x04,
            0x6A, (byte) 0xE4, (byte) 0xC8, (byte) 0xB8
        };
        assertArrayEquals(expected,
                ZhBraceletBinding.timeSyncFrame(2026, 11, 18, 12, 34, 56));
        assertEquals(expected.length, ZhBraceletBinding.declaredLength(expected));
    }

    @Test public void timeSyncRejectsInvalidClockFields() {
        try {
            ZhBraceletBinding.timeSyncFrame(2026, 13, 1, 0, 0, 0);
            fail("month 13 must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            ZhBraceletBinding.timeSyncFrame(2026, 1, 1, 24, 0, 0);
            fail("hour 24 must be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test public void userInfoMatchesTheVendorSdkFrame() {
        // com.zjw.zhbraceletsdk a.a(UserInfo): key 4, sex bit 7 clear for a male profile.
        byte[] male = {
            (byte) 0xAB, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x04, 0x00, 0x04,
            0x1E, (byte) 0xAF, 0x46, 0x00
        };
        assertArrayEquals(male, ZhBraceletBinding.userInfoFrame(30, 175, 70, true));

        byte[] female = male.clone();
        female[13] = (byte) 0x9C; // !getSex() → bit 7 set, age 28
        assertArrayEquals(female, ZhBraceletBinding.userInfoFrame(28, 175, 70, false));
    }

    @Test public void userInfoClampsHeightAndWeightLikeTheSdk() {
        byte[] low = ZhBraceletBinding.userInfoFrame(30, 0, 0, true);
        assertEquals(1, low[14] & 0xFF);
        assertEquals(1, low[15] & 0xFF);

        byte[] high = ZhBraceletBinding.userInfoFrame(30, 300, 300, true);
        assertEquals(255, high[14] & 0xFF);
        assertEquals(255, high[15] & 0xFF);
    }

    @Test public void parseAckReadsTheBuzzCommandAcks() {
        // 2026-11-18 17:45:39, ColorFit Ultra Buzz: key 0x42 answers the clock sync (1) and the
        // user-info profile (4) with payload [command key] 00 01.
        ZhBraceletBinding.Ack timeSync = ZhBraceletBinding.parseAck(
                hex("AB0000089700004E0300420003010001"));
        assertNotNull(timeSync);
        assertEquals(ZhBraceletBinding.KEY_COMMAND_ACK, timeSync.key);
        assertEquals(ZhBraceletBinding.KEY_TIME_SYNC, timeSync.refersTo);
        assertTrue(timeSync.success);
        assertTrue(timeSync.describe().contains("time sync"));

        ZhBraceletBinding.Ack userInfo = ZhBraceletBinding.parseAck(
                hex("AB0000089700004E0300420003040001"));
        assertNotNull(userInfo);
        assertEquals(ZhBraceletBinding.KEY_USER_INFO, userInfo.refersTo);
        assertTrue(userInfo.success);
    }

    @Test public void keyLabelsNameTheRecoveredCommands() {
        assertEquals("time sync", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_TIME_SYNC));
        assertEquals("user info", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_USER_INFO));
        assertEquals("status", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_STATUS));
        assertEquals("bind info", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_BIND_INFO));
        assertEquals("command ack", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_COMMAND_ACK));
        assertEquals("heart rate", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_HEART_RATE));
        assertEquals("measurement", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_MEASUREMENT));
        assertEquals("continuous HR", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_CONTINUOUS_HR));
        assertEquals("private feature", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_PRIVATE_FEATURE));
        assertEquals("unit / device info", ZhBraceletBinding.keyLabel(0x08));
    }

    @Test public void parseAckReadsStatusAndDeviceAnswerFrames() {
        byte[] status = {
            (byte) 0xAB, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x00,
            0x03, 0x00, 0x0F, 0x00, 0x01, 0x01
        };
        ZhBraceletBinding.Ack statusAck = ZhBraceletBinding.parseAck(status);
        assertNotNull(statusAck);
        assertTrue(statusAck.success);
        assertEquals(-1, statusAck.refersTo);

        byte[] answer = {
            (byte) 0xAB, 0x00, 0x00, 0x07, 0x00, 0x00, 0x00, 0x00,
            0x03, 0x00, (byte) 0xAC, 0x00, 0x02, 0x01, 0x01
        };
        ZhBraceletBinding.Ack answerAck = ZhBraceletBinding.parseAck(answer);
        assertNotNull(answerAck);
        assertTrue(answerAck.success);
        assertEquals(ZhBraceletBinding.KEY_TIME_SYNC, answerAck.refersTo);

        assertNull(ZhBraceletBinding.parseAck(BIND_TYPE_0)); // phone-to-watch echo
    }

    @Test public void measurementFramesMatchTheVendorSdk() {
        // com.zjw.zhbraceletsdk a.u(): openMeasurement(), key 0x19 payload 1.
        byte[] start = {
            (byte) 0xAB, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x19, 0x00, 0x01, 0x01
        };
        assertArrayEquals(start, ZhBraceletBinding.measurementFrame(true));
        assertEquals(start.length, ZhBraceletBinding.declaredLength(start));

        // a.b(): closeMeasurement() only clears the payload byte.
        byte[] stop = start.clone();
        stop[13] = 0x00;
        assertArrayEquals(stop, ZhBraceletBinding.measurementFrame(false));
    }

    @Test public void continuousHeartRateFrameMatchesTheVendorSdk() {
        // com.zjw.zhbraceletsdk a.j(boolean), key 41 (0x29), one payload byte.
        byte[] on = {
            (byte) 0xAB, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x29, 0x00, 0x01, 0x01
        };
        assertArrayEquals(on, ZhBraceletBinding.continuousHeartRateFrame(true));

        byte[] off = on.clone();
        off[13] = 0x00;
        assertArrayEquals(off, ZhBraceletBinding.continuousHeartRateFrame(false));
    }

    @Test public void parseHeartRateReadsTheFirstPayloadByte() {
        // Watch reply for key 11: direction 3, one payload byte carrying the BPM.
        byte[] reply = {
            (byte) 0xAB, 0x00, 0x00, 0x06, (byte) 0x97, 0x00, 0x00, 0x4E,
            0x03, 0x00, 0x0B, 0x00, 0x01, 0x48
        };
        assertEquals(72, ZhBraceletBinding.parseHeartRate(reply));
    }

    @Test public void parseHeartRateRejectsOtherFramesAndEmptyReadings() {
        assertTrue(ZhBraceletBinding.parseHeartRate(null) < 0);
        assertTrue(ZhBraceletBinding.parseHeartRate(BIND_TYPE_0) < 0); // phone-to-watch
        assertTrue(ZhBraceletBinding.parseHeartRate(new byte[] {(byte) 0xAB}) < 0);

        byte[] zero = {
            (byte) 0xAB, 0x00, 0x00, 0x06, (byte) 0x97, 0x00, 0x00, 0x4E,
            0x03, 0x00, 0x0B, 0x00, 0x01, 0x00
        };
        assertTrue("0 bpm is not a reading", ZhBraceletBinding.parseHeartRate(zero) < 0);

        byte[] otherKey = zero.clone();
        otherKey[10] = ZhBraceletBinding.KEY_STATUS;
        otherKey[13] = 0x48;
        assertTrue(ZhBraceletBinding.parseHeartRate(otherKey) < 0);
    }

    @Test public void parseHeartRateHistoryDecodesTheDailyArray() {
        byte[] frame = heartHistoryFrame();
        frame[17 + 10] = (byte) 0x5F;  // 95 bpm at 00:50
        frame[17 + 287] = (byte) 0x52; // 82 bpm, most recent slot
        ZhBraceletBinding.HeartRateHistory history =
                ZhBraceletBinding.parseHeartRateHistory(frame);
        assertNotNull(history);
        assertEquals("2026-11-19", history.date);
        assertEquals(82, history.recent);
        assertEquals(95, history.dayMax);
        assertEquals(82, history.dayMin);
        assertEquals(88, history.dayAverage);
        assertEquals(2, history.sampleCount);
        assertArrayEquals(new int[] {95, 82}, history.samples);
        // The graph needs slot positions, not just the nonzero values.
        assertEquals(288, history.slots.length);
        assertEquals(95, history.slots[10]);
        assertEquals(82, history.slots[287]);
        assertEquals(0, history.slots[0]);
    }

    @Test public void parseHeartRateHistoryRejectsBadFrames() {
        assertNull(ZhBraceletBinding.parseHeartRateHistory(null));
        assertNull(ZhBraceletBinding.parseHeartRateHistory(new byte[] {(byte) 0xAB}));

        byte[] shortFrame = java.util.Arrays.copyOf(heartHistoryFrame(), 100);
        assertNull(ZhBraceletBinding.parseHeartRateHistory(shortFrame));

        byte[] wrongKey = heartHistoryFrame();
        wrongKey[10] = (byte) ZhBraceletBinding.KEY_HEART_RATE;
        assertNull(ZhBraceletBinding.parseHeartRateHistory(wrongKey));

        // The vendor drops the array when any nonzero sample is below 45 bpm.
        byte[] implausible = heartHistoryFrame();
        implausible[17] = 44;
        assertNull(ZhBraceletBinding.parseHeartRateHistory(implausible));

        byte[] empty = heartHistoryFrame();
        ZhBraceletBinding.HeartRateHistory history =
                ZhBraceletBinding.parseHeartRateHistory(empty);
        assertNotNull(history);
        assertEquals(0, history.recent);
        assertEquals(0, history.sampleCount);
    }

    private static byte[] heartHistoryFrame() {
        byte[] frame = new byte[305];
        frame[0] = (byte) 0xAB;
        frame[2] = 0x01;
        frame[3] = 0x29;
        frame[8] = (byte) ZhBraceletBinding.DIRECTION_WATCH;
        frame[10] = (byte) ZhBraceletBinding.KEY_HEART_RATE_HISTORY;
        frame[11] = 0x01;
        frame[12] = 0x24; // 292-byte payload
        frame[13] = 0x35; // 2026
        frame[14] = 0x73; // November, day 19
        return frame;
    }

    @Test public void oneByteSettingsUseTheVendorKeyAndValue() {
        assertArrayEquals(hex("AB000006000000000100200001" + "01"),
                ZhBraceletBinding.toggleFrame(ZhBraceletBinding.KEY_DND, true));
        assertArrayEquals(hex("AB0000060000000001000A0001" + "00"),
                ZhBraceletBinding.toggleFrame(ZhBraceletBinding.KEY_BRIGHT_SCREEN, false));
        assertArrayEquals(hex("AB000006000000000100090001" + "01"),
                ZhBraceletBinding.toggleFrame(ZhBraceletBinding.KEY_TIME_FORMAT, true));
        assertArrayEquals(hex("AB000006000000000100080001" + "00"),
                ZhBraceletBinding.toggleFrame(ZhBraceletBinding.KEY_UNIT, false));
    }

    @Test public void findWatchSetsTheSessionFlagInByteNine() {
        byte[] frame = ZhBraceletBinding.findDeviceFrame();
        assertEquals(13, frame.length);
        assertEquals((byte) 0xAB, frame[0]);
        assertEquals(0x05, frame[3] & 0xFF);
        assertEquals(0x80, frame[9] & 0xFF);
        assertEquals(ZhBraceletBinding.KEY_FIND_DEVICE, frame[10] & 0xFF);
        assertEquals(0, frame[12] & 0xFF);
    }

    @Test public void privateFeatureFramesPutTheSelectorFirst() {
        assertArrayEquals(hex("AB0000080000000001007F0003" + "220101"),
                ZhBraceletBinding.privateToggleFrame(
                        ZhBraceletBinding.PRIVATE_BATTERY_SAVER, true));
        assertArrayEquals(hex("AB0000070000000001007F0002" + "0200"),
                ZhBraceletBinding.privateReadFrame(ZhBraceletBinding.PRIVATE_QUICK_EYE));
    }

    @Test public void sedentaryFrameRoundTripsThroughTheReplyParser() {
        byte[] write = ZhBraceletBinding.sedentaryFrame(true, 2, 9, 30, 18, 45);
        assertEquals(ZhBraceletBinding.KEY_SEDENTARY, write[10] & 0xFF);
        assertEquals(4, write[12] & 0xFF);
        byte[] payload = new byte[] {
            (byte) ZhBraceletBinding.PRIVATE_SEDENTARY, 0,
            write[13], write[14], write[15], write[16]
        };
        ZhBraceletBinding.SitReminder reminder =
                ZhBraceletBinding.parseSedentary(privateReply(payload));
        assertNotNull(reminder);
        assertTrue(reminder.enable);
        assertEquals(2, reminder.period);
        assertEquals(9, reminder.startHour);
        assertEquals(30, reminder.startMin);
        assertEquals(18, reminder.endHour);
        assertEquals(45, reminder.endMin);
    }

    @Test public void alarmFrameRoundTripsAndPicksTheNextOccurrence() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        now.clear();
        now.set(2026, java.util.Calendar.NOVEMBER, 27, 10, 0, 0);
        java.util.List<ZhBraceletBinding.Alarm> alarms = java.util.Arrays.asList(
                new ZhBraceletBinding.Alarm(0, 7, 30, 128),
                new ZhBraceletBinding.Alarm(1, 18, 0, 128));
        byte[] write = ZhBraceletBinding.alarmsFrame(alarms, now);
        assertEquals(ZhBraceletBinding.KEY_ALARM, write[10] & 0xFF);
        assertEquals(10, write[12] & 0xFF);
        // 07:30 has passed, so it lands tomorrow (2026-11-28); 18:00 is still today (2026-11-27).
        assertEquals(0x6A, write[13] & 0xFF);
        assertEquals(0xF8, write[14] & 0xFF);
        assertEquals(0x77, write[15] & 0xFF);
        assertEquals(0x80, write[16] & 0xFF);
        assertEquals(0x80, write[17] & 0xFF);
        assertEquals(0x6A, write[18] & 0xFF);
        assertEquals(0xF7, write[19] & 0xFF);
        assertEquals(0x20, write[20] & 0xFF);
        assertEquals(0x08, write[21] & 0xFF);

        byte[] records = new byte[10];
        System.arraycopy(write, 13, records, 0, 10);
        byte[] payload = new byte[12];
        payload[0] = (byte) ZhBraceletBinding.PRIVATE_ALARMS;
        payload[1] = 0;
        System.arraycopy(records, 0, payload, 2, 10);
        java.util.List<ZhBraceletBinding.Alarm> decoded =
                ZhBraceletBinding.parseAlarms(privateReply(payload));
        assertNotNull(decoded);
        assertEquals(2, decoded.size());
        assertEquals(7, decoded.get(0).hour);
        assertEquals(30, decoded.get(0).minute);
        assertFalse(decoded.get(0).repeats());
        assertEquals(18, decoded.get(1).hour);
        assertEquals(0, decoded.get(1).minute);
        assertEquals(1, decoded.get(1).id);
    }

    @Test public void deviceInfoDecodesBatteryAndFirmware() {
        byte[] payload = new byte[4];
        payload[0] = 87;    // battery
        payload[1] = 0x01;  // version number high byte -> N1xx
        payload[2] = 0x08;  // version tail -> 1.1.8
        byte[] frame = reply(ZhBraceletBinding.KEY_DEVICE_INFO, payload);
        ZhBraceletBinding.DeviceInfo info = ZhBraceletBinding.parseDeviceInfo(frame);
        assertNotNull(info);
        assertEquals(87, info.battery);
        assertEquals("N1.1.8", info.versionName);
    }

    @Test public void stepTargetIsBigEndian() {
        byte[] frame = ZhBraceletBinding.userTargetStepFrame(8000);
        assertEquals(ZhBraceletBinding.KEY_USER_TARGET_STEP, frame[10] & 0xFF);
        assertArrayEquals(new byte[] {0x00, 0x00, 0x1F, 0x40},
                new byte[] {frame[13], frame[14], frame[15], frame[16]});
    }

    // ---------------------------------------------------------------------------------------
    // Sensor history parsers
    // ---------------------------------------------------------------------------------------

    @Test public void parseStepsSumsTheBigEndianSlots() {
        byte[] frame = reply(ZhBraceletBinding.KEY_SPORT_DATA, new byte[] {
            0x35, 0x7B, 0x00, 0x03,
            0x00, 0x64,   // 100
            0x00, (byte) 0xC8, // 200
            0x01, 0x2C    // 300
        });
        ZhBraceletBinding.Steps steps = ZhBraceletBinding.parseSteps(frame);
        assertNotNull(steps);
        assertEquals("2026-11-27", steps.date);
        assertEquals(600, steps.total);
        assertEquals(3, steps.slots.length);
        assertEquals(300, steps.slots[2]);
    }

    @Test public void parseStepsIgnoresPhoneDirection() {
        byte[] frame = reply(ZhBraceletBinding.KEY_SPORT_DATA, new byte[] {
            0x35, 0x7B, 0x00, 0x01, 0x00, 0x64
        });
        frame[8] = (byte) ZhBraceletBinding.DIRECTION_PHONE; // an outgoing alarm frame, not steps
        assertNull(ZhBraceletBinding.parseSteps(frame));
    }

    @Test public void parseActivitySumsTheMinuteSlots() {
        byte[] frame = reply(ZhBraceletBinding.KEY_ACTIVITY_DURATION, new byte[] {
            0x35, 0x7B, 0x00, 0x03,
            0x05, 0x0A, 0x0F
        });
        ZhBraceletBinding.Activity activity = ZhBraceletBinding.parseActivity(frame);
        assertNotNull(activity);
        assertEquals("2026-11-27", activity.date);
        assertEquals(30, activity.totalMinutes);
        assertEquals(3, activity.slots.length);
    }

    @Test public void parseSleepRunsEachStageToTheNextRecord() {
        // 23:00 light (90m), 00:30 deep (60m), 01:30 REM (30m), 02:00 end marker.
        byte[] frame = reply(ZhBraceletBinding.KEY_SLEEP_DATA, new byte[] {
            0x35, 0x7B, 0x04,
            (byte) 0xB8, 0x02,
            0x03, (byte) 0xC3,
            0x0B, (byte) 0xC4,
            0x10, 0x01
        });
        ZhBraceletBinding.SleepSummary sleep = ZhBraceletBinding.parseSleep(frame);
        assertNotNull(sleep);
        assertEquals("2026-11-28", sleep.date);
        assertEquals(90, sleep.light);
        assertEquals(60, sleep.deep);
        assertEquals(30, sleep.rem);
        assertEquals(150, sleep.total);
        assertEquals(0, sleep.awake);
    }

    @Test public void parseSleepRollsTheDateOverNewYear() {
        // Packed 2026-12-31 -> the vendor labels the morning as 2027-01-01.
        byte[] frame = reply(ZhBraceletBinding.KEY_SLEEP_DATA, new byte[] {
            0x35, (byte) 0x9F, 0x01,
            0x10, 0x01
        });
        ZhBraceletBinding.SleepSummary sleep = ZhBraceletBinding.parseSleep(frame);
        assertNotNull(sleep);
        assertEquals("2027-01-01", sleep.date);
    }

    @Test public void parseSpo2ReadsTimestampedRecords() {
        byte[] frame = reply(ZhBraceletBinding.KEY_OFFLINE_SPO2, new byte[] {
            0x00, 0x00, 0x01,
            0x6A, (byte) 0xF6, (byte) 0xC8, (byte) 0xB8, 0x62
        });
        List<ZhBraceletBinding.SensorSample> samples = ZhBraceletBinding.parseSpo2(frame);
        assertNotNull(samples);
        assertEquals(1, samples.size());
        assertEquals("2026-11-27 12:34:56", samples.get(0).time);
        assertEquals(98, samples.get(0).value);
    }

    @Test public void parseStressUsesTheSameRecordShape() {
        byte[] frame = reply(ZhBraceletBinding.KEY_OFFLINE_STRESS, new byte[] {
            0x00, 0x00, 0x01,
            0x6A, (byte) 0xF6, (byte) 0xC8, (byte) 0xB8, 0x2A
        });
        List<ZhBraceletBinding.SensorSample> samples = ZhBraceletBinding.parseStress(frame);
        assertNotNull(samples);
        assertEquals(1, samples.size());
        assertEquals(42, samples.get(0).value);
    }

    @Test public void parseBodyTemperatureKeepsTheRawByteAndDifference() {
        byte[] frame = reply(ZhBraceletBinding.KEY_OFFLINE_TEMP, new byte[] {
            0x00, 0x05, 0x01,
            0x6A, (byte) 0xF6, (byte) 0xC8, (byte) 0xB8, 0x6E
        });
        List<ZhBraceletBinding.TempSample> samples = ZhBraceletBinding.parseBodyTemperature(frame);
        assertNotNull(samples);
        assertEquals(1, samples.size());
        assertEquals(110, samples.get(0).raw);
        assertEquals(5, samples.get(0).difference);
    }

    @Test public void parseBloodPressureDropsEmptyRecords() {
        byte[] frame = reply(ZhBraceletBinding.KEY_OFFLINE_BP, new byte[] {
            0x00, 0x00, 0x02,
            0x6A, (byte) 0xF6, (byte) 0xC8, (byte) 0xB8, 0x48, 0x78, 0x50,
            0x6A, (byte) 0xF6, (byte) 0xC8, (byte) 0xB8, 0x00, 0x00, 0x00
        });
        List<ZhBraceletBinding.BloodPressureSample> samples =
                ZhBraceletBinding.parseOfflineBloodPressure(frame);
        assertNotNull(samples);
        assertEquals(1, samples.size());
        assertEquals(72, samples.get(0).pulse);
        assertEquals(120, samples.get(0).systolic);
        assertEquals(80, samples.get(0).diastolic);
    }

    @Test public void parseRecordTimeRejectsAnInvalidMonth() {
        // month 0 -> parseRecordTime returns null, so the record is skipped.
        byte[] frame = reply(ZhBraceletBinding.KEY_OFFLINE_SPO2, new byte[] {
            0x00, 0x00, 0x01,
            0x68, 0x36, (byte) 0xC8, (byte) 0xB8, 0x62
        });
        List<ZhBraceletBinding.SensorSample> samples = ZhBraceletBinding.parseSpo2(frame);
        assertNotNull(samples);
        assertTrue(samples.isEmpty());
    }

    private static byte[] privateReply(byte[] payload) {
        return reply(ZhBraceletBinding.KEY_PRIVATE_FEATURE, payload);
    }

    private static byte[] reply(int key, byte[] payload) {
        byte[] frame = new byte[13 + payload.length];
        frame[0] = (byte) 0xAB;
        int body = frame.length - 8;
        frame[2] = (byte) ((body >> 8) & 0xFF);
        frame[3] = (byte) (body & 0xFF);
        frame[8] = (byte) ZhBraceletBinding.DIRECTION_WATCH;
        frame[10] = (byte) key;
        frame[11] = (byte) ((payload.length >> 8) & 0xFF);
        frame[12] = (byte) (payload.length & 0xFF);
        System.arraycopy(payload, 0, frame, 13, payload.length);
        return frame;
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
