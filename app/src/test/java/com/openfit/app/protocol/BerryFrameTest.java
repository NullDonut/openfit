package com.openfit.app.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BerryFrameTest {
    private static final byte[] BIND_REQUEST = {
        // BERRY_APP_REQ_DEV_HEAD = {102, 102, 72, 90}: the head the phone must use.
        (byte) 0x66, (byte) 0x66, 0x48, 0x5A,
        0x00,                                  // serial
        0x03,                                  // event: private
        0x02,                                  // type: private
        0x04, 0x00,                            // payload length
        0x03, 0x00, 0x00, 0x00                 // cmdId 3 (bind), empty body
    };

    @Test public void phoneFramesUseTheAppToDeviceMagic() {
        // BERRY_APP_REQ_DEV_HEAD = {102, 102, 72, 90}: a swapped head makes the watch drop the
        // frame silently, which is indistinguishable from a wrong command.
        assertEquals(0x5A486666, BerryFrame.HEAD_APP_REQ_DEV);
        byte[] raw = BerryFrame.privateCommand(0, 3, null).toBytes();
        assertEquals((byte) 0x66, raw[0]);
        assertEquals((byte) 0x66, raw[1]);
        assertEquals((byte) 0x48, raw[2]);
        assertEquals((byte) 0x5A, raw[3]);
        assertEquals(0x5A488888, BerryFrame.HEAD_DEV_REQ_APP);
    }

    @Test public void privateBindFrameMatchesObservedLayout() {
        assertArrayEquals(BIND_REQUEST, BerryFrame.privateCommand(0, BerrySewear.PRIVATE_CMD_BIND, null).toBytes());
    }

    @Test public void bindResultFrameCarriesOneByteFlag() {
        byte[] frame = BerryFrame.privateCommand(7, BerrySewear.PRIVATE_CMD_BIND_RESULT, new byte[] {1}).toBytes();
        assertEquals(14, frame.length);
        assertEquals(7, frame[4]);
        assertEquals(5, frame[7]);
        assertEquals(1, frame[13]);
    }

    @Test public void sewearBindStateRequestEncodesTypeOnly() {
        // SEWear{ACCOUNT}: proto3 omits the zero id, so only field 1 varint 1 remains.
        assertArrayEquals(new byte[] {0x08, 0x01}, BerrySewear.bindStateRequest());
        byte[] frame = BerryFrame.protobuf(1, BerrySewear.bindStateRequest()).toBytes();
        assertEquals(11, frame.length);
        assertEquals(0x01, frame[5]); // event protobuf
        assertEquals(0x03, frame[6]); // type protobuf
        assertEquals(2, frame[7]);
        assertEquals(0x08, frame[9]);
        assertEquals(0x01, frame[10]);
    }

    @Test public void decodeRoundTripsHeaderAndPayload() {
        byte[] raw = BerryFrame.protobuf(0x40, BerrySewear.wear(BerrySewear.TYPE_SETTING_MENU, 141)).toBytes();
        BerryFrame frame = BerryFrame.decode(raw);
        assertEquals(BerryFrame.HEAD_APP_REQ_DEV, frame.head());
        assertEquals(0x40, frame.serialNo());
        assertTrue(frame.isProtobuf());
        assertFalse(frame.isPrivate());
        assertEquals(BerrySewear.TYPE_SETTING_MENU, BerrySewear.readType(frame.payload()));
        assertEquals(141, BerrySewear.readId(frame.payload()));
    }

    @Test public void privateFrameExposesCommandId() {
        BerryFrame frame = BerryFrame.decode(BerryFrame.privateCommand(2, 4, new byte[] {0, 1, 2}).toBytes());
        assertTrue(frame.isPrivate());
        assertEquals(4, frame.privateCmdId());
        assertArrayEquals(new byte[] {0, 1, 2}, frame.privateData());
    }

    @Test public void ackIsAShortControlFrame() {
        assertArrayEquals(new byte[] {
            (byte) 0x99, (byte) 0x99, 0x48, 0x5A, 0x2A, 0x00, 0x00, 0x00
        }, BerryFrame.ackBytes(0x2A, 0));
    }

    @Test public void frameLengthMatchesDeclaredPayload() {
        byte[] raw = BerryFrame.privateCommand(0, 3, null).toBytes();
        assertEquals(raw.length, BerryFrame.frameLength(raw));
        assertEquals(-1, BerryFrame.frameLength(new byte[] {1, 2, 3}));
    }

    @Test public void serialNumberWrapsAt255() {
        assertEquals(0, BerryFrame.nextSerial(255));
        assertEquals(9, BerryFrame.nextSerial(8));
    }

    @Test public void decodeRejectsTruncatedFrame() {
        byte[] raw = BerryFrame.privateCommand(0, 3, null).toBytes();
        byte[] truncated = new byte[raw.length - 1];
        System.arraycopy(raw, 0, truncated, 0, truncated.length);
        try {
            BerryFrame.decode(truncated);
            fail("expected rejection of truncated frame");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test public void sewearNestedPayloadKeepsFieldNumber() {
        byte[] body = {0x08, 0x01};
        byte[] sewear = BerrySewear.wear(BerrySewear.TYPE_FACTORY, 6, 15, body);
        assertArrayEquals(new byte[] {0x08, 0x0C, 0x10, 0x06, 0x7A, 0x02, 0x08, 0x01}, sewear);
    }

    @Test public void sewearReaderReportsAbsentFields() {
        assertEquals(-1, BerrySewear.readType(null));
        assertEquals(-1, BerrySewear.readId(new byte[] {0x08, 0x02}));
        assertEquals(-1, BerrySewear.readId(new byte[] {(byte) 0xFF}));
    }
}
