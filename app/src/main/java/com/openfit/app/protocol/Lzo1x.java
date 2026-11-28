package com.openfit.app.protocol;

import java.util.Arrays;

/**
 * A small, allocation-free LZO1X-1 compressor, ported from the reference
 * {@code lzo1x_1_compress} (minilzo, integer dictionary variant). The Buzz stores each 4096-byte
 * dial block compressed with this exact format; a block whose two-byte table entry has bit15 set is
 * inflated by the watch.
 *
 * <p>The port deliberately follows the reference byte for byte: for the known-good Buzz dial it
 * reproduces all 119 vendor blocks exactly (verified against {@code liblzo2}). The watch's own
 * inflater is pickier than a general LZO decoder, so emitting the same stream the vendor app emits
 * matters.
 *
 * <p>Only compression is implemented — the app never decompresses a dial. Android-free so it can be
 * exercised on the JVM.
 */
public final class Lzo1x {
    private Lzo1x() {}

    // LZO1X-1 constants (lzo_conf.h / lzo1x_1.c).
    private static final int M2_MAX_OFFSET = 0x0800;
    private static final int M3_MAX_OFFSET = 0x4000;
    private static final int M4_MAX_OFFSET = 0xbfff;
    private static final int M2_MAX_LEN = 8;
    private static final int M3_MAX_LEN = 33;
    private static final int M4_MAX_LEN = 9;
    private static final int M3_MARKER = 32;
    private static final int M4_MARKER = 16;

    private static final int D_BITS = 14;
    private static final int D_MASK = (1 << D_BITS) - 1;   // 0x3fff
    private static final int D_HIGH = (D_MASK >> 1) + 1;  // 0x2000
    /** Dictionary slots, one int per slot. */
    public static final int DICT_SLOTS = 1 << D_BITS;

    // DINDEX(dv,p) = DM(DMUL(0x1824429d,dv) >> (32-D_BITS)); the 32-bit product is truncated.
    private static final long HASH_MUL = 0x1824429dL;

    /**
     * Worst-case output for {@code n} input bytes (the reference bound is
     * {@code n + n/16 + 64 + 3}); rounded up so callers can share one scratch buffer.
     */
    public static int maxCompressedLength(int n) {
        return n + n / 16 + 64 + 3 + 32;
    }

    /** Allocates a fresh dictionary; reuse it across blocks with {@link #compress}. */
    public static int[] newDictionary() {
        return new int[DICT_SLOTS];
    }

