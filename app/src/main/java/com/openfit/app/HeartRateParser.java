package com.openfit.app;

/** Parser for the Bluetooth SIG Heart Rate Measurement characteristic (0x2A37). */
public final class HeartRateParser {
    private HeartRateParser() {}

    /** Returns beats per minute, or -1 for a malformed/unsupported measurement. */
    public static int parse(byte[] value) {
        if (value == null || value.length < 2) return -1;
        int flags = value[0] & 0xff;
        if ((flags & 0x01) == 0) return value[1] & 0xff;
        if (value.length < 3) return -1;
        return (value[1] & 0xff) | ((value[2] & 0xff) << 8);
    }
}
