package com.openfit.app.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Frame and parser tests for the SE (Apricot / Noise Watch Beat) command layer. */
public class SeCommandsTest {

    // ------------------------------------------------------------------------------------
    // Encoders
    // ------------------------------------------------------------------------------------

    @Test public void timeSyncMatchesTheVendorSystemTimeFrame() {
        // timestamp 1700000000, offset +05:30 = 22 quarter-hours (sint32 zigzag 44).
        assertArrayEquals(new byte[] {
                0x08, 0x30,                         // SEWear.id = 48
                0x2A, 0x0A,                         // system_time (5), 10 bytes
                0x0A, 0x08,                         // time_set (1), 8 bytes
                0x08, (byte) 0x80, (byte) 0xE2, (byte) 0xCF, (byte) 0xAA, 0x06,
                0x10, 0x2C                          // offset = 22
        }, SeCommands.timeSync(1700000000L, 22));
    }

    @Test public void timeSyncWritesANegativeOffsetAsSint32() {
        byte[] frame = SeCommands.timeSync(1700000000L, -20);
        // Trailing field: tag 0x10, zigzag(-20) = 39 = 0x27.
        assertArrayEquals(new byte[] {0x10, 0x27},
                new byte[] {frame[frame.length - 2], frame[frame.length - 1]});
    }

    @Test public void userProfileCarriesHeightWeightAgeAndSex() {
        assertArrayEquals(new byte[] {
                0x08, 0x44,                         // SEWear.id = 68
                0x32, 0x0E,                         // user_settings (6), 14 bytes
                0x2A, 0x0C,                         // user_profile (5), 12 bytes
                0x08, (byte) 0xB4, 0x01,            // height = 180
                0x15, 0x00, 0x00, (byte) 0x97, 0x42, // weight = 75.5f
                0x20, 0x1E,                         // age = 30
                0x28, 0x01                          // sex = MALE
        }, SeCommands.userProfile(180, 75.5f, 30, true));
    }

    @Test public void heartRateMonitorWritesEveryFieldTheVendorSets() {
        assertArrayEquals(new byte[] {
                0x08, (byte) 0xD7, 0x01,            // SEWear.id = 215
                0x7A, 0x12,                         // setting_menu (15), 18 bytes
                0x1A, 0x10,                         // heart_rate_monitor (3), 16 bytes
                0x08, 0x00,                         // mode = AUTO
                0x10, 0x05,                         // frequency = 5 minutes
                0x18, 0x00,                         // warning = false
                0x20, 0x00,                         // warning_value = 0
                0x28, 0x00,                         // sport_warning = false
                0x30, 0x00,                         // sport_warning_value = 0
                0x38, 0x00,                         // continuous_mode = ALL_DAY
                0x40, 0x00                          // low_warning_value = 0
        }, SeCommands.heartRateMonitor(true));
    }

    @Test public void heartRateMonitorOffUsesTheOffAndIntelligentEnums() {
        assertArrayEquals(new byte[] {
                0x08, (byte) 0xD7, 0x01,
                0x7A, 0x12,
                0x1A, 0x10,
                0x08, 0x01,                         // mode = OFF
                0x10, 0x05,
                0x18, 0x00,
                0x20, 0x00,
                0x28, 0x00,
                0x30, 0x00,
                0x38, 0x01,                         // continuous_mode = INTELLIGENT
                0x40, 0x00
        }, SeCommands.heartRateMonitor(false));
    }

    @Test public void sedentaryReminderCarriesWindowAndPeriod() {
        assertArrayEquals(new byte[] {
                0x08, (byte) 0xDA, 0x01,            // SEWear.id = 218
                0x7A, 0x1D,                         // setting_menu (15), 29 bytes
                0x22, 0x1B,                         // sedentary (4), 27 bytes
                0x08, 0x01,                         // on = true
                0x12, 0x08,                         // start_time (2), 8 bytes
                0x08, 0x09, 0x10, 0x00, 0x18, 0x00, 0x20, 0x00, // 09:00:00.000
                0x1A, 0x08,                         // end_time (3), 8 bytes
                0x08, 0x12, 0x10, 0x00, 0x18, 0x00, 0x20, 0x00, // 18:00:00.000
                0x20, (byte) 0x90, 0x1C,            // frequency = 3600 seconds
                0x28, 0x01                          // no_disturb = true
        }, SeCommands.sedentary(true, 3600, 9, 0, 18, 0));
    }

