package com.openfit.app.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The Berry vendor bind ("pairing") handshake.
 *
 * <p>Recovered from NoiseFit 5.5.0. The watch and the phone agree on a locally generated account
 * token; no key exchange or encryption is involved on this path.
 *
 * <pre>
 * phone  private cmd 2  UserIdBean(token, phoneName, systemVersion)
 * watch  private cmd 2  BerryDeviceInfoBean(flags: isBind, isSameUser)
 *   if !isBind          -> phone sends private cmd 3 (empty)
 *   if isBind + same    -> already paired, nothing to do
 *   if isBind + other   -> watch is paired to another account
 * watch  private cmd 3  one status byte
 *   0 -> phone sends private cmd 4 [1]
 *   1 invalid token, 2 refused, 3 already bound, 4 already reset,
 *   5 timed out on watch, 6 parse failure
 * watch  private cmd 4  one status byte; 0 = bind confirmed
 * </pre>
 *
 * Android-free so the encoders can be unit tested with plain JUnit.
 */
public final class BerryBinding {
    /** Status returned by the watch for private cmd 3. */
    public static final int BIND_OK = 0;
    public static final int BIND_INVALID_TOKEN = 1;
    public static final int BIND_REFUSED = 2;
    public static final int BIND_ALREADY_BOUND = 3;
    public static final int BIND_ALREADY_RESET = 4;
    public static final int BIND_OVER_TIME = 5;
    public static final int BIND_PARSE_FAILED = 6;

    private static final int USER_ID_MAX_CHARS = 100;
    private static final int PHONE_NAME_MAX_CHARS = 255;
    private static final int SYSTEM_VERSION_MAX_CHARS = 50;

    private BerryBinding() {}

    /**
     * Creates the persistent per-watch token the same way the reference app does:
     * {@code (uuid + randomSuffix + userId).substring(30)}. A token that would be shorter than
     * the requested start index is returned unchanged rather than throwing.
     */
    public static String newToken(String uuid, int randomSuffix, String userId) {
        String combined = String.valueOf(uuid) + randomSuffix + (userId == null ? "" : userId);
        return combined.length() > 30 ? combined.substring(30) : combined;
    }

    /** UserIdBean body: {@code [len][token][0xBB][len][phone][len][systemVersion]}. */
    public static byte[] userIdPayload(String token, String phoneName, String systemVersion) {
        String user = truncate(token, USER_ID_MAX_CHARS);
        String phone = truncate(phoneName, PHONE_NAME_MAX_CHARS);
        String version = truncate(systemVersion, SYSTEM_VERSION_MAX_CHARS);
        byte[] userBytes = user.getBytes(StandardCharsets.UTF_8);
        byte[] phoneBytes = phone.getBytes(StandardCharsets.UTF_8);
        byte[] versionBytes = version.getBytes(StandardCharsets.UTF_8);
        // The reference implementation writes String.length() (UTF-16 units), not the encoded
        // byte count. Mirror that exactly so non-ASCII names still match vendor behaviour.
        byte[] out = new byte[1 + userBytes.length + 1 + 1 + phoneBytes.length + 1 + versionBytes.length];
        int at = 0;
        out[at++] = (byte) user.length();
        System.arraycopy(userBytes, 0, out, at, userBytes.length);
        at += userBytes.length;
        out[at++] = (byte) 0xBB;
        out[at++] = (byte) phone.length();
        System.arraycopy(phoneBytes, 0, out, at, phoneBytes.length);
        at += phoneBytes.length;
        out[at++] = (byte) version.length();
        System.arraycopy(versionBytes, 0, out, at, versionBytes.length);
        return out;
    }

    /**
     * Private cmd 1: ask the watch for its protocol description. The reference app sends this
     * before the account exchange and waits for the reply before treating the link as connected.
     */
    public static byte[] protocolFrame(int serialNo) {
        return BerryFrame.privateCommand(serialNo, BerrySewear.PRIVATE_CMD_PROTOCOL, null).toBytes();
    }

    public static byte[] userIdFrame(int serialNo, String token, String phoneName, String systemVersion) {
        return BerryFrame.privateCommand(serialNo, BerrySewear.PRIVATE_CMD_USER_ID,
                userIdPayload(token, phoneName, systemVersion)).toBytes();
    }

    /** Private cmd 3: request a bind, empty body. */
    public static byte[] bindFrame(int serialNo) {
        return BerryFrame.privateCommand(serialNo, BerrySewear.PRIVATE_CMD_BIND, null).toBytes();
    }

    /** Private cmd 4: confirm (or reject) the bind. */
    public static byte[] bindResultFrame(int serialNo, boolean success) {
        return BerryFrame.privateCommand(serialNo, BerrySewear.PRIVATE_CMD_BIND_RESULT,
                new byte[] {(byte) (success ? 1 : 0)}).toBytes();
    }

    /** Ack for one received watch frame; the watch sends an 8-byte ack for every request. */
    public static byte[] ackFrame(int serialNo) {
        return BerryFrame.ackBytes(serialNo, 0);
    }

    public static String bindStatusLabel(int status) {
        switch (status) {
            case BIND_OK: return "watch accepted the bind";
            case BIND_INVALID_TOKEN: return "watch rejected the account token";
            case BIND_REFUSED: return "watch refused the bind";
            case BIND_ALREADY_BOUND: return "watch is already bound";
            case BIND_ALREADY_RESET: return "watch was already reset";
            case BIND_OVER_TIME: return "watch timed out waiting for confirmation";
            case BIND_PARSE_FAILED: return "watch could not parse the bind request";
            default: return "unknown bind status " + status;
        }
    }

