package com.openfit.app.protocol;

import java.util.Arrays;

/**
 * Berry protocol frame used by Noise/ColorFit watches on the
 * 16186f00-0000-1000-8000-00807f9b34fb GATT service.
 *
 * <pre>
 * offset size field
 * 0      4    head (little-endian magic)
 * 4      1    serial number, rolling 0..255
 * 5      1    event: 1 protobuf, 2 big data, 3 private, 4 log
 * 6      1    type:  3 protobuf, 2 private
 * 7      2    payload length, little-endian
 * 9      N    payload
 * </pre>
 *
 * Static description only: see PROTOCOL.md for provenance and open questions.
 */
public final class BerryFrame {
    public static final int HEAD_APP_REQ_DEV = 0x5A488888;
    public static final int HEAD_DEV_REQ_APP = 0x5A486666;
    public static final int HEAD_ASK = 0x5A489999;
    public static final int HEAD_DEV_LARGE_FILES = 0x5A481111;

    public static final int EVENT_PROTOBUF = 1;
    public static final int EVENT_BIG_DATA = 2;
    public static final int EVENT_PRIVATE = 3;
    public static final int EVENT_LOG = 4;

    public static final int TYPE_PRIVATE = 2;
    public static final int TYPE_PROTOBUF = 3;

    public static final int HEADER_SIZE = 9;
    private static final int MAX_PAYLOAD = 0xFFFF;

    private final int head;
    private final int serialNo;
    private final int event;
    private final int type;
    private final byte[] payload;

    private BerryFrame(int head, int serialNo, int event, int type, byte[] payload) {
        this.head = head;
        this.serialNo = serialNo & 0xff;
        this.event = event & 0xff;
        this.type = type & 0xff;
        this.payload = payload == null ? new byte[0] : payload;
    }

    public static BerryFrame of(int head, int serialNo, int event, int type, byte[] payload) {
        if (payload != null && payload.length > MAX_PAYLOAD) {
            throw new IllegalArgumentException("payload too large: " + payload.length);
        }
        return new BerryFrame(head, serialNo, event, type, payload);
    }

    /** Builds a phone-to-watch protobuf command whose payload is a SEWear message. */
    public static BerryFrame protobuf(int serialNo, byte[] sewear) {
        return of(HEAD_APP_REQ_DEV, serialNo, EVENT_PROTOBUF, TYPE_PROTOBUF, sewear);
    }

    /** Builds a phone-to-watch private command: cmdId(2) + length(2) + data, all little-endian. */
    public static BerryFrame privateCommand(int serialNo, int cmdId, byte[] data) {
        return of(HEAD_APP_REQ_DEV, serialNo, EVENT_PRIVATE, TYPE_PRIVATE, privatePayload(cmdId, data));
    }

    /** Acknowledgement to a watch request; empty payload, error byte only. */
    public static BerryFrame ack(int serialNo, int error) {
        return of(HEAD_ASK, serialNo, 0, 0, new byte[] {(byte) error, 0, 0});
    }

    public static byte[] privatePayload(int cmdId, byte[] data) {
        int size = data == null ? 0 : data.length;
        byte[] out = new byte[4 + size];
        out[0] = (byte) cmdId;
        out[1] = (byte) (cmdId >> 8);
        out[2] = (byte) size;
        out[3] = (byte) (size >> 8);
        if (size > 0) System.arraycopy(data, 0, out, 4, size);
        return out;
    }

    public byte[] toBytes() {
        byte[] out = new byte[HEADER_SIZE + payload.length];
        out[0] = (byte) head;
        out[1] = (byte) (head >> 8);
        out[2] = (byte) (head >> 16);
        out[3] = (byte) (head >> 24);
        out[4] = (byte) serialNo;
        out[5] = (byte) event;
        out[6] = (byte) type;
        out[7] = (byte) payload.length;
        out[8] = (byte) (payload.length >> 8);
        System.arraycopy(payload, 0, out, HEADER_SIZE, payload.length);
        return out;
    }

    /** Total frame length announced by {@code raw}, or -1 when the header is incomplete. */
    public static int frameLength(byte[] raw) {
        if (raw == null || raw.length < HEADER_SIZE) return -1;
        int declared = (raw[7] & 0xff) | ((raw[8] & 0xff) << 8);
        return HEADER_SIZE + declared;
    }

    /**
     * Parses one complete frame. Rejects headers whose declared length does not match the
     * supplied bytes, so callers can accumulate notifications first.
     */
    public static BerryFrame decode(byte[] raw) {
        int expected = frameLength(raw);
        if (expected < 0) throw new IllegalArgumentException("frame shorter than header");
        if (raw.length != expected) {
            throw new IllegalArgumentException("frame length " + raw.length + " != declared " + expected);
        }
        int head = (raw[0] & 0xff) | ((raw[1] & 0xff) << 8) | ((raw[2] & 0xff) << 16) | ((raw[3] & 0xff) << 24);
        byte[] payload = Arrays.copyOfRange(raw, HEADER_SIZE, raw.length);
        return new BerryFrame(head, raw[4] & 0xff, raw[5] & 0xff, raw[6] & 0xff, payload);
    }

    /** Rolling serial number helper: 255 wraps to 0. */
    public static int nextSerial(int serialNo) {
        return (serialNo + 1) & 0xff;
    }

    public int head() { return head; }

    public int serialNo() { return serialNo; }

    public int event() { return event; }

    public int type() { return type; }

    public byte[] payload() { return Arrays.copyOf(payload, payload.length); }

    public boolean isProtobuf() { return event == EVENT_PROTOBUF && type == TYPE_PROTOBUF; }

    public boolean isPrivate() { return event == EVENT_PRIVATE && type == TYPE_PRIVATE; }

    /** cmdId of a private frame, or -1 when this is not a private frame. */
    public int privateCmdId() {
        return isPrivate() && payload.length >= 2 ? (payload[0] & 0xff) | ((payload[1] & 0xff) << 8) : -1;
    }
}
