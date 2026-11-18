package com.openfit.app.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BerryBindingTest {
    @Test public void userIdBodyMatchesVendorByteLayout() {
        byte[] body = BerryBinding.userIdPayload("token123", "Pixel", "16");
        assertArrayEquals(new byte[] {
            0x08, 't', 'o', 'k', 'e', 'n', '1', '2', '3',
            (byte) 0xBB,
            0x05, 'P', 'i', 'x', 'e', 'l',
            0x02, '1', '6'
        }, body);
    }

    @Test public void userIdBodyTruncatesLikeTheVendor() {
        String token = repeat('a', 101);
        String phone = repeat('b', 256);
        String version = repeat('c', 51);
        byte[] body = BerryBinding.userIdPayload(token, phone, version);
        int at = 0;
        assertEquals(100, body[at++] & 0xff);
        at += 100; // token bytes
        assertEquals((byte) 0xBB, body[at++]);
        assertEquals(255, body[at++] & 0xff);
        at += 255; // phone bytes
        assertEquals(50, body[at++] & 0xff);
    }

    @Test public void protocolRequestIsPrivateCommandOneWithEmptyBody() {
        assertArrayEquals(new byte[] {
            (byte) 0x66, (byte) 0x66, 0x48, 0x5A,
            0x0B, 0x03, 0x02, 0x04, 0x00,
            0x01, 0x00, 0x00, 0x00
        }, BerryBinding.protocolFrame(11));
    }

    @Test public void protocolInfoDecodesVersionAndPlatform() {
        byte[] payload = {
            0x34, 0x12,             // type 0x1234
            0x08, 0x07, 0x06,       // protocol 6.7.8
            0x00, 0x00, 0x00,       // padding
            0x03,                   // album version
            0x02,                   // platform
            0x10, 0x00,             // album radius
            0x40                    // feature bits
        };
        BerryBinding.ProtocolInfo info = BerryBinding.ProtocolInfo.parse(payload);
        assertNotNull(info);
        assertEquals(0x1234, info.deviceType);
        assertEquals("6.7.8", info.protocolVersion);
        assertEquals(3, info.albumVersion);
        assertEquals(2, info.platformType);
        assertNull(BerryBinding.ProtocolInfo.parse(new byte[] {1, 2}));
    }

    @Test public void userIdFrameWrapsPrivateCommandTwo() {
        byte[] frame = BerryBinding.userIdFrame(5, "abc", "P", "1");
        assertEquals(22, frame.length);
        assertEquals(5, frame[4]);
        assertArrayEquals(new byte[] {
            0x02, 0x00, 0x09, 0x00,
            0x03, 'a', 'b', 'c', (byte) 0xBB, 0x01, 'P', 0x01, '1'
        }, java.util.Arrays.copyOfRange(frame, 9, frame.length));
    }

    @Test public void bindFramesUsePrivateCommandsThreeAndFour() {
        assertArrayEquals(new byte[] {
            (byte) 0x66, (byte) 0x66, 0x48, 0x5A,
            0x09, 0x03, 0x02, 0x04, 0x00,
            0x03, 0x00, 0x00, 0x00
        }, BerryBinding.bindFrame(9));

        assertArrayEquals(new byte[] {
            (byte) 0x66, (byte) 0x66, 0x48, 0x5A,
            0x0A, 0x03, 0x02, 0x05, 0x00,
            0x04, 0x00, 0x01, 0x00, 0x01
        }, BerryBinding.bindResultFrame(10, true));
    }

    @Test public void ackIsEightBytesWithoutFrameHeader() {
        assertArrayEquals(new byte[] {
            (byte) 0x99, (byte) 0x99, 0x48, 0x5A, 0x07, 0x00, 0x00, 0x00
        }, BerryBinding.ackFrame(7));
        assertEquals(8, BerryBinding.ackFrame(7).length);
    }

    @Test public void tokenMatchesVendorSubstringRule() {
        String uuid = "012345678901234567890123456789012345"; // 36 characters
        assertEquals("012345123u1", BerryBinding.newToken(uuid, 123, "u1"));
        assertEquals("short1", BerryBinding.newToken("short", 1, ""));
    }

    @Test public void deviceInfoDecodesMacsSerialAndFlags() {
        byte[] payload = {
            0x34, 0x12,                    // type 0x1234
            0x03, 0x02, 0x01,              // version v1.2.3 (read 4,3,2)
            0x11, 0x22, 0x33, 0x44, 0x55, 0x66, // device MAC little-endian
            (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF,
            0x02,                          // SN length
            (byte) 0xAB, (byte) 0xCD,      // SN, printed in reverse
            0x40                           // bit6 => bound, bit7 clear => same user
        };
        BerryBinding.DeviceInfo info = BerryBinding.DeviceInfo.parse(payload);
        assertNotNull(info);
        assertEquals(0x1234, info.deviceType);
        assertEquals("v1.2.3", info.deviceVersion);
        assertEquals("66:55:44:33:22:11", info.deviceMac);
        assertEquals("FF:EE:DD:CC:BB:AA", info.btMac);
        assertEquals("CDAB", info.serialNumber);
        assertTrue(info.isBind);
        assertTrue(info.isSameUser);
    }

    @Test public void deviceInfoFlagsFollowVendorBitOrder() {
        byte[] base = new byte[21];
        base[17] = 0; // no serial bytes; flags sit at index 18

        base[18] = (byte) 0xC0; // bit7 set => different user, bit6 set => bound
        BerryBinding.DeviceInfo other = BerryBinding.DeviceInfo.parse(base);
        assertNotNull(other);
        assertTrue(other.isBind);
        assertFalse(other.isSameUser);

        base[18] = 0x00;
        BerryBinding.DeviceInfo blank = BerryBinding.DeviceInfo.parse(base);
        assertNotNull(blank);
        assertFalse(blank.isBind);
        assertTrue(blank.isSameUser);

        assertNull(BerryBinding.DeviceInfo.parse(new byte[18]));
    }

    @Test public void statusLabelsCoverTheVendorTable() {
        assertEquals("watch accepted the bind", BerryBinding.bindStatusLabel(0));
        assertEquals("watch rejected the account token", BerryBinding.bindStatusLabel(1));
        assertEquals("watch is already bound", BerryBinding.bindStatusLabel(3));
        assertEquals("watch timed out waiting for confirmation", BerryBinding.bindStatusLabel(5));
        assertEquals("bind confirmed", BerryBinding.bindResultLabel(0));
    }

    private static String repeat(char value, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }
}
