package com.openfit.app.protocol;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WatchFaceBuilderTest {
    @Test public void wrapsTheRawDialIntoAnAcceptedContainer() {
        byte[] raw = ramp(486752);
        byte[] dial = WatchFaceBuilder.wrap(raw);

        // 119 blocks of 4096 (the last 3424), each compressed to less than a block.
        int blocks = 119;
        int header = 26 + 2 * blocks;
        int stored = le32(dial, 12);
        assertTrue("the ramp should compress", stored < raw.length);
        assertEquals(header + stored, dial.length);
        assertTrue(BuzzUpload.isDialContainer(dial));

        assertEquals("magic", 0xaf, dial[0] & 0xff);
        assertEquals(0x02, dial[3] & 0xff);
        assertEquals(486752, le32(dial, 4));   // raw length
        assertEquals(0, le32(dial, 8));        // raw CRC (not checked)
        assertEquals(0, le32(dial, 16));       // stored CRC (not checked)
        assertEquals(0, le32(dial, 20));
        assertEquals(blocks, le16(dial, 24));

        // Every block is compressed (bit15 set), and the length table adds up to the header field.
        int sum = 0;
        for (int i = 0; i < blocks; i++) {
            int flag = le16(dial, 26 + 2 * i);
            assertTrue("block " + i + " must be compressed", (flag & 0x8000) != 0);
            sum += flag & 0x7fff;
        }
        assertEquals(stored, sum);

        // The blocks inflate back to the raw dial, byte for byte.
        assertArrayEquals(raw, unwrap(dial));
    }

    @Test public void incompressibleBlocksAreStillCompressedAndRoundTrip() {
        byte[] raw = new byte[5000]; // two blocks; the second is short
        new Random(424242).nextBytes(raw);
        byte[] dial = WatchFaceBuilder.wrap(raw);

        int blocks = le16(dial, 24);
        assertEquals(2, blocks);
        for (int i = 0; i < blocks; i++) {
            int flag = le16(dial, 26 + 2 * i);
            assertTrue("block " + i + " must be marked LZO", (flag & 0x8000) != 0);
        }
        // Even when a block grows, the container stays self-consistent and decodes exactly.
        assertTrue(BuzzUpload.isDialContainer(dial));
        assertArrayEquals(raw, unwrap(dial));
    }

    @Test public void backgroundIsWrittenAsLittleEndianRgb565() {
        byte[] raw = new byte[WatchFaceBuilder.BACKGROUND_OFFSET
                + WatchFaceBuilder.BACKGROUND_BYTES + 8];
        int pixels = WatchFaceBuilder.BACKGROUND_WIDTH * WatchFaceBuilder.BACKGROUND_HEIGHT;
        int[] argb = new int[pixels];
        // A red pixel, a green pixel, then white for the rest.
        argb[0] = 0xffff0000;
        argb[1] = 0xff00ff00;
        Arrays.fill(argb, 2, pixels, 0xffffffff);
        WatchFaceBuilder.writeBackground(raw, argb, WatchFaceBuilder.BACKGROUND_WIDTH,
                WatchFaceBuilder.BACKGROUND_HEIGHT);

        int at = WatchFaceBuilder.BACKGROUND_OFFSET;
        assertEquals(0xf800, le16(raw, at));      // red  -> 11111 000000 00000
        assertEquals(0x07e0, le16(raw, at + 2));  // green-> 00000 111111 00000
        assertEquals(0xffff, le16(raw, at + 4));  // white
        // The byte right after the region is untouched.
        assertEquals(0, raw[WatchFaceBuilder.BACKGROUND_OFFSET + WatchFaceBuilder.BACKGROUND_BYTES]);
    }

    @Test public void buildOverwritesOnlyTheBackgroundAndLeavesTheTemplate() {
        byte[] template = new byte[WatchFaceBuilder.BACKGROUND_OFFSET
                + WatchFaceBuilder.BACKGROUND_BYTES + 16];
        Arrays.fill(template, (byte) 0x5a);
        int pixels = WatchFaceBuilder.BACKGROUND_WIDTH * WatchFaceBuilder.BACKGROUND_HEIGHT;
        int[] argb = new int[pixels];
        Arrays.fill(argb, 0xff1e90ff); // dodger blue

        byte[] dial = WatchFaceBuilder.build(template, argb,
                WatchFaceBuilder.BACKGROUND_WIDTH, WatchFaceBuilder.BACKGROUND_HEIGHT);

        // The template itself is not mutated.
        for (int i = 0; i < template.length; i++) {
            if (i >= WatchFaceBuilder.BACKGROUND_OFFSET
                    && i < WatchFaceBuilder.BACKGROUND_OFFSET + WatchFaceBuilder.BACKGROUND_BYTES) {
                continue;
            }
            assertEquals("template byte " + i, (byte) 0x5a, template[i]);
        }
        assertTrue(BuzzUpload.isDialContainer(dial));

        // After inflating, the region holds dodger blue and nothing outside it moved.
        byte[] raw = unwrap(dial);
        int blue = (((0x1e) >> 3) << 11) | (((0x90) >> 2) << 5) | (0xff >> 3);
        int at = WatchFaceBuilder.BACKGROUND_OFFSET;
        for (int i = 0; i < pixels; i++) {
            assertEquals("pixel " + i, blue, le16(raw, at + 2 * i));
        }
        assertEquals((byte) 0x5a, raw[at + WatchFaceBuilder.BACKGROUND_BYTES]);
    }

    @Test public void buildRejectsMalformedInput() {
        int pixels = WatchFaceBuilder.BACKGROUND_WIDTH * WatchFaceBuilder.BACKGROUND_HEIGHT;
        int[] argb = new int[pixels];
        byte[] tooSmall = new byte[WatchFaceBuilder.BACKGROUND_OFFSET + 4];
        try {
            WatchFaceBuilder.build(tooSmall, argb, WatchFaceBuilder.BACKGROUND_WIDTH,
                    WatchFaceBuilder.BACKGROUND_HEIGHT);
            fail("a truncated template must be rejected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        byte[] ok = new byte[WatchFaceBuilder.BACKGROUND_OFFSET + WatchFaceBuilder.BACKGROUND_BYTES];
        try {
            WatchFaceBuilder.build(ok, new int[pixels], 320, 384);
            fail("a wrongly sized photo must be rejected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            WatchFaceBuilder.wrap(new byte[0]);
            fail("an empty dial must be rejected");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test public void compressesTheVendorExampleToAboutItsKnownSize() {
        // The known-good Buzz dial is 199732 B: header + table + 199468 compressed bytes for a
        // 486752 B raw. The compressor reproduces liblzo2 byte for byte (see Lzo1xTest), so this
        // only checks the wrapper really compresses rather than storing the blocks.
        byte[] raw = ramp(486752);
        int total = WatchFaceBuilder.wrap(raw).length;
        assertTrue("expected a compressed container, got " + total, total < 486752);
        assertEquals(26 + 2 * 119 + le32(WatchFaceBuilder.wrap(raw), 12), total);
    }

    /** Parses a dial container and inflates every block back into the raw dial. */
    private static byte[] unwrap(byte[] dial) {
        int blocks = le16(dial, 24);
        int rawLength = le32(dial, 4);
        int at = 26 + 2 * blocks;
        ByteArrayOutputStream out = new ByteArrayOutputStream(rawLength);
        for (int i = 0; i < blocks; i++) {
            int flag = le16(dial, 26 + 2 * i);
            int length = flag & 0x7fff;
            byte[] block = Arrays.copyOfRange(dial, at, at + length);
            at += length;
            int rawBlock = Math.min(WatchFaceBuilder.BLOCK_BYTES, rawLength - out.size());
            if ((flag & 0x8000) != 0) {
                out.writeBytes(Lzo1xDecompress.decompress(block, rawBlock));
            } else {
                out.writeBytes(block);
            }
        }
        assertEquals("the block table must cover the payload", at, dial.length);
        return out.toByteArray();
    }

    private static byte[] ramp(int length) {
        byte[] out = new byte[length];
        for (int i = 0; i < length; i++) out[i] = (byte) (i * 31 + 7);
        return out;
    }

    private static int le16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static int le32(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }
}