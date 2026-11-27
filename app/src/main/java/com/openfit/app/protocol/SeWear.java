package com.openfit.app.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimal protobuf (proto3) codec for the <b>SE / Apricot</b> {@code SEWear} envelope used on
 * the {@code 16186f00} GATT service
 * ({@code com.zh.ble.wear.protobuf.SEWear}, decoded from NoiseFit 5.5.0).
 *
 * <p>Field numbers are:
 * <pre>
 * 1   id                                   function id (SEFunctionId)
 * 2   change_mtu
 * 3   bind_account                         SEWear.bind_account
 * 5   system_time
 * 6   user_settings
 * 8   sporting
 * 9   fitness
 * 15  setting_menu
 * 99  ble_connect_parmeter_config
 * 100 error_code
 * </pre>
 *
 * <p>This is deliberately <b>not</b> the Berry classic-SPP schema: that one has {@code type=1},
 * {@code id=2} and {@code bind_account=4} ({@link BerrySewear}). Mixing the two was the reason
 * the first on-device SE command was interpreted as {@code change_mtu=16} and ignored.
 */
public final class SeWear {
    public static final int FIELD_ID = 1;
    public static final int FIELD_CHANGE_MTU = 2;
    public static final int FIELD_BIND_ACCOUNT = 3;
    public static final int FIELD_BLE_CONNECT_PARAMETER_CONFIG = 99;
    public static final int FIELD_ERROR_CODE = 100;

    private SeWear() {}