    public static String bindResultLabel(int status) {
        return status == 0 ? "bind confirmed" : "bind confirmation rejected (" + status + ")";
    }

    /** Decoded private cmd 1 response (vendor {@code ProtocolBean}). */
    public static final class ProtocolInfo {
        public final int deviceType;
        public final String protocolVersion;
        public final int albumVersion;
        public final int platformType;

        private ProtocolInfo(int deviceType, String protocolVersion, int albumVersion, int platformType) {
            this.deviceType = deviceType;
            this.protocolVersion = protocolVersion;
            this.albumVersion = albumVersion;
            this.platformType = platformType;
        }

        /**
         * <pre>
         * 0..1  device type, LE
         * 2..4  protocol version, read as 4,3,2
         * 8     album version
         * 9     platform type
         * 10..11 album radius, LE
         * 12    feature bits
         * </pre>
         */
        public static ProtocolInfo parse(byte[] data) {
            if (data == null || data.length < 3) return null;
            int type = (data[0] & 0xff) | ((data[1] & 0xff) << 8);
            String version = data.length > 4
                    ? (data[4] & 0xff) + "." + (data[3] & 0xff) + "." + (data[2] & 0xff)
                    : "?";
            int albumVersion = data.length > 8 ? data[8] & 0xff : -1;
            int platformType = data.length > 9 ? data[9] & 0xff : -1;
            return new ProtocolInfo(type, version, albumVersion, platformType);
        }

        @Override public String toString() {
            return "type=" + deviceType + ", protocol=" + protocolVersion
                    + ", album=" + albumVersion + ", platform=" + platformType;
        }
    }

    /** Decoded private cmd 2 response. */
    public static final class DeviceInfo {
        public final int deviceType;
        public final String deviceVersion;
        public final String deviceMac;
        public final String btMac;
        public final String serialNumber;
        public final boolean isBind;
        public final boolean isSameUser;

        private DeviceInfo(int deviceType, String deviceVersion, String deviceMac, String btMac,
                           String serialNumber, boolean isBind, boolean isSameUser) {
            this.deviceType = deviceType;
            this.deviceVersion = deviceVersion;
            this.deviceMac = deviceMac;
            this.btMac = btMac;
            this.serialNumber = serialNumber;
            this.isBind = isBind;
            this.isSameUser = isSameUser;
        }

        /**
         * Decodes the vendor layout. Returns {@code null} when the payload is too short to hold
         * the mandatory fields.
         *
         * <pre>
         * 0..1   device type, LE
         * 2..4   version major/minor/patch (read as 4,3,2)
         * 5..10  device MAC, little-endian
         * 11..16 BT MAC, little-endian
         * 17     SN length
         * 18..   SN bytes (printed in reverse order by the vendor)
         * +1     flags: bit7 != set => same user, bit6 == set => already bound
         * </pre>
         */
        public static DeviceInfo parse(byte[] data) {
            if (data == null || data.length < 19) return null;
            int type = (data[0] & 0xff) | ((data[1] & 0xff) << 8);
            String version = "v" + (data[4] & 0xff) + "." + (data[3] & 0xff) + "." + (data[2] & 0xff);
            String deviceMac = mac(data, 5);
            String btMac = mac(data, 11);
            int snLength = data[17] & 0xff;
            StringBuilder sn = new StringBuilder();
            for (int i = snLength + 17; i > 17 && i < data.length; i--) {
                sn.append(String.format(Locale.ENGLISH, "%02X", data[i]));
            }
            int flagIndex = snLength + 18;
            boolean isBind = false;
            boolean isSameUser = false;
            if (flagIndex >= 0 && flagIndex < data.length) {
                int flags = data[flagIndex] & 0xff;
                // BleUtils.BinstrToIntArray is MSB-first: bit0=0x80, bit1=0x40.
                isSameUser = (flags & 0x80) == 0;
                isBind = (flags & 0x40) != 0;
            }
            return new DeviceInfo(type, version, deviceMac, btMac, sn.toString(), isBind, isSameUser);
        }

        private static String mac(byte[] data, int offset) {
            if (offset + 6 > data.length) return "";
            StringBuilder out = new StringBuilder();
            for (int i = offset + 5; i >= offset; i--) {
                if (out.length() > 0) out.append(':');
                out.append(String.format(Locale.ENGLISH, "%02X", data[i]));
            }
            return out.toString();
        }

        @Override public String toString() {
            return "type=" + deviceType + ", version=" + deviceVersion + ", deviceMac=" + deviceMac
                    + ", btMac=" + btMac + ", sn=" + serialNumber
                    + ", isBind=" + isBind + ", isSameUser=" + isSameUser;
        }
    }

    /** Hex for logs, capped so a noisy watch cannot flood the diagnostics report. */
    public static String hex(byte[] data) {
        return hex(data, 64);
    }

    public static String hex(byte[] data, int maxBytes) {
        if (data == null) return "null";
        int count = Math.min(data.length, Math.max(0, maxBytes));
        StringBuilder out = new StringBuilder(count * 3);
        for (int i = 0; i < count; i++) {
            if (i > 0) out.append(' ');
            out.append(String.format(Locale.ENGLISH, "%02X", data[i]));
        }
        if (count < data.length) out.append(" …(").append(data.length).append(" bytes)");
        return out.toString();
    }

    private static String truncate(String value, int maxChars) {
        if (value == null) return "";
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
