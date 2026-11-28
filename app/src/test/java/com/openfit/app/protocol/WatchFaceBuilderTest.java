package com.openfit.app.protocol;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WatchFaceBuilderTest {
    @Test public void wrapsTheRawDialIntoAnAcceptedContainer() {
        byte[] raw = ramp(486752);
        byte[] dial = WatchFaceBuilder.wrap(raw);

        // 119 blocks of 4096 (the last 3424): 26 + 2*119 + 486752.
        assertEquals(487016, dial.length);
        assertEquals(487016, WatchFaceBuilder.wrappedLength(raw.length));
        assertTrue(BuzzUpload.isDialContainer(dial));

        assertEquals("magic", 0xaf, dial[0] & 0xff);
        assertEquals(0x02, dial[3] & 0xff);
        assertEquals(486752, le32(dial, 4));   // raw length
        assertEquals(0, le32(dial, 8));        // raw CRC (not checked)
        assertEquals(486752, le32(dial, 12));  // stored length = raw length (all uncompressed)
        assertEquals(0, le32(dial, 16));
        assertEquals(0, le32(dial, 20));
        assertEquals(119, (dial[24] & 0xff) | ((dial[25] & 0xff) << 8));

        // Every block is stored uncompressed: bit15 clear, length 4096 except the 3424 tail.
        for (int i = 0; i < 118; i++) {
            assertEquals("block " + i, 4096, le16(dial, 26 + 2 * i));
        }
        assertEquals(3424, le16(dial, 26 + 2 * 118));
        assertEquals(0, le16(dial, 26 + 2 * 118) & 0x8000);

        // The stored payload is the raw dial, verbatim.
        assertArrayEquals(raw, Arrays.copyOfRange(dial, 26 + 2 * 119, dial.length));
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

        // The region now holds dodger blue in every pixel and nothing outside it moved.
        int regionAt = 26 + 2 * ((template.length + WatchFaceBuilder.BLOCK_BYTES - 1)
                / WatchFaceBuilder.BLOCK_BYTES) + WatchFaceBuilder.BACKGROUND_OFFSET;
        int blue = (((0x1e) >> 3) << 11) | (((0x90) >> 2) << 5) | (0xff >> 3);
        for (int i = 0; i < pixels; i++) {
            assertEquals("pixel " + i, blue, le16(dial, regionAt + 2 * i));
        }
        // A byte just past the background still carries the fill value.
        assertEquals((byte) 0x5a,
                dial[regionAt + WatchFaceBuilder.BACKGROUND_BYTES]);
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

    @Test public void wrappedLengthMatchesTheVendorBlockMath() {
        // The vendor example: raw 486752 -> 119 live blocks, but stored compressed to 199468,
        // so the real container is 26 + 2*119 + 199468 = 199732. Uncompressed it is larger.
        assertEquals(199732, 26 + 2 * 119 + 199468);
        assertEquals(487016, WatchFaceBuilder.wrappedLength(486752));
        assertEquals(26 + 2 + 4, WatchFaceBuilder.wrappedLength(4)); // one short block
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