    @Test public void powerSavingIsANestedBool() {
        assertArrayEquals(new byte[] {
                0x08, (byte) 0xEC, 0x01,            // SEWear.id = 236
                0x7A, 0x04,                         // setting_menu (15), 4 bytes
                0x52, 0x02,                         // power_saving (10), 2 bytes
                0x08, 0x01                          // on = true
        }, SeCommands.powerSaving(true));
    }

    @Test public void realTimeHeartRateCarriesSwitchFrequencyAndShutdown() {
        assertArrayEquals(new byte[] {
                0x08, (byte) 0xDB, 0x05,            // SEWear.id = 731
                0x7A, 0x09,                         // setting_menu (15), 9 bytes
                (byte) 0xB2, 0x02, 0x06,            // real_time_settings (38), 6 bytes
                0x08, 0x01,                         // switch = true
                0x10, 0x01,                         // frequency = 1 s
                0x18, 0x3C                          // auto shutdown = 60 s
        }, SeCommands.realTimeHeartRate(true, 1, 60));
    }

    @Test public void bareRequestsAreTheVendorIds() {
        assertArrayEquals(new byte[] {0x08, (byte) 0xA1, 0x01}, SeCommands.findWear());
        assertArrayEquals(new byte[] {0x08, (byte) 0xD6, 0x01},
                SeCommands.requestHeartRateMonitor());
        assertArrayEquals(new byte[] {0x08, (byte) 0xD9, 0x01}, SeCommands.requestSedentary());
        assertArrayEquals(new byte[] {0x08, (byte) 0xEB, 0x01}, SeCommands.requestPowerSaving());
        assertArrayEquals(new byte[] {0x08, (byte) 0xDA, 0x05},
                SeCommands.requestRealTimeHeartRate());
        assertArrayEquals(new byte[] {0x08, 0x20}, SeCommands.deviceInfoRequest());
        assertArrayEquals(new byte[] {0x08, 0x21}, SeCommands.batteryRequest());
    }

    // ------------------------------------------------------------------------------------
    // Parsers
    // ------------------------------------------------------------------------------------

    @Test public void batteryReplyIsReadFromTheNestedDeviceMessage() {
        byte[] reply = {
                0x08, 0x21,                         // SEWear.id = 33
                0x22, 0x04,                         // device (4), 4 bytes
                0x12, 0x02,                         // battery (2), 2 bytes
                0x08, 0x57                          // capacity = 87
        };
        assertEquals(87, SeCommands.batteryLevel(reply));
        assertEquals("SEWear{id=33, error=0, battery=87}", SeCommands.describe(reply));
    }

    @Test public void deviceInfoReplyCarriesFirmwareSerialAndBattery() {
        byte[] reply = {
                0x08, 0x20,                         // SEWear.id = 32
                0x22, 0x14,                         // device (4), 20 bytes
                0x0A, 0x12,                         // device_info (1), 18 bytes
                0x0A, 0x05, '1', '.', '1', '.', '8', // firmware
                0x22, 0x05, '1', '0', '0', '0', '5', // serial
                0x2A, 0x02, 0x08, 0x4D              // battery status{capacity = 77}
        };
        SeCommands.DeviceInfo info = SeCommands.deviceInfo(reply);
        assertNotNull(info);
        assertEquals("1.1.8", info.firmware);
        assertEquals("10005", info.serialNumber);
        assertEquals(77, info.battery);
        assertEquals(77, SeCommands.batteryLevel(reply));
    }

