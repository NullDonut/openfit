package com.openfit.app.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Test public void errorCodesHaveLabels() {
        assertEquals("no error", SeWear.errorLabel(0));
        assertEquals("not supported", SeWear.errorLabel(1));
        assertEquals("dependency not ready", SeWear.errorLabel(2));
        assertEquals("unknown error", SeWear.errorLabel(255));
        assertEquals("error 9", SeWear.errorLabel(9));
    }

    @Test public void emptyFieldIsNotEmittedUnlessRequested() {
        assertArrayEquals(new byte[] {0x08, 0x10},
                SeWear.wear(16, SeWear.FIELD_BIND_ACCOUNT, new byte[0]));
        assertArrayEquals(new byte[] {0x08, 0x10, 0x1A, 0x00},
                SeWear.wearMessage(16, SeWear.FIELD_BIND_ACCOUNT, null));
    }

    @Test public void repeatedFieldsAreCollectedInOrder() {
        // field 1 three times, with a field-2 varint in between that must be skipped.
        byte[] message = {
                0x0A, 0x01, 'a',
                0x10, 0x07,
                0x0A, 0x01, 'b',
                0x0A, 0x02, 'c', 'd'
        };
        java.util.List<byte[]> values = SeWear.readMessages(message, 1);
        assertEquals(3, values.size());
        assertArrayEquals(new byte[] {'a'}, values.get(0));
        assertArrayEquals(new byte[] {'b'}, values.get(1));
        assertArrayEquals(new byte[] {'c', 'd'}, values.get(2));
        assertTrue(SeWear.readMessages(message, 5).isEmpty());
        assertTrue(SeWear.readMessages(null, 1).isEmpty());
    }
}
