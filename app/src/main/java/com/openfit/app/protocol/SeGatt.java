package com.openfit.app.protocol;

import java.util.Arrays;

/**
 * Framing used by the SE ("Apricot") command channel on GATT service {@code 16186f00}.
 *
 * <p>This is a different transport from the Berry classic-SPP link. Both directions carry a
 * 2-byte little-endian packet number followed by a payload slice. Packet number {@code 0} is
 * reserved for six-byte control frames: {@code 00 00 01 <code> <payload:2 LE>}.
 *
 * <pre>
 * phone -> watch  00 00 00 00 &lt;chunkCount LE16&gt; 00     announce a transfer
 * watch -> phone  00 00 01 01 00 00                  ready, send the chunks
 * phone -> watch  &lt;index LE16&gt; &lt;chunk&gt;               one chunk (index starts at 1)
 * watch -> phone  00 00 01 03 00 00                  transfer complete
 * watch -> phone  00 00 01 02 00 00                  busy, drop/retry
 * watch -> phone  00 00 01 05 &lt;index LE16&gt;           resend that chunk
 * phone -> watch  00 00 01 00 00 00                  received a complete reply
 * </pre>
 *
 * <p>The vendor stack announces every outbound message first, waits for the ready control frame,
 * then sends the chunks. Incoming replies are announced the same way on the bulk characteristic
 * ({@code 16186f01}), acknowledged with the ready frame, and reassembled here.
 *
 * <p>Android-free so it can be unit tested on the JVM.
 */
public final class SeGatt {
    /** Received a complete reply; sent by the phone after the last incoming chunk. */
    public static final int CODE_RECEIVED = 0x0100;
    /** Ready, send the chunks (also the phone's answer to an incoming announce). */
    public static final int CODE_READY = 0x0101;
    /** Device is busy and did not accept the command. */
    public static final int CODE_BUSY = 0x0102;
    /** Transfer complete. */
    public static final int CODE_DONE = 0x0103;
    /** Resend the chunk named by the control payload (1-based). */
    public static final int CODE_RESEND = 0x0105;

    /** Packet-number prefix in front of every payload slice. */
    public static final int HEADER = 2;

    private SeGatt() {}

    /**
     * Number of bytes of command data that fit in one chunk at the given GATT MTU. The vendor
     * stack calls the usable value length {@code k} ({@code mtu - 3}) and puts {@code k - 2}
     * payload bytes behind the 2-byte packet number.
     */
    public static int chunkSize(int mtu) {
        return Math.max(1, mtu - 3 - HEADER);
    }

    public static int chunkCount(int length, int mtu) {
        int size = chunkSize(mtu);
        return Math.max(1, (length + size - 1) / size);
    }

    /** {@code 00 00 00 00 <count LE16> 00}: "I am about to send this many chunks". */
    public static byte[] announce(int chunkCount) {
        int count = Math.max(1, Math.min(chunkCount, 0xffff));
        return new byte[] {0, 0, 0, 0, (byte) count, (byte) (count >> 8)};
    }

    public static boolean isAnnounce(byte[] value) {
        return value != null && value.length >= 6
                && value[0] == 0 && value[1] == 0 && value[2] == 0 && value[3] == 0;
    }

    public static int announceCount(byte[] value) {
        return value == null || value.length < 6 ? 0 : le16(value, 4);
    }

    /** One chunk: {@code <index LE16> <data>}. */
    public static byte[] packet(int index, byte[] data, int offset, int length) {
        byte[] out = new byte[HEADER + length];
        out[0] = (byte) index;
        out[1] = (byte) (index >> 8);
        System.arraycopy(data, offset, out, HEADER, length);
        return out;
    }

    /** Packet number of a chunk frame. */
    public static int packetIndex(byte[] value) {
        return value == null || value.length < HEADER ? -1 : le16(value, 0);
    }

    /** Payload of a chunk frame with the packet number removed. */
    public static byte[] packetData(byte[] value) {
        if (value == null || value.length <= HEADER) return new byte[0];
        return Arrays.copyOfRange(value, HEADER, value.length);
    }

    public static boolean isControl(byte[] value) {
        return value != null && value.length >= 4
                && value[0] == 0 && value[1] == 0 && value[2] == 1;
    }

    /**
     * The control word at offset 2, e.g. {@code 0x0101} for "ready". The vendor writes this
     * field as a two-byte big-endian value ({@code 01 01}); only the trailing payload is
     * little-endian.
     */
    public static int controlCode(byte[] value) {
        if (value == null || value.length < 4) return -1;
        return ((value[2] & 0xff) << 8) | (value[3] & 0xff);
    }

    public static int controlPayload(byte[] value) {
        return value == null || value.length < 6 ? 0 : le16(value, 4);
    }

    public static byte[] control(int code) {
        return control(code, 0);
    }

    /** {@code 00 00 <code BE16> <payload LE16>}. */
    public static byte[] control(int code, int payload) {
        return new byte[] {
                0, 0, (byte) (code >> 8), (byte) code, (byte) payload, (byte) (payload >> 8)};
    }

    public static String controlLabel(int code) {
        switch (code) {
            case CODE_RECEIVED: return "received";
            case CODE_READY: return "ready";
            case CODE_BUSY: return "busy";
            case CODE_DONE: return "done";
            case CODE_RESEND: return "resend";
            default: return "unknown 0x" + Integer.toHexString(code);
        }
    }

    private static int le16(byte[] value, int offset) {
        return (value[offset] & 0xff) | ((value[offset + 1] & 0xff) << 8);
    }

    /**
     * Reassembles one incoming transfer. Out-of-order or oversized packets are dropped rather than
     * trusted, because the notification stream is not reliable.
     */
    public static final class Transfer {
        private static final int MAX_CHUNKS = 4096;

        private int expected;
        private byte[][] chunks;
        private int received;

        public void reset() {
            expected = 0;
            chunks = null;
            received = 0;
        }

        public boolean isActive() {
            return expected > 0;
        }

        public int expected() {
            return expected;
        }

        public int received() {
            return received;
        }

        /** Starts a transfer; returns false when the announced count is implausible. */
        public boolean start(int count) {
            if (count <= 0 || count > MAX_CHUNKS) {
                reset();
                return false;
            }
            expected = count;
            chunks = new byte[count][];
            received = 0;
            return true;
        }

        /**
         * Adds one chunk. Returns the complete payload once every announced chunk has arrived,
         * otherwise {@code null}. A completed transfer is reset automatically.
         */
        public byte[] accept(int index, byte[] data) {
            if (chunks == null || index < 1 || index > expected) return null;
            if (chunks[index - 1] == null) {
                chunks[index - 1] = data == null ? new byte[0] : data;
                received++;
            }
            if (received < expected) return null;
            int total = 0;
            for (byte[] chunk : chunks) {
                if (chunk == null) return null;
                total += chunk.length;
            }
            byte[] out = new byte[total];
            int at = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, out, at, chunk.length);
                at += chunk.length;
            }
            reset();
            return out;
        }
    }
}