    @Test public void heartRateMonitorReplyReportsAllDay() {
        byte[] reply = {
                0x08, (byte) 0xD6, 0x01,            // SEWear.id = 214
                0x7A, 0x06,                         // setting_menu (15), 6 bytes
                0x1A, 0x04,                         // heart_rate_monitor (3), 4 bytes
                0x08, 0x00,                         // mode = AUTO
                0x38, 0x00                          // continuous = ALL_DAY
        };
        assertEquals(Boolean.TRUE, SeCommands.heartRateMonitorOn(reply));
        assertEquals(Boolean.FALSE, SeCommands.intelligentHeartRate(reply));
    }

    @Test public void sedentaryReplyIsDecoded() {
        byte[] reply = {
                0x08, (byte) 0xD9, 0x01,            // SEWear.id = 217
                0x7A, 0x15,                         // setting_menu (15), 21 bytes
                0x22, 0x13,                         // sedentary (4), 19 bytes
                0x08, 0x01,                         // on = true
                0x12, 0x04, 0x08, 0x09, 0x10, 0x1E, // start 09:30
                0x1A, 0x04, 0x08, 0x12, 0x10, 0x00, // end 18:00
                0x20, (byte) 0x90, 0x1C,            // frequency = 3600 seconds
                0x28, 0x00                          // no_disturb = false
        };
        SeCommands.Sedentary sedentary = SeCommands.sedentary(reply);
        assertNotNull(sedentary);
        assertTrue(sedentary.on);
        assertEquals(60, sedentary.periodMinutes);
        assertEquals(9, sedentary.startHour);
        assertEquals(30, sedentary.startMinute);
        assertEquals(18, sedentary.endHour);
        assertEquals(0, sedentary.endMinute);
    }

    @Test public void powerSavingReplyIsDecoded() {
        byte[] reply = {
                0x08, (byte) 0xEB, 0x01,
                0x7A, 0x04,
                0x52, 0x02,
                0x08, 0x01
        };
        assertEquals(Boolean.TRUE, SeCommands.powerSavingOn(reply));
    }

    @Test public void realTimeSettingsAndSampleAreDecoded() {
        byte[] settings = {
                0x08, (byte) 0xDA, 0x05,            // SEWear.id = 730
                0x7A, 0x09,
                (byte) 0xB2, 0x02, 0x06,
                0x08, 0x01, 0x10, 0x01, 0x18, 0x3C
        };
        SeCommands.RealTimeSettings decoded = SeCommands.realTimeSettings(settings);
        assertNotNull(decoded);
        assertTrue(decoded.on);
        assertEquals(1, decoded.frequencySeconds);
        assertEquals(60, decoded.autoShutdownSeconds);

        byte[] sample = {
                0x08, (byte) 0xDD, 0x05,            // SEWear.id = 733
                0x7A, 0x0B,
                (byte) 0xBA, 0x02, 0x08,
                0x08, (byte) 0xFB, (byte) 0xE2, (byte) 0xCF, (byte) 0xAA, 0x06,
                0x10, 0x48                          // bpm = 72
        };
        SeCommands.RealTimeSample realTime = SeCommands.realTimeSample(sample);
        assertNotNull(realTime);
        assertEquals(1700000123L, realTime.timestamp);
        assertEquals(72, realTime.bpm);
    }

    @Test public void absentFieldsDoNotPretendToBeAnswers() {
        assertEquals(-1, SeCommands.batteryLevel(new byte[] {0x08, 0x21}));
        assertEquals(null, SeCommands.deviceInfo(new byte[] {0x08, 0x20}));
        assertEquals(null, SeCommands.heartRateMonitorOn(new byte[] {0x08, (byte) 0xD6, 0x01}));
        assertFalse(SeCommands.isCommandReply(SePairing.FUNCTION_BINDING_RESULT));
        assertTrue(SeCommands.isCommandReply(SeCommands.FUNCTION_DEVICE_BATTERY));
        assertTrue(SeCommands.isCommandReply(SeCommands.FUNCTION_SET_TIME));
        assertTrue(SeCommands.isCommandReply(SeCommands.FUNCTION_SET_USER_PROFILE));
    }
}