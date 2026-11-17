package com.openfit.app.protocol;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BerryFrameReaderTest {
    private static byte[] watchFrame(int serial, int cmdId, byte[] data) {
        return BerryFrame.of(BerryFrame.HEAD_DEV_REQ_APP, serial, BerryFrame.EVENT_PRIVATE,
                BerryFrame.TYPE_PRIVATE, BerryFrame.privatePayload(cmdId, data)).toBytes();
    }

    @Test public void reassemblesFrameSplitAcrossNotifications() {
        byte[] frame = watchFrame(3, 2, new byte[] {1, 2, 3, 4, 5});
        BerryFrameReader reader = new BerryFrameReader();

        assertTrue(reader.append(java.util.Arrays.copyOfRange(frame, 0, 5)).isEmpty());
        List<BerryFrame> frames = reader.append(java.util.Arrays.copyOfRange(frame, 5, frame.length));

        assertEquals(1, frames.size());
        assertEquals(3, frames.get(0).serialNo());
        assertEquals(2, frames.get(0).privateCmdId());
        assertEquals(5, frames.get(0).privateData().length);
    }

    @Test public void consumesAckAndSkipsLeadingNoise() {
        byte[] ack = BerryBinding.ackFrame(9);
        byte[] frame = watchFrame(10, 3, new byte[] {0});
        byte[] stream = new byte[2 + ack.length + frame.length];
        stream[0] = 0x01;
        stream[1] = 0x02;
        System.arraycopy(ack, 0, stream, 2, ack.length);
        System.arraycopy(frame, 0, stream, 2 + ack.length, frame.length);

        List<BerryFrame> frames = new BerryFrameReader().append(stream);

        assertEquals(1, frames.size());
        assertEquals(10, frames.get(0).serialNo());
        assertEquals(3, frames.get(0).privateCmdId());
    }

    @Test public void returnsEveryCompleteFrameInOneChunk() {
        byte[] first = watchFrame(1, 2, new byte[] {0x11});
        byte[] second = watchFrame(2, 3, new byte[] {0x00});
        byte[] stream = new byte[first.length + second.length];
        System.arraycopy(first, 0, stream, 0, first.length);
        System.arraycopy(second, 0, stream, first.length, second.length);

        List<BerryFrame> frames = new BerryFrameReader().append(stream);

        assertEquals(2, frames.size());
        assertEquals(1, frames.get(0).serialNo());
        assertEquals(2, frames.get(1).serialNo());
    }

    @Test public void keepsPartialMagicAtTheTail() {
        byte[] frame = watchFrame(4, 4, new byte[] {0});
        BerryFrameReader reader = new BerryFrameReader();
        byte[] head = new byte[] {(byte) 0x66, (byte) 0x66, 0x48};

        assertTrue(reader.append(head).isEmpty());
        assertEquals(1, reader.append(java.util.Arrays.copyOfRange(frame, 3, frame.length)).size());
    }

    @Test public void resetDropsPartialFrame() {
        byte[] frame = watchFrame(5, 2, new byte[] {1, 2});
        BerryFrameReader reader = new BerryFrameReader();
        assertTrue(reader.append(java.util.Arrays.copyOfRange(frame, 0, 4)).isEmpty());
        reader.reset();
        // The remaining bytes no longer contain a magic at the start, so they are discarded.
        assertEquals(0, reader.append(java.util.Arrays.copyOfRange(frame, 4, frame.length)).size());
    }

    @Test public void ackAloneProducesNoFrame() {
        assertEquals(0, new BerryFrameReader().append(BerryBinding.ackFrame(1)).size());
    }
}
