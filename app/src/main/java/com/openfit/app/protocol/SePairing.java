package com.openfit.app.protocol;

import java.io.ByteArrayOutputStream;

/**
 * Account bind ("pairing") messages used by the SE / Apricot command channel.
 *
 * <p>The vendor app does not use the Berry classic-SPP private commands on {@code 16186f00}.
 * It sends {@link SeWear} protobuf messages whose ids come from {@code SEFunctionId} and whose
 * account body is {@code SEBindAccount}. This class encodes the messages the phone sends and
 * decodes the ones the watch answers with. It is Android-free so the encodings can be unit
 * tested on the JVM.
 *
 * <pre>
 * id 0   wear{bleConnectParmeterConfig}                   link greeting: 9A 06 00
 * id 16  bindAccount{requestBindingStatus}                is the watch already bound?
 * id 17  bindAccount{bindCheck{deviceVerify:true}}        ask the watch to accept this phone
 * id 18  bindAccount{bindResult{userId}}                  confirm the account / user token
 * id 19  bindAccount{verifyUserNumber{token}}             prove ownership on reconnect
 * </pre>
 */
public final class SePairing {
    // SEFunctionId values from the vendor protobuf.
    public static final int FUNCTION_MTU = 0;
    public static final int FUNCTION_INQUIRY_BINDING_STATUS = 16;
    public static final int FUNCTION_BINDING_CHECK = 17;
    public static final int FUNCTION_BINDING_RESULT = 18;
    public static final int FUNCTION_VERIFY_USER_NUMBER = 19;
    public static final int FUNCTION_UNBIND_REQUEST = 23;

    // SEBindAccount fields.
    public static final int ACCOUNT_REQUEST_BINDING_STATUS = 1;
    public static final int ACCOUNT_BIND_CHECK = 2;
    public static final int ACCOUNT_BIND_RESULT = 3;
    public static final int ACCOUNT_VERIFY_USER_NUMBER = 6;
    public static final int ACCOUNT_VERIFY_RESULT = 7;

    // SEBindCheck fields.
    public static final int CHECK_DEVICE_VERIFY = 1;
    public static final int CHECK_RANDOM_KEY = 2;
    public static final int CHECK_RESULT = 3;
    public static final int CHECK_DEVICE_NUMBER = 4;
    public static final int CHECK_MAC = 5;
    public static final int CHECK_SERIAL_NUMBER = 6;
    public static final int CHECK_FIRMWARE_VERSION = 7;
    public static final int CHECK_DEVICE_NAME = 8;

    // SEBindResult fields.
    public static final int RESULT_TYPE = 1;
    public static final int RESULT_USER_ID = 2;
    public static final int RESULT_PHONE_TYPE = 3;

    // SEVerifyResult fields. verify_result_type is a proto3 bool: true means "verified".
    public static final int VERIFY_RESULT_TYPE = 1;
    public static final int VERIFY_BINDING_STATUS = 2;

    // SEBindResultType / SEBindCheckResult values.
    public static final int BIND_RESULT_SUCCESS = 0;
    public static final int BIND_RESULT_REFUSE = 1;
    public static final int BIND_RESULT_OVER_TIME = 2;
    public static final int BIND_RESULT_VERIFICATION_FAILED = 3;

    private SePairing() {}

    /** {@code SEWear{id=0, bleConnectParmeterConfig{}}} = {@code 9A 06 00}. */
    public static byte[] connectParameterConfig() {
        return SeWear.connectParameterConfig();
    }

    /** {@code SEWear{id=16}} = {@code 08 10}: is the watch already bound to an account? */
    public static byte[] bindingStatusRequest() {
        return SeWear.wear(FUNCTION_INQUIRY_BINDING_STATUS);
    }

    /**
     * {@code SEWear{id=17, bindAccount{bindCheck{deviceVerify:true}}}} = {@code 08 11 1A 04 12 02
     * 08 01}: the exact command the vendor app sends on a first connection.
     */
    public static byte[] bindCheckRequest() {
        ByteArrayOutputStream check = new ByteArrayOutputStream();
        SeWear.writeVarintField(check, CHECK_DEVICE_VERIFY, 1);
        return withAccount(FUNCTION_BINDING_CHECK, ACCOUNT_BIND_CHECK, check.toByteArray());
    }

