package com.openfit.app.protocol;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BuzzUploadTest {
    @Test public void frameWrapsPayloadInHeaderAndCrc() {
        byte[] payload = {1, 2, 3, 4};
        byte[] frame = BuzzUpload.frameWatch(payload);
        assertEquals(22 + 4 + 4, frame.length);
        assertEquals(0, frame[0]);
        assertEquals(BuzzUpload.TYPE_WATCH, frame[1] & 0xff);
        for (int i = 2; i < 18; i++) assertEquals("byte " + i + " must be zero", 0, frame[i]);
        // Payload length, LE32 at offset 18.
        assertEquals(4, le32(frame, 18));
        assertArrayEquals(payload, Arrays.copyOfRange(frame, 22, 26));
        // Trailing CRC32 (IEEE) of header+payload, LE32.
        CRC32 crc = new CRC32();
        crc.update(frame, 0, 26);
        assertEquals((int) crc.getValue(), le32(frame, 26));
    }

    @Test public void chunkAndBlockMathMatchTheVendorExample() {
        // MTU 247 -> chunk 244, 16 chunks/block, 3868 bytes/block.
        assertEquals(244, BuzzUpload.chunkSize(247));
        assertEquals(16, BuzzUpload.chunksPerBlock(244));
        assertEquals(3868, BuzzUpload.blockBytes(244));
        // A 549884-byte face frames to 549910: 143 blocks, the last 654 bytes over 3 chunks.
        int framed = 549884 + BuzzUpload.FRAME_OVERHEAD;
        assertEquals(549910, framed);
        assertEquals(143, BuzzUpload.totalBlocks(framed, 244));
        assertEquals(654, BuzzUpload.blockDataLength(framed, 244, 143));
        assertEquals(3, BuzzUpload.chunksInBlock(654, 244));
        // The saved-binding Buzz path never raises MTU, so mtu 23 -> chunk 20.
        assertEquals(20, BuzzUpload.chunkSize(23));
        assertEquals(200, BuzzUpload.chunksPerBlock(20));
        assertEquals(3596, BuzzUpload.blockBytes(20));
    }

    @Test public void firstChunkOfABlockCarriesTheBlockHeader() {
        byte[] framed = new byte[100];
        for (int i = 0; i < framed.length; i++) framed[i] = (byte) i;
        List<byte[]> chunks = BuzzUpload.blockChunks(framed, 244, 1);
        assertEquals(1, chunks.size()); // 100 <= chunk - 6, so a single chunk
        byte[] chunk = chunks.get(0);
        assertEquals(6 + 100, chunk.length);
        assertArrayEquals(new byte[] {1, 0, 1, 0, 1, 0}, Arrays.copyOfRange(chunk, 0, 6));
        assertArrayEquals(framed, Arrays.copyOfRange(chunk, 6, chunk.length));
    }

    @Test public void laterChunksCarryOnlyTheirOwnIndex() {
        byte[] framed = new byte[300];
        for (int i = 0; i < framed.length; i++) framed[i] = (byte) i;
        List<byte[]> chunks = BuzzUpload.blockChunks(framed, 100, 1);
        // chunk 100 -> first carries 94, then 98 each: 94 + 98 + 98 + 10.
        assertEquals(4, chunks.size());
        assertEquals(6 + 94, chunks.get(0).length);
        assertEquals(2 + 98, chunks.get(1).length);
        assertEquals(2 + 98, chunks.get(2).length);
        assertEquals(2 + 10, chunks.get(3).length);
        assertArrayEquals(new byte[] {2, 0}, Arrays.copyOfRange(chunks.get(1), 0, 2));
        assertArrayEquals(new byte[] {4, 0}, Arrays.copyOfRange(chunks.get(3), 0, 2));
        // The chunks reassemble to the framed bytes, in order.
        byte[] joined = new byte[300];
        int at = 0;
        for (int i = 0; i < chunks.size(); i++) {
            byte[] chunk = chunks.get(i);
            int header = i == 0 ? 6 : 2;
            System.arraycopy(chunk, header, joined, at, chunk.length - header);
            at += chunk.length - header;
        }
        assertArrayEquals(framed, joined);
    }

    @Test public void classifiesTheBlockAcks() {
        assertEquals(BuzzUpload.ACK_GO_AHEAD, BuzzUpload.classifyAck(hex("00 00 01 01 00 00")));
        assertEquals(BuzzUpload.ACK_BLOCK_DONE, BuzzUpload.classifyAck(hex("00 00 01 00 00 00")));
        assertEquals(BuzzUpload.ACK_RESEND, BuzzUpload.classifyAck(hex("00 00 01 00 03 00")));
        assertEquals(BuzzUpload.ACK_RESEND, BuzzUpload.classifyAck(hex("00 00 01 01 07 00")));
        assertEquals(3, BuzzUpload.resendIndex(hex("00 00 01 00 03 00")));
        assertEquals(0, BuzzUpload.resendIndex(hex("00 00 01 01 00 00")));
        assertEquals(BuzzUpload.ACK_NONE, BuzzUpload.classifyAck(hex("00 00 01 02 00 00")));
        assertEquals(BuzzUpload.ACK_NONE, BuzzUpload.classifyAck(new byte[0]));
        assertEquals(BuzzUpload.ACK_NONE, BuzzUpload.classifyAck(null));
    }

    @Test public void emptyPayloadStillFrames() {
        byte[] frame = BuzzUpload.frameWatch(new byte[0]);
        assertEquals(22 + 4, frame.length);
        assertEquals(0, le32(frame, 18));
        assertTrue(frame[1] == BuzzUpload.TYPE_WATCH);
    }

    private static int le32(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    private static byte[] hex(String text) {
        String clean = text.replace(" ", "");
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
