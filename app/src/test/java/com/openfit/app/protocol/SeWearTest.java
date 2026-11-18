package com.openfit.app.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SeWearTest {
    @Test public void idIsFieldOne() {
        assertArrayEquals(new byte[] {0x08, 0x10}, SeWear.wear(16));
        assertEquals(16, SeWear.readId(SeWear.wear(16)));
        assertEquals(16, SeWear.readId(new byte[] {0x08, 0x10, 0x1A, 0x00}));
        assertArrayEquals(new byte[0], SeWear.wear(0));
        assertEquals(-1, SeWear.readId(new byte[] {0x10, 0x10}));
    }

    @Test public void bindAccountIsFieldThree() {
        assertArrayEquals(new byte[] {0x08, 0x11, 0x1A, 0x02, 0x08, 0x01},
                SeWear.wearMessage(17, SeWear.FIELD_BIND_ACCOUNT, new byte[] {0x08, 0x01}));
        assertArrayEquals(new byte[] {0x08, 0x11, 0x1A, 0x00},
                SeWear.wearMessage(17, SeWear.FIELD_BIND_ACCOUNT, new byte[0]));
        assertArrayEquals(new byte[] {0x08, 0x01},
                SeWear.readMessageField(new byte[] {0x08, 0x11, 0x1A, 0x02, 0x08, 0x01},
                        SeWear.FIELD_BIND_ACCOUNT));
        assertNull(SeWear.readMessageField(new byte[] {0x08, 0x11}, SeWear.FIELD_BIND_ACCOUNT));
    }

    @Test public void errorCodeIsFieldOneHundred() {
        // 100 << 3 | 0 = 800 = 0xA0 0x06
        byte[] message = {(byte) 0xA0, 0x06, 0x05};
        assertEquals(5, SeWear.readErrorCode(message));
        assertEquals(0, SeWear.readErrorCode(new byte[] {0x08, 0x10}));
    }

    @Test public void emptyFieldIsNotEmittedUnlessRequested() {
        assertArrayEquals(new byte[] {0x08, 0x10},
                SeWear.wear(16, SeWear.FIELD_BIND_ACCOUNT, new byte[0]));
        assertArrayEquals(new byte[] {0x08, 0x10, 0x1A, 0x00},
                SeWear.wearMessage(16, SeWear.FIELD_BIND_ACCOUNT, null));
    }
}