    /**
     * Compresses {@code src[srcOff .. srcOff+srcLen)} into {@code dst[dstOff ...]} and returns the
     * number of bytes written. {@code dst} must have room for
     * {@link #maxCompressedLength(int) maxCompressedLength(srcLen)} bytes and {@code dict} must come
     * from {@link #newDictionary()}.
     */
    public static int compress(byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff, int[] dict) {
        // The reference wrapper only starts the compressor when its loop-overlap guard
        // `(t + ll) >> 5` is non-zero; with a single block that means blocks shorter than 32
        // bytes are emitted as one literal run. Buzz blocks are 4096 bytes, but matching the
        // reference exactly keeps this usable for any block size.
        if (srcLen < 32) {
            return tail(src, srcOff, srcLen, srcOff, dst, dstOff, dstOff);
        }
        Arrays.fill(dict, 0);
        final int dstBase = dstOff;
        final int ipEnd = srcOff + srcLen - 20;
        final int srcEnd = srcOff + srcLen;
        int op = dstOff;
        int ip = srcOff + 4; // the reference starts four bytes in, then advances once before hashing
        int ii = srcOff;
        boolean advance = true;

        for (;;) {
            if (advance) {
                ip += 1 + ((ip - ii) >> 5);
            }
            advance = true;

            // Find a 4-byte match. The reference uses one dictionary probe per position, keyed by a
            // multiplicative hash of the four bytes. The dictionary holds offsets from the start of
            // the block and starts zeroed, so an untouched slot means "candidate at offset 0".
            int mPos;
            int mOff;
            for (;;) {
                if (ip >= ipEnd) {
                    return tail(src, srcOff, srcLen, ii, dst, dstBase, op);
                }
                int relative = ip - srcOff;
                int dv = le32(src, ip);
                int index = (int) ((((HASH_MUL * (dv & 0xffffffffL)) & 0xffffffffL) >>> (32 - D_BITS))
                        & D_MASK);
                int stored = dict[index];
                dict[index] = relative;
                mPos = srcOff + stored;
                if (le32(src, mPos) == dv) {
                    mOff = relative - stored;
                    break;
                }
                ip += 1 + ((ip - ii) >> 5);
            }

            // Flush the pending literals.
            int t = ip - ii;
            if (t != 0) {
                if (t <= 3) {
                    dst[op - 2] = (byte) (dst[op - 2] | t);
                    System.arraycopy(src, ii, dst, op, t);
                    op += t;
                } else if (t <= 18) {
                    dst[op++] = (byte) (t - 3);
                    System.arraycopy(src, ii, dst, op, t);
                    op += t;
                } else {
                    int tt = t - 18;
                    dst[op++] = 0;
                    while (tt > 255) {
                        tt -= 255;
                        dst[op++] = 0;
                    }
                    dst[op++] = (byte) tt;
                    System.arraycopy(src, ii, dst, op, t);
                    op += t;
                }
            }

            // Extend the match exactly like the reference's 64-bit fast path: compare eight
            // bytes at a time and stop as soon as the hashing window ends.
            int mLen = 4;
            int v = le32(src, ip + mLen) ^ le32(src, mPos + mLen);
            if (v == 0) {
                boolean stopped = false;
                do {
                    mLen += 4;
                    v = le32(src, ip + mLen) ^ le32(src, mPos + mLen);
                    if (v != 0) break;
                    mLen += 4;
                    v = le32(src, ip + mLen) ^ le32(src, mPos + mLen);
                    if (ip + mLen >= ipEnd) {
                        stopped = true;
                        break;
                    }
                } while (v == 0);
                if (!stopped) {
                    mLen += Integer.numberOfTrailingZeros(v) >>> 3;
                }
            } else {
                mLen += Integer.numberOfTrailingZeros(v) >>> 3;
            }
            ip += mLen;
            ii = ip;

            // Emit the match.
            if (mLen <= M2_MAX_LEN && mOff <= M2_MAX_OFFSET) {
                mOff -= 1;
                dst[op++] = (byte) (((mLen - 1) << 5) | ((mOff & 7) << 2));
                dst[op++] = (byte) (mOff >> 3);
            } else if (mOff <= M3_MAX_OFFSET) {
                mOff -= 1;
                if (mLen <= M3_MAX_LEN) {
                    dst[op++] = (byte) (M3_MARKER | (mLen - 2));
                } else {
                    mLen -= M3_MAX_LEN;
                    dst[op++] = (byte) M3_MARKER;
                    while (mLen > 255) {
                        mLen -= 255;
                        dst[op++] = 0;
                    }
                    dst[op++] = (byte) mLen;
                }
                dst[op++] = (byte) (mOff << 2);
                dst[op++] = (byte) (mOff >> 6);
            } else {
                mOff -= 0x4000;
                if (mLen <= M4_MAX_LEN) {
                    dst[op++] = (byte) (M4_MARKER | ((mOff >> 11) & 8) | (mLen - 2));
                } else {
                    mLen -= M4_MAX_LEN;
                    dst[op++] = (byte) (M4_MARKER | ((mOff >> 11) & 8));
                    while (mLen > 255) {
                        mLen -= 255;
                        dst[op++] = 0;
                    }
                    dst[op++] = (byte) mLen;
                }
                dst[op++] = (byte) (mOff << 2);
                dst[op++] = (byte) (mOff >> 6);
            }

            // The reference jumps straight back to the hashing step after a match, without the
            // literal-skip advance.
            advance = false;
        }
    }

    /** Flushes the trailing literals and the end marker, mirroring the reference wrapper. */
    private static int tail(byte[] src, int srcOff, int srcLen, int ii,
                            byte[] dst, int dstBase, int op) {
        int tail = srcOff + srcLen - ii;
        if (tail > 0) {
            if (op == dstBase && tail <= 238) {
                dst[op++] = (byte) (17 + tail);
            } else if (tail <= 3) {
                dst[op - 2] = (byte) (dst[op - 2] | tail);
            } else if (tail <= 18) {
                dst[op++] = (byte) (tail - 3);
            } else {
                int tt = tail - 18;
                dst[op++] = 0;
                while (tt > 255) {
                    tt -= 255;
                    dst[op++] = 0;
                }
                dst[op++] = (byte) tt;
            }
            System.arraycopy(src, ii, dst, op, tail);
            op += tail;
        }
        dst[op++] = (byte) (M4_MARKER | 1);
        dst[op++] = 0;
        dst[op++] = 0;
        return op - dstBase;
    }

    private static int le32(byte[] b, int at) {
        return (b[at] & 0xff) | ((b[at + 1] & 0xff) << 8)
                | ((b[at + 2] & 0xff) << 16) | ((b[at + 3] & 0xff) << 24);
    }

    }