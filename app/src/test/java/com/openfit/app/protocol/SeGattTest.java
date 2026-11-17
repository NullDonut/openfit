package com.openfit.app.protocol;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SeGattTest {
    @Test public void chunkSizeLeavesRoomForTheMtuAndTwoByteHeader() {
        assertEquals(18, SeGatt.chunkSize(23));
        assertEquals(242, SeGatt.chunkSize(247));
        assertEquals(1, SeGatt.chunkSize(3));
    }

    @Test public void chunkCountRoundsUp() {
        assertEquals(1, SeGatt.chunkCount(0, 23));
        assertEquals(1, SeGatt.chunkCount(18, 23));
        assertEquals(2, SeGatt.chunkCount(19, 23));
        assertEquals(2, SeGatt.chunkCount(36, 23));
    }

    @Test public void announceMatchesTheVendorBytes() {
        assertArrayEquals(new byte[] {0, 0, 0, 0, 3, 0}, SeGatt.announce(3));
        assertTrue(SeGatt.isAnnounce(SeGatt.announce(3)));
        assertEquals(3, SeGatt.announceCount(SeGatt.announce(3)));
        assertFalse(SeGatt.isAnnounce(new byte[] {0, 0, 1, 1, 0, 0}));
    }

    @Test public void controlFramesMatchTheVendorBytes() {
        assertArrayEquals(new byte[] {0, 0, 1, 1, 0, 0},
                SeGatt.control(SeGatt.CODE_READY));
        assertArrayEquals(new byte[] {0, 0, 1, 0, 0, 0},
                SeGatt.control(SeGatt.CODE_RECEIVED));
        assertEquals(SeGatt.CODE_READY, SeGatt.controlCode(SeGatt.control(SeGatt.CODE_READY)));
        assertEquals(SeGatt.CODE_DONE, SeGatt.controlCode(SeGatt.control(SeGatt.CODE_DONE)));
        assertTrue(SeGatt.isControl(SeGatt.control(SeGatt.CODE_BUSY)));
        assertFalse(SeGatt.isControl(new byte[] {1, 0, 0, 1}));
    }

    @Test public void resendCarriesTheChunkIndexLittleEndian() {
        byte[] frame = SeGatt.control(SeGatt.CODE_RESEND, 2);
        assertArrayEquals(new byte[] {0, 0, 1, 5, 2, 0}, frame);
        assertEquals(SeGatt.CODE_RESEND, SeGatt.controlCode(frame));
        assertEquals(2, SeGatt.controlPayload(frame));
    }

    @Test public void packetPrefixedTheIndexLittleEndian() {
        byte[] data = {10, 11, 12, 13};
        byte[] frame = SeGatt.packet(2, data, 1, 2);
        assertArrayEquals(new byte[] {2, 0, 11, 12}, frame);
        assertEquals(2, SeGatt.packetIndex(frame));
        assertArrayEquals(new byte[] {11, 12}, SeGatt.packetData(frame));
    }

    @Test public void transferReassemblesOutOfOrderChunks() {
        SeGatt.Transfer transfer = new SeGatt.Transfer();
        assertTrue(transfer.start(3));
        assertNull(transfer.accept(3, new byte[] {5}));
        assertNull(transfer.accept(1, new byte[] {1, 2}));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, transfer.accept(2, new byte[] {3, 4}));
        assertFalse(transfer.isActive());
    }

    @Test public void transferIgnoresDuplicateAndOutOfRangeChunks() {
        SeGatt.Transfer transfer = new SeGatt.Transfer();
        assertTrue(transfer.start(2));
        assertNull(transfer.accept(1, new byte[] {1}));
        assertNull(transfer.accept(1, new byte[] {9})); // duplicate keeps the first copy
        assertArrayEquals(new byte[] {1, 2}, transfer.accept(2, new byte[] {2}));
        assertNull(transfer.accept(2, new byte[] {3})); // no transfer is active any more
    }

    @Test public void transferRejectsImplausibleAnnounces() {
        SeGatt.Transfer transfer = new SeGatt.Transfer();
        assertFalse(transfer.start(0));
        assertFalse(transfer.start(4097));
        assertFalse(transfer.isActive());
    }
}
