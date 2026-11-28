package com.openfit.app.protocol;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;

/**
 * Locks the compressor to the reference {@code lzo1x_1_compress} bitstream. The expected bytes
 * below were produced by {@code liblzo2} 2.10; the watch's inflater only accepts that vendor
 * stream, so any divergence here is a real regression.
 */
public class Lzo1xTest {
    @Test public void blocksShorterThanThirtyTwoBytesAreOneLiteralRun() {
        // The reference wrapper only enters the compressor once (t + ll) >> 5 is non-zero, so a
        // short block is emitted as a single literal run instead of being compressed.
        assertArrayEquals(hex("2f000000000000000000000000000000000000000000000000000000000000110000"),
                compress(new byte[30]));
        assertArrayEquals(hex("3000000000000000000000000000000000000000000000000000000000000000110000"),
                compress(new byte[31]));
    }

    @Test public void compressesOnceTheBlockReachesThirtyTwoBytes() {
        assertArrayEquals(hex("0200000000002a10000c000000000000000000000000000000110000"),
                compress(new byte[32]));
    }

    @Test public void matchesTheReferenceForRealBuzzBlockSizes() {
        // 4096 is the normal block size; 3424 is the final block of the 486752-byte Buzz dial.
        assertArrayEquals(hex("02000000000020000000000000000000000000000000da10000c000000000000000000000000000000110000"),
                compress(new byte[4096]));
        assertArrayEquals(hex("02000000000020000000000000000000000000003810000c000000000000000000000000000000110000"),
                compress(new byte[3424]));
    }

    private static byte[] compress(byte[] in) {
        byte[] out = new byte[Lzo1x.maxCompressedLength(in.length)];
        int n = Lzo1x.compress(in, 0, in.length, out, 0, Lzo1x.newDictionary());
        return Arrays.copyOf(out, n);
    }

    private static byte[] hex(String text) {
        String h = text.replace(" ", "");
        byte[] bytes = new byte[h.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }
}