    /** {@code SEWear{id}}; {@code id == 0} is omitted, as proto3 requires. */
    public static byte[] wear(int id) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (id != 0) writeVarintField(out, FIELD_ID, id);
        return out.toByteArray();
    }

    /** {@code SEWear{id, payloadField: payload}}; the body is omitted when empty. */
    public static byte[] wear(int id, int payloadField, byte[] payload) {
        return wearMessage(id, payloadField, payload, payload != null && payload.length > 0);
    }

    /**
     * {@code SEWear{id, payloadField: payload}}, always emitting the payload field. A proto3
     * message field has explicit presence, so the vendor stack writes an empty nested message as
     * tag + zero length (for example id 18 yields {@code 08 12 1A 00}).
     */
    public static byte[] wearMessage(int id, int payloadField, byte[] payload) {
        return wearMessage(id, payloadField, payload, true);
    }

    private static byte[] wearMessage(int id, int payloadField, byte[] payload, boolean include) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (id != 0) writeVarintField(out, FIELD_ID, id);
        if (payloadField > 0 && include) {
            byte[] body = payload == null ? new byte[0] : payload;
            writeTag(out, payloadField, 2);
            writeVarint(out, body.length);
            out.write(body, 0, body.length);
        }
        return out.toByteArray();
    }

    /**
     * {@code SEWear{id=0, ble_connect_parmeter_config{}}} — the greeting the vendor stack sends
     * immediately after enabling notifications. Every field is a proto3 default, so this is
     * exactly {@code 9A 06 00}.
     */
    public static byte[] connectParameterConfig() {
        return wearMessage(0, FIELD_BLE_CONNECT_PARAMETER_CONFIG, new byte[0]);
    }

    /** Returns {@code SEWear.id}, or -1 when absent or unparsable. */
    public static int readId(byte[] sewear) {
        return (int) readVarintField(sewear, FIELD_ID);
    }

    /** {@code SEWear.error_code}; {@code 0} when absent (the proto3 default). */
    public static int readErrorCode(byte[] sewear) {
        long value = readVarintField(sewear, FIELD_ERROR_CODE);
        return value < 0 ? 0 : (int) value;
    }

    /** Human-readable {@code CommonProtos.SEErrorCode} label. */
    public static String errorLabel(int code) {
        switch (code) {
            case 0: return "no error";
            case 1: return "not supported";
            case 2: return "dependency not ready";
            case 3: return "set failed";
            case 4: return "parameter error";
            case 255: return "unknown error";
            default: return "error " + code;
        }
    }

    /**
     * Returns a top-level length-delimited field (a nested message) as a byte slice, or
     * {@code null} when the field is absent or the buffer is malformed.
     */
    public static byte[] readMessageField(byte[] message, int wantedField) {
        if (message == null) return null;
        int pos = 0;
        while (pos < message.length) {
            long tag = readVarint(message, pos);
            if (tag < 0) return null;
            pos += varintSize(tag);
            int field = (int) (tag >>> 3);
            int wireType = (int) (tag & 7);
            if (wireType == 0) {
                long value = readVarint(message, pos);
                if (value < 0) return null;
                pos += varintSize(value);
            } else if (wireType == 2) {
                long length = readVarint(message, pos);
                if (length < 0 || length > message.length) return null;
                pos += varintSize(length);
                if (pos + length > message.length) return null;
                if (field == wantedField) {
                    return Arrays.copyOfRange(message, pos, pos + (int) length);
                }
                pos += (int) length;
            } else if (wireType == 5) {
                pos += 4;
            } else if (wireType == 1) {
                pos += 8;
            } else {
                return null;
            }
            if (pos > message.length) return null;
        }
        return null;
    }

    /**
     * Collects <b>every</b> occurrence of a top-level length-delimited field, in wire order. This
     * is the repeated-message counterpart of {@link #readMessageField}, used by the watch-face
     * list ({@code SEWatchFaceItem.SEList.list}). Returns an empty list when the field is absent
     * or the buffer is malformed, so a partial reply never throws.
     */
    public static List<byte[]> readMessages(byte[] message, int wantedField) {
        List<byte[]> out = new ArrayList<>();
        if (message == null) return out;
        int pos = 0;
        while (pos < message.length) {
            long tag = readVarint(message, pos);
            if (tag < 0) return out;
            pos += varintSize(tag);
            int field = (int) (tag >>> 3);
            int wireType = (int) (tag & 7);
            if (wireType == 0) {
                long value = readVarint(message, pos);
                if (value < 0) return out;
                pos += varintSize(value);
            } else if (wireType == 2) {
                long length = readVarint(message, pos);
                if (length < 0 || length > message.length) return out;
                pos += varintSize(length);
                if (pos + length > message.length) return out;
                if (field == wantedField) {
                    out.add(Arrays.copyOfRange(message, pos, pos + (int) length));
                }
                pos += (int) length;
            } else if (wireType == 5) {
                pos += 4;
            } else if (wireType == 1) {
                pos += 8;
            } else {
                return out;
            }
            if (pos > message.length) return out;
        }
        return out;
    }

    /** Returns a top-level length-delimited field decoded as UTF-8, or {@code null}. */
    public static String readStringField(byte[] message, int wantedField) {
        byte[] raw = readMessageField(message, wantedField);
        return raw == null ? null : new String(raw, StandardCharsets.UTF_8);
    }

    /** Reads a top-level varint field; -1 when the field is absent or the buffer is malformed. */
    public static long readVarintField(byte[] message, int wantedField) {
        if (message == null) return -1;
        int pos = 0;
        while (pos < message.length) {
            long tag = readVarint(message, pos);
            if (tag < 0) return -1;
            pos += varintSize(tag);
            int field = (int) (tag >>> 3);
            int wireType = (int) (tag & 7);
            if (wireType == 0) {
                long value = readVarint(message, pos);
                if (value < 0) return -1;
                pos += varintSize(value);
                if (field == wantedField) return value;
            } else if (wireType == 2) {
                long length = readVarint(message, pos);
                if (length < 0) return -1;
                pos += varintSize(length);
                if (pos + length > message.length) return -1;
                if (field == wantedField) return -1; // length-delimited, not a varint
                pos += (int) length;
            } else if (wireType == 5) {
                pos += 4;
            } else if (wireType == 1) {
                pos += 8;
            } else {
                return -1;
            }
        }
        return -1;
    }

    public static void writeVarintField(ByteArrayOutputStream out, int field, int value) {
        if (value == 0) return;
        writeTag(out, field, 0);
        writeVarint(out, value);
    }

    public static void writeTag(ByteArrayOutputStream out, int field, int wireType) {
        writeVarint(out, (field << 3) | (wireType & 7));
    }

    public static void writeVarint(ByteArrayOutputStream out, int value) {
        long v = value & 0xffffffffL;
        while ((v & ~0x7fL) != 0) {
            out.write((int) ((v & 0x7f) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }

    /** Encodes a nested message as {@code tag + length + body}. */
    public static void writeMessage(ByteArrayOutputStream out, int field, byte[] body) {
        byte[] value = body == null ? new byte[0] : body;
        writeTag(out, field, 2);
        writeVarint(out, value.length);
        out.write(value, 0, value.length);
    }

    private static long readVarint(byte[] data, int pos) {
        long result = 0;
        int shift = 0;
        while (pos < data.length) {
            int b = data[pos++] & 0xff;
            result |= (long) (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift > 63) return -1;
        }
        return -1;
    }

    private static int varintSize(long value) {
        int size = 1;
        long v = value & 0xffffffffL;
        while ((v >>>= 7) != 0) size++;
        return size;
    }
}
