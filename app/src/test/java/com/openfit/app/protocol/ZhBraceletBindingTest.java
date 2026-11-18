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

    @Test public void keyLabelsNameTheRecoveredCommands() {
        assertEquals("time sync", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_TIME_SYNC));
        assertEquals("user info", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_USER_INFO));
        assertEquals("status", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_STATUS));
        assertEquals("bind info", ZhBraceletBinding.keyLabel(ZhBraceletBinding.KEY_BIND_INFO));
        assertEquals("key 0x08", ZhBraceletBinding.keyLabel(0x08));
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
}
