package com.openfit.app.protocol;

import java.util.Arrays;

/**
 * A small, allocation-free LZO1X-1 compressor, ported from the reference
 * {@code lzo1x_1_compress} (minilzo). The Buzz stores each 4096-byte dial block compressed with
 * this exact format; a block whose two-byte table entry has bit15 set is inflated by the watch.
 *
 * <p>Only compression is implemented — the app never decompresses a dial. The output is a
 * standard LZO1X stream, so it is not byte-identical to the vendor's blocks but decodes to the same
 * bytes; that is all the watch needs.
 *
 * <p>Android-free so it can be exercised on the JVM.
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
        Arrays.fill(dict, 0);
        final int ipEnd = srcLen - 20;
        final int dstBase = dstOff;
        int op = dstOff;
        int ip = 4; // ti == 0 in the reference wrapper
        int ii = 0;

        for (;;) {
            if (ip >= ipEnd) break;
            int dindex = dindex1(src, srcOff, ip);
            int stored = dict[dindex];
            dict[dindex] = ip + 1;
            int mPos = 0;
            int mOff;
            boolean matched = false;
            if (stored != 0) {
                mPos = stored - 1;
                mOff = ip - mPos;
                matched = mOff != 0 && mOff <= M4_MAX_OFFSET
                        && (mOff <= M2_MAX_OFFSET
                                || src[srcOff + mPos + 3] == src[srcOff + ip + 3]);
            }
            if (!matched) {
                dindex = dindex2(dindex);
                stored = dict[dindex];
                dict[dindex] = ip + 1;
                if (stored != 0) {
                    mPos = stored - 1;
                    mOff = ip - mPos;
                    matched = mOff != 0 && mOff <= M4_MAX_OFFSET
                            && (mOff <= M2_MAX_OFFSET
                                    || src[srcOff + mPos + 3] == src[srcOff + ip + 3]);
                }
            }
            if (!matched
                    || src[srcOff + mPos] != src[srcOff + ip]
                    || src[srcOff + mPos + 1] != src[srcOff + ip + 1]
                    || src[srcOff + mPos + 2] != src[srcOff + ip + 2]
                    || src[srcOff + mPos + 3] != src[srcOff + ip + 3]) {
                // No match: advance a little faster the longer the pending literal run grows.
                ip += 1 + ((ip - ii) >> 5);
                continue;
            }

            // Flush the pending literals.
            int t = ip - ii;
            if (t != 0) {
                if (t <= 3) {
                    dst[op - 2] = (byte) (dst[op - 2] | t);
                    System.arraycopy(src, srcOff + ii, dst, op, t);
                    op += t;
                } else if (t <= 18) {
                    dst[op++] = (byte) (t - 3);
                    System.arraycopy(src, srcOff + ii, dst, op, t);
                    op += t;
                } else {
                    int tt = t - 18;
                    dst[op++] = 0;
                    while (tt > 255) {
                        tt -= 255;
                        dst[op++] = 0;
                    }
                    dst[op++] = (byte) tt;
                    System.arraycopy(src, ii + srcOff, dst, op, t);
                    op += t;
                }
            }

            // Extend the match to its full length.
            int mLen = 4;
            while (ip + mLen < ipEnd && src[srcOff + ip + mLen] == src[srcOff + mPos + mLen]) {
                mLen++;
            }
            mOff = ip - mPos;
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
        }

        // Flush the trailing literals and the end marker.
        int tail = srcLen - ii;
        if (tail > 0) {
            int base = srcLen - tail;
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
            System.arraycopy(src, srcOff + base, dst, op, tail);
            op += tail;
        }
        dst[op++] = (byte) (M4_MARKER | 1);
        dst[op++] = 0;
        dst[op++] = 0;
        return op - dstBase;
    }

    private static int dindex1(byte[] b, int base, int p) {
        long dx2 = ((((long) (b[base + p + 3] & 0xff) << 6) ^ (b[base + p + 2] & 0xff)) << 5)
                ^ (b[base + p + 1] & 0xff);
        long dx3 = (dx2 << 5) ^ (b[base + p] & 0xff);
        return (int) ((((0x21L * dx3) & 0xffffffffL) >> 5) & D_MASK);
    }

    private static int dindex2(int d) {
        return (d & (D_MASK & 0x7ff)) ^ (D_HIGH | 0x1f);
    }
}