    /**
     * {@code SEWear{id=17, bindAccount{bindCheck{deviceVerify:false, bindRandomKey:key}}}}.
     * The vendor SDK also has this variant; retained so both probes can be compared on hardware.
     */
    public static byte[] bindCheckKeyRequest(String key) {
        ByteArrayOutputStream check = new ByteArrayOutputStream();
        if (key != null && !key.isEmpty()) {
            SeWear.writeMessage(check, CHECK_RANDOM_KEY,
                    key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return withAccount(FUNCTION_BINDING_CHECK, ACCOUNT_BIND_CHECK, check.toByteArray());
    }

    /**
     * {@code SEWear{id=18, bindAccount{bindResult{userId:token}}}}. The result type SUCCESS and
     * phone type ANDROID are both proto3 defaults, so only the user id is written.
     */
    public static byte[] bindResult(String userId) {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        if (userId != null && !userId.isEmpty()) {
            SeWear.writeMessage(result, RESULT_USER_ID,
                    userId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return withAccount(FUNCTION_BINDING_RESULT, ACCOUNT_BIND_RESULT, result.toByteArray());
    }

    /** {@code SEWear{id=19, bindAccount{verifyUserNumber:token}}}: prove the saved token. */
    public static byte[] verifyUserNumber(String token) {
        ByteArrayOutputStream account = new ByteArrayOutputStream();
        if (token != null && !token.isEmpty()) {
            SeWear.writeMessage(account, ACCOUNT_VERIFY_USER_NUMBER,
                    token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        // bindAccount is a message field, so an empty body is still emitted as 1A 00.
        return SeWear.wearMessage(FUNCTION_VERIFY_USER_NUMBER,
                SeWear.FIELD_BIND_ACCOUNT, account.toByteArray());
    }

    /** {@code SEWear{id=23}}: unbind the account (not sent automatically). */
    public static byte[] unbindRequest() {
        return SeWear.wear(FUNCTION_UNBIND_REQUEST);
    }

    /** The {@code bindAccount} body of a reply, or {@code null} when absent. */
    public static byte[] bindAccount(byte[] sewear) {
        return SeWear.readMessageField(sewear, SeWear.FIELD_BIND_ACCOUNT);
    }

    /** Watch reply to id 16; {@code null} when the reply has no account body. */
    public static Boolean bindingStatus(byte[] sewear) {
        byte[] account = bindAccount(sewear);
        if (account == null) return null;
        return SeWear.readVarintField(account, ACCOUNT_REQUEST_BINDING_STATUS) > 0;
    }

    /** Watch reply to id 17; {@code null} when malformed or missing. */
    public static BindCheck bindCheck(byte[] sewear) {
        byte[] account = bindAccount(sewear);
        if (account == null) return null;
        byte[] check = SeWear.readMessageField(account, ACCOUNT_BIND_CHECK);
        if (check == null) return null;
        return BindCheck.parse(check);
    }

    /** Watch reply to id 18; {@code -1} when the reply contains no bindResult message. */
    public static int bindResultType(byte[] sewear) {
        byte[] account = bindAccount(sewear);
        if (account == null) return -1;
        byte[] result = SeWear.readMessageField(account, ACCOUNT_BIND_RESULT);
        if (result == null) return -1;
        long type = SeWear.readVarintField(result, RESULT_TYPE);
        return type < 0 ? BIND_RESULT_SUCCESS : (int) type;
    }

    /**
     * Watch reply to id 19: {@code true} when {@code verify_result_type} is set, {@code false}
     * when it is unset (proto3 bool default), and {@code null} when the reply has no
     * {@code verify_result} message at all. The vendor SDK treats true as success.
     */
    public static Boolean verifyResult(byte[] sewear) {
        byte[] account = bindAccount(sewear);
        if (account == null) return null;
        byte[] result = SeWear.readMessageField(account, ACCOUNT_VERIFY_RESULT);
        if (result == null) return null;
        return SeWear.readVarintField(result, VERIFY_RESULT_TYPE) > 0;
    }

    public static String resultLabel(int value) {
        switch (value) {
            case BIND_RESULT_SUCCESS: return "success";
            case BIND_RESULT_REFUSE: return "refused by the watch";
            case BIND_RESULT_OVER_TIME: return "timed out on the watch";
            case BIND_RESULT_VERIFICATION_FAILED: return "verification failed";
            default: return "unknown result " + value;
        }
    }

    /** One-line, log-safe summary of a reply. Never includes the full private token. */
    public static String describe(byte[] sewear) {
        if (sewear == null) return "null";
        StringBuilder out = new StringBuilder("SEWear{id=")
                .append(SeWear.readId(sewear))
                .append(", error=").append(SeWear.readErrorCode(sewear));
        byte[] account = bindAccount(sewear);
        if (account != null) {
            long status = SeWear.readVarintField(account, ACCOUNT_REQUEST_BINDING_STATUS);
            if (status >= 0) out.append(", bound=").append(status > 0);
            BindCheck check = bindCheck(sewear);
            if (check != null) out.append(", ").append(check);
            byte[] bindResult = SeWear.readMessageField(account, ACCOUNT_BIND_RESULT);
            if (bindResult != null) {
                out.append(", bindResult=")
                        .append(resultLabel((int) valueOr(bindResult, RESULT_TYPE, 0)));
            }
            byte[] verify = SeWear.readMessageField(account, ACCOUNT_VERIFY_RESULT);
            if (verify != null) {
                out.append(", verifyResult=")
                        .append(valueOr(verify, VERIFY_RESULT_TYPE, 0) > 0);
            }
        }
        return out.append('}').toString();
    }

    /** Decoded {@code SEBindCheck}; absent proto3 fields read as their defaults. */
    public static final class BindCheck {
        public final boolean deviceVerify;
        public final int result;
        public final String randomKey;
        public final String deviceNumber;
        public final String mac;
        public final String serialNumber;
        public final String firmwareVersion;
        public final String deviceName;

        private BindCheck(boolean deviceVerify, int result, String randomKey, String deviceNumber,
                          String mac, String serialNumber, String firmwareVersion, String deviceName) {
            this.deviceVerify = deviceVerify;
            this.result = result;
            this.randomKey = randomKey;
            this.deviceNumber = deviceNumber;
            this.mac = mac;
            this.serialNumber = serialNumber;
            this.firmwareVersion = firmwareVersion;
            this.deviceName = deviceName;
        }

        static BindCheck parse(byte[] message) {
            return new BindCheck(
                    SeWear.readVarintField(message, CHECK_DEVICE_VERIFY) > 0,
                    (int) valueOr(message, CHECK_RESULT, BIND_RESULT_SUCCESS),
                    SeWear.readStringField(message, CHECK_RANDOM_KEY),
                    SeWear.readStringField(message, CHECK_DEVICE_NUMBER),
                    SeWear.readStringField(message, CHECK_MAC),
                    SeWear.readStringField(message, CHECK_SERIAL_NUMBER),
                    SeWear.readStringField(message, CHECK_FIRMWARE_VERSION),
                    SeWear.readStringField(message, CHECK_DEVICE_NAME));
        }

        @Override public String toString() {
            return "bindCheck{result=" + resultLabel(result)
                    + ", deviceVerify=" + deviceVerify
                    + ", name=" + deviceName
                    + ", mac=" + mac
                    + ", serial=" + serialNumber
                    + ", firmware=" + firmwareVersion + "}";
        }
    }

    private static byte[] withAccount(int id, int accountField, byte[] body) {
        ByteArrayOutputStream account = new ByteArrayOutputStream();
        SeWear.writeMessage(account, accountField, body == null ? new byte[0] : body);
        return SeWear.wearMessage(id, SeWear.FIELD_BIND_ACCOUNT, account.toByteArray());
    }

    private static long valueOr(byte[] message, int field, long fallback) {
        long value = SeWear.readVarintField(message, field);
        return value < 0 ? fallback : value;
    }
}
