package com.openfit.app.protocol;

/**
 * A minimal LZO1X decompressor used only by the unit tests, so {@link WatchFaceBuilder#wrap} can be
 * verified to produce streams the watch can inflate. Translated from the reference
 * {@code lzo1x_decompress_safe}; it trusts its input (tests feed it known-good data) but still
 * fails loudly on malformed bytes via array bounds.
 */
final class Lzo1xDecompress {
    private Lzo1xDecompress() {}

    /** Decompresses {@code in} into exactly {@code outLen} bytes. */
    static byte[] decompress(byte[] in, int outLen) {
        byte[] out = new byte[outLen];
        int ip = 0;
        int op = 0;
        int t;

        int state;
        int first = in[ip++] & 0xff;
        if (first > 17) {
            t = first - 17;
            copy(out, op, in, ip, t);
            op += t;
            ip += t;
            t = in[ip++] & 0xff;
            // A very short first run jumps straight to a match; a longer one may be followed by
            // the special three-byte match form.
            state = (first - 17) < 4 ? MATCH : (t >= 16 ? MATCH : FIRST_MATCH);
        } else {
            t = first;
            state = t >= 16 ? MATCH : LITERAL;
        }

        for (;;) {
            switch (state) {
                case LITERAL: {
                    if (t == 0) {
                        while ((in[ip] & 0xff) == 0) {
                            t += 255;
                            ip++;
                        }
                        t += 15 + (in[ip++] & 0xff);
                    }
                    t += 3;
                    copy(out, op, in, ip, t);
                    op += t;
                    ip += t;
                    t = in[ip++] & 0xff;
                    state = t >= 16 ? MATCH : FIRST_MATCH;
                    break;
                }
                case FIRST_MATCH: {
                    // Special short match that follows a literal run: copy exactly three bytes.
                    int mPos = op - (1 + 0x0800) - (t >> 2) - ((in[ip++] & 0xff) << 2);
                    out[op++] = out[mPos++];
                    out[op++] = out[mPos++];
                    out[op++] = out[mPos];
                    state = MATCH_DONE;
                    break;
                }
                case MATCH: {
                    int mPos;
                    int len;
                    if (t >= 64) {                       // M2
                        len = (t >> 5) + 1;
                        mPos = op - 1 - ((t >> 2) & 7) - ((in[ip++] & 0xff) << 3);
                    } else if (t >= 32) {                // M3
                        len = t & 31;
                        if (len == 0) {
                            while ((in[ip] & 0xff) == 0) {
                                len += 255;
                                ip++;
                            }
                            len += 31 + (in[ip++] & 0xff);
                        }
                        len += 2;
                        mPos = op - 1 - ((le16(in, ip)) >> 2);
                        ip += 2;
                    } else if (t >= 16) {                // M4
                        mPos = op - ((t & 8) << 11);
                        len = t & 7;
                        if (len == 0) {
                            while ((in[ip] & 0xff) == 0) {
                                len += 255;
                                ip++;
                            }
                            len += 7 + (in[ip++] & 0xff);
                        }
                        mPos -= (le16(in, ip)) >> 2;
                        ip += 2;
                        if (mPos == op) {                // end marker
                            return out;
                        }
                        mPos -= 0x4000;
                        len += 2;
                    } else {                             // M1 reached after a match's trailing literals
                        len = 2;
                        mPos = op - 1 - (t >> 2) - ((in[ip++] & 0xff) << 2);
                    }
                    for (int k = 0; k < len; k++) {
                        out[op++] = out[mPos++];
                    }
                    state = MATCH_DONE;
                    break;
                }
                case MATCH_DONE: {
                    int trailing = in[ip - 2] & 3;
                    if (trailing == 0) {
                        t = in[ip++] & 0xff;
                        state = t >= 16 ? MATCH : LITERAL;
                    } else {
                        copy(out, op, in, ip, trailing);
                        op += trailing;
                        ip += trailing;
                        t = in[ip++] & 0xff;
                        state = MATCH;
                    }
                    break;
                }
                default:
                    throw new IllegalStateException("bad decoder state");
            }
        }
    }

    private static final int LITERAL = 0;
    private static final int FIRST_MATCH = 1;
    private static final int MATCH = 2;
    private static final int MATCH_DONE = 3;

    private static void copy(byte[] out, int op, byte[] in, int ip, int n) {
        for (int k = 0; k < n; k++) out[op + k] = in[ip + k];
    }

    private static int le16(byte[] b, int at) {
        return (b[at] & 0xff) | ((b[at + 1] & 0xff) << 8);
    }
}