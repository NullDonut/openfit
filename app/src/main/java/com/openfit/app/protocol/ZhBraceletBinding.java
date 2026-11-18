package com.openfit.app.protocol;

/**
 * The script-style command channel of the bundled {@code com.zjw.zhbraceletsdk} ("NavPlus")
 * stack. NoiseFit routes ColorFit Ultra Buzz, ColorFit Vision Buzz and ColorFit Ultra 2 through
 * {@code NavPlusConnectHandler}, which binds them with this SDK instead of the SE/Berry stacks.
 *
 * <p>Frames travel on service {@code 00000001} (write {@code 00000002}, notify
 * {@code 00000003}). They are a fixed 13-byte header followed by a length-prefixed payload:
 *
 * <pre>
 * AB 00 00 LL 00 00 00 00 01 00 KK 00 PP [payload]
 * </pre>
 *
 * <ul>
 *   <li>{@code LL} — frame length minus 8, big-endian ({@code payload.length + 5}).</li>
 *   <li>bytes 4..7 are zero in every phone-to-watch request.</li>
 *   <li>{@code 01} — direction, phone to watch ({@code 03} comes back from the watch).</li>
 *   <li>{@code KK} — command key. {@link #KEY_BIND} = {@code 0x6C} starts a bind; the watch
 *       answers with {@link #KEY_BIND_INFO} = {@code 0x34}.</li>
 *   <li>{@code PP} — payload length. The bind payload is a single type byte.</li>
 * </ul>
 *
 * <p>The type byte is {@link #TYPE_BIND} on a first pairing and {@link #TYPE_INQUIRY} when the
 * phone only wants to know whether the watch already has an app binding. The reply repeats the
 * type and carries a result: {@code 1} means bound/accepted, {@code 0} means refused/unbound.
 */
public final class ZhBraceletBinding {
    public static final int MAGIC = 0xAB;
    public static final int DIRECTION_PHONE = 1;
    public static final int DIRECTION_WATCH = 3;

    public static final int KEY_TIME_SYNC = 0x01;
    public static final int KEY_USER_INFO = 0x04;
    public static final int KEY_STATUS = 0x0F;
    /**
     * Command acknowledgement: payload {@code [command key] 00 [result]}. Observed on the
     * ColorFit Ultra Buzz answering the clock sync (key 1) and user info (key 4) after a bind.
     */
    public static final int KEY_COMMAND_ACK = 0x42;
    public static final int KEY_DEVICE_ANSWER = 0xAC;
    public static final int KEY_UNBIND = 0x33;

    public static final int KEY_BIND = 0x6C;
    public static final int KEY_BIND_INFO = 0x34;

    public static final int TYPE_BIND = 0;
    public static final int TYPE_INQUIRY = 1;

    private static final int HEADER_LENGTH = 13;

    private ZhBraceletBinding() {}

    /** Builds the bind/inquiry request the vendor SDK sends from {@code bindDevice(type)}. */
    public static byte[] bindRequest(int type) {
        return frame(KEY_BIND, new byte[] {(byte) type});
    }

    /**
     * Builds the time-sync frame the vendor sends first after the notifications are enabled
     * ({@code com.zjw.zhbraceletsdk.service.c.Q()} → {@code a.B()}). The payload packs the local
     * clock into four bytes:
     *
     * <pre>
     * p0 = yy &lt;&lt; 2 | MM &gt;&gt; 2
     * p1 = dd &lt;&lt; 1 | (MM &amp; 3) &lt;&lt; 6 | HH &gt;&gt; 4
     * p2 = (HH &amp; 15) &lt;&lt; 4 | mm &gt;&gt; 2
     * p3 = (mm &amp; 3) &lt;&lt; 6 | ss
     * </pre>
     */
    public static byte[] timeSyncFrame(int year, int month, int day,
                                       int hour, int minute, int second) {
        if (month < 1 || month > 12 || day < 1 || day > 31
                || hour < 0 || hour > 23 || minute < 0 || minute > 59
                || second < 0 || second > 59) {
            throw new IllegalArgumentException("invalid clock fields");
        }
        int yy = year % 100;
        byte[] payload = {
            (byte) ((yy << 2) | (month >> 2)),
            (byte) ((day << 1) | ((month & 3) << 6) | (hour >> 4)),
            (byte) (((hour & 15) << 4) | (minute >> 2)),
            (byte) (((minute & 3) << 6) | second)
        };
        return frame(KEY_TIME_SYNC, payload);
    }

    /**
     * Builds the user-info frame the vendor sends 50 ms after the time sync
     * ({@code c.setUserInfo} → {@code a.a(UserInfo)}). Bit 7 of the first payload byte is set for
     * a female profile ({@code !getSex()}), the low seven bits carry the age; height and weight
     * are one byte each in centimetres/kilograms, clamped to 1..255 like {@code h.setUserInfo}.
     */
    public static byte[] userInfoFrame(int age, int heightCm, int weightKg, boolean male) {
        byte[] payload = {
            (byte) (((male ? 0 : 1) << 7) | (age & 0x7F)),
            (byte) clampByte(heightCm),
            (byte) clampByte(weightKg),
            0
        };
        return frame(KEY_USER_INFO, payload);
    }

