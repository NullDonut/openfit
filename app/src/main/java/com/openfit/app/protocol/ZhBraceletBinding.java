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
}
