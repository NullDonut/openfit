package com.openfit.app.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Minimal protobuf (proto3) encoder/decoder for the Berry {@code SEWear} envelope.
 *
 * <p>Only the envelope fields needed to address a command are typed here; command bodies are
 * passed through as already-encoded protobuf messages, because their inner field numbers have
 * not all been recovered (see PROTOCOL.md). Not dependent on Android or protobuf runtime.
 */
public final class BerrySewear {
    // SEWear.Type
    public static final int TYPE_OTHER = 0;
    public static final int TYPE_ACCOUNT = 1;
    public static final int TYPE_DEVICE = 2;
    public static final int TYPE_SYSTEM_TIME = 3;
    public static final int TYPE_USER_SETTINGS = 4;
    public static final int TYPE_SPORTING = 5;
    public static final int TYPE_FITNESS = 6;
    public static final int TYPE_GNSS = 7;
    public static final int TYPE_MICRO_FUNCTION = 8;
    public static final int TYPE_NOTIFICATION = 9;
    public static final int TYPE_MUSIC = 10;
    public static final int TYPE_SETTING_MENU = 11;
    public static final int TYPE_FACTORY = 12;
    public static final int TYPE_ALEXA = 13;
    public static final int TYPE_NEW_WATCH_FACE = 14;
    public static final int TYPE_NEW_WEATHER = 15;
    public static final int TYPE_CHILDREN = 16;
    public static final int TYPE_AIFUNCTION = 17;
    public static final int TYPE_TV_REMOTE = 18;

    // SEWear fields
    public static final int FIELD_TYPE = 1;
    public static final int FIELD_ID = 2;
    public static final int FIELD_BIND_ACCOUNT = 4;
    public static final int FIELD_BLE_CONNECT_PARAMETER_CONFIG = 99;
    public static final int FIELD_ERROR_CODE = 100;

    // Private (event=3) command ids observed in the reference implementation.
    public static final int PRIVATE_CMD_PROTOCOL = 1;
    public static final int PRIVATE_CMD_USER_ID = 2;
    public static final int PRIVATE_CMD_BIND = 3;
    public static final int PRIVATE_CMD_BIND_RESULT = 4;

    private BerrySewear() {}

    /** SEWear{type, id}. {@code id == 0} is omitted, as proto3 requires. */
    public static byte[] wear(int type, int id) {
        return wear(type, id, 0, null);
    }

    /**
     * SEWear{type, id, payloadField: payload}. Pass {@code payloadField == 0} for no body.
     * The caller owns encoding of {@code payload} (a nested message without a length prefix).
     */
    public static byte[] wear(int type, int id, int payloadField, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (type != 0) writeVarintField(out, FIELD_TYPE, type);
        if (id != 0) writeVarintField(out, FIELD_ID, id);
        if (payloadField > 0 && payload != null && payload.length > 0) {
            writeTag(out, payloadField, 2);
            writeVarint(out, payload.length);
            out.write(payload, 0, payload.length);
        }
        return out.toByteArray();
    }

    /**
     * Like {@link #wear(int, int, int, byte[])}, but always emits the payload field, including
     * when the nested message is empty. The vendor stack encodes {@code bindCheck{}} and
     * {@code bleConnectParmeterConfig{}} this way.
     */
    public static byte[] wearWithMessage(int type, int id, int payloadField, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (type != 0) writeVarintField(out, FIELD_TYPE, type);
        if (id != 0) writeVarintField(out, FIELD_ID, id);
        if (payloadField > 0) {
            byte[] body = payload == null ? new byte[0] : payload;
            writeTag(out, payloadField, 2);
            writeVarint(out, body.length);
            out.write(body, 0, body.length);
        }
        return out.toByteArray();
    }

    /** Account/bind-state request: SEWear{ACCOUNT, id=0}. */
    public static byte[] bindStateRequest() {
        return wear(TYPE_ACCOUNT, 0);
    }

    /**
     * The command the vendor stack sends immediately after enabling notifications:
     * {@code SEWear{id=0, bleConnectParmeterConfig{}}}. Because every proto3 default is
     * omitted this is the three bytes {@code 9A 06 00}.
     */
    public static byte[] connectParameterConfig() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeTag(out, FIELD_BLE_CONNECT_PARAMETER_CONFIG, 2);
        writeVarint(out, 0);
        return out.toByteArray();
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

    /** Returns SEWear.type, or -1 when absent or unparsable. */
    public static int readType(byte[] sewear) {
        return (int) readVarintField(sewear, FIELD_TYPE);
    }

    /** Returns SEWear.id, or -1 when absent or unparsable. */
    public static int readId(byte[] sewear) {
        return (int) readVarintField(sewear, FIELD_ID);
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

    /** Returns a top-level length-delimited field decoded as UTF-8, or {@code null}. */
    public static String readStringField(byte[] message, int wantedField) {
        byte[] raw = readMessageField(message, wantedField);
        return raw == null ? null : new String(raw, StandardCharsets.UTF_8);
    }

    /** SEWear.errorCode; {@code 0} when absent (the proto3 default). */
    public static int readErrorCode(byte[] sewear) {
        long value = readVarintField(sewear, FIELD_ERROR_CODE);
        return value < 0 ? 0 : (int) value;
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