    private static int clampByte(int value) {
        if (value < 1) return 1;
        return Math.min(value, 255);
    }

    /** Parses one acknowledgement reply, or returns {@code null} for any other frame. */
    public static Ack parseAck(byte[] frame) {
        if (frame == null || frame.length < 14) return null;
        if ((frame[0] & 0xFF) != MAGIC) return null;
        if ((frame[8] & 0xFF) != DIRECTION_WATCH) return null;
        int key = frame[10] & 0xFF;
        if (key == KEY_STATUS) {
            return new Ack(key, -1, (frame[13] & 0xFF) == 1);
        }
        if (key == KEY_COMMAND_ACK && frame.length >= 16) {
            // Payload: the acknowledged command key, one reserved byte, then the result.
            return new Ack(key, frame[13] & 0xFF, (frame[15] & 0xFF) == 1);
        }
        if (key == KEY_DEVICE_ANSWER && frame.length >= 15) {
            return new Ack(key, frame[13] & 0xFF, (frame[14] & 0xFF) == 1);
        }
        return null;
    }

    /** Builds one phone-to-watch frame with the vendor's header layout. */
    public static byte[] frame(int key, byte[] payload) {
        int payloadLength = payload == null ? 0 : payload.length;
        byte[] out = new byte[HEADER_LENGTH + payloadLength];
        out[0] = (byte) MAGIC;
        out[1] = 0;
        int bodyLength = out.length - 8;
        out[2] = (byte) ((bodyLength >> 8) & 0xFF);
        out[3] = (byte) (bodyLength & 0xFF);
        // out[4..7] stay zero: the vendor leaves the session fields empty on every request.
        out[8] = (byte) DIRECTION_PHONE;
        out[9] = 0;
        out[10] = (byte) key;
        out[11] = (byte) ((payloadLength >> 8) & 0xFF);
        out[12] = (byte) (payloadLength & 0xFF);
        if (payload != null) System.arraycopy(payload, 0, out, HEADER_LENGTH, payload.length);
        return out;
    }

    /** Total frame length declared by the header, or {@code -1} when the header is malformed. */
    public static int declaredLength(byte[] frame) {
        if (frame == null || frame.length < 4) return -1;
        if ((frame[0] & 0xFF) != MAGIC) return -1;
        int body = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        int total = body + 8;
        return total < HEADER_LENGTH ? -1 : total;
    }

    /** Returns the bind-info reply, or {@code null} when this is not one. */
    public static BindInfo parseBindInfo(byte[] frame) {
        if (frame == null || frame.length < HEADER_LENGTH + 2) return null;
        if ((frame[0] & 0xFF) != MAGIC) return null;
        if ((frame[8] & 0xFF) != DIRECTION_WATCH) return null;
        if ((frame[10] & 0xFF) != KEY_BIND_INFO) return null;
        return new BindInfo(frame[13] & 0xFF, frame[14] & 0xFF);
    }

    /** One {@code KEY_BIND_INFO} reply: the type that was asked about and the watch's result. */
    public static final class BindInfo {
        public final int type;
        public final int result;

        public BindInfo(int type, int result) {
            this.type = type;
            this.result = result;
        }

        public boolean accepted() {
            return result == 1;
        }

        @Override public String toString() {
            return "BindInfo{type=" + type + ", result=" + result + '}';
        }
    }

    /** Human-readable name for a command key, for the on-device log. */
    public static String keyLabel(int key) {
        switch (key) {
            case KEY_TIME_SYNC: return "time sync";
            case KEY_USER_INFO: return "user info";
            case KEY_STATUS: return "status";
            case KEY_UNBIND: return "unbind";
            case KEY_BIND_INFO: return "bind info";
            case KEY_BIND: return "bind";
            case KEY_COMMAND_ACK: return "command ack";
            case KEY_DEVICE_ANSWER: return "device answer";
            default:
                return String.format(java.util.Locale.US, "key 0x%02X", key & 0xFF);
        }
    }

    /**
     * A generic acknowledgement: {@code KEY_STATUS} (0x0F) answers any set command, while
     * {@code KEY_DEVICE_ANSWER} (0xAC) echoes the original command key and its own status byte.
     */
    public static final class Ack {
        /** The reply key: {@link #KEY_STATUS} or {@link #KEY_DEVICE_ANSWER}. */
        public final int key;
        /** Original command key for {@code KEY_DEVICE_ANSWER}, else {@code -1}. */
        public final int refersTo;
        public final boolean success;

        public Ack(int key, int refersTo, boolean success) {
            this.key = key;
            this.refersTo = refersTo;
            this.success = success;
        }

        public String describe() {
            if (key == KEY_COMMAND_ACK) {
                return "command ack · " + ZhBraceletBinding.keyLabel(refersTo)
                        + " · " + (success ? "ok" : "refused");
            }
            String name = key == KEY_STATUS ? "status" : "device answer";
            String target = refersTo < 0 ? "" : " for key " + refersTo;
            return name + target + " · " + (success ? "ok" : "refused");
        }
    }
}
