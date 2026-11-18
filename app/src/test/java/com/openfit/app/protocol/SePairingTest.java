package com.openfit.app.protocol;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SePairingTest {
    @Test public void connectParameterConfigIsTheVendorGreeting() {
        assertArrayEquals(new byte[] {(byte) 0x9A, 0x06, 0x00},
                SePairing.connectParameterConfig());
    }

    @Test public void deviceInfoProbeUsesId32() {
        // 0x20 = 32, a varint in field 1: 08 20.
        assertArrayEquals(new byte[] {0x08, 0x20}, SePairing.deviceInfoRequest());
        assertEquals(32, SeWear.readId(SePairing.deviceInfoRequest()));
    }

    @Test public void batteryProbeUsesId33() {
        // 0x21 = 33: the other read-only liveness probe for the SE command set.
        assertArrayEquals(new byte[] {0x08, 0x21}, SePairing.deviceBatteryRequest());
        assertEquals(33, SeWear.readId(SePairing.deviceBatteryRequest()));
    }

    @Test public void populatedConnectConfigCarriesTheMtu() {
        assertArrayEquals(new byte[] {
                (byte) 0x9A, 0x06, 0x03, // ble_connect_parmeter_config (99), 3 bytes
                0x08, (byte) 0xF7, 0x01  // config_change_mtu = 247
        }, SePairing.connectParameterConfig(247));
    }

    @Test public void bindingStatusRequestUsesSewearIdOne() {
        // SEWear.id is field 1 in the SE schema; the old Berry encoding (10 10) was change_mtu=16.
        assertArrayEquals(new byte[] {0x08, 0x10}, SePairing.bindingStatusRequest());
        assertEquals(16, SeWear.readId(SePairing.bindingStatusRequest()));
    }

    @Test public void explicitBindingStatusProbeCarriesTheFlag() {
        // A bool field is a varint, not a nested message: 08 01 inside bind_account.
        assertArrayEquals(new byte[] {
                0x08, 0x10,             // SEWear.id = 16
                0x1A, 0x02,             // bind_account (3), 2 bytes
                0x08, 0x01              // request_binding_status = true
        }, SePairing.bindingStatusRequestExplicit());
        assertEquals(Boolean.TRUE, SePairing.bindingStatus(
                SeWear.wearMessage(16, SeWear.FIELD_BIND_ACCOUNT,
                        new byte[] {0x08, 0x01})));
    }

    @Test public void bindCheckRequestAsksTheWatchToVerifyThisPhone() {
        assertArrayEquals(new byte[] {
                0x08, 0x11,             // SEWear.id = 17
                0x1A, 0x04,             // bind_account (3), 4 bytes
                0x12, 0x02,             // bind_check (2), 2 bytes
                0x08, 0x01              // device_verify = true
        }, SePairing.bindCheckRequest());
    }

    @Test public void bindResultCarriesOnlyTheUserId() {
        assertArrayEquals(new byte[] {
                0x08, 0x12,             // SEWear.id = 18
                0x1A, 0x07,             // bind_account, 7 bytes
                0x1A, 0x05,             // bind_result (3), 5 bytes
                0x12, 0x03, 'a', 'b', 'c'
        }, SePairing.bindResult("abc"));
    }

    @Test public void bindResultKeepsTheEmptyNestedMessage() {
        assertArrayEquals(new byte[] {0x08, 0x12, 0x1A, 0x02, 0x1A, 0x00},
                SePairing.bindResult(""));
    }

    @Test public void verifyUserNumberUsesFieldSix() {
        assertArrayEquals(new byte[] {
                0x08, 0x13,             // SEWear.id = 19
                0x1A, 0x05,             // bind_account, 5 bytes
                0x32, 0x03, 'a', 'b', 'c'
        }, SePairing.verifyUserNumber("abc"));
    }

    @Test public void bindCheckKeyVariantCarriesTheRandomKey() {
        byte[] message = SePairing.bindCheckKeyRequest("key");
        assertEquals(17, SeWear.readId(message));
        SePairing.BindCheck check = SePairing.bindCheck(message);
        assertNotNull(check);
        assertEquals("key", check.randomKey);
        assertFalse(check.deviceVerify);
        assertEquals(SePairing.BIND_RESULT_SUCCESS, check.result);
    }

    @Test public void bindingStatusDecodesBothValues() {
        byte[] notBound = reply(16, account(1, 0));
        byte[] bound = reply(16, account(1, 1));
        assertEquals(Boolean.FALSE, SePairing.bindingStatus(notBound));
        assertEquals(Boolean.TRUE, SePairing.bindingStatus(bound));
        assertNull(SePairing.bindingStatus(new byte[] {0x08, 0x10}));
    }

    @Test public void bindCheckDecodesTheWatchDescription() {
        ByteArrayOutputStream check = new ByteArrayOutputStream();
        field(check, 1, 1);                                  // device_verify = true
        field(check, 3, 0);                                  // bind_check_result = SUCCESS (omitted)
        string(check, 5, "D4:27:1D:30:44:9B");
        string(check, 6, "SN123");
        string(check, 7, "1.2.3");
        string(check, 8, "ColorFit Ultra Buzz");
        byte[] message = reply(17, accountWith(2, check.toByteArray()));

        SePairing.BindCheck decoded = SePairing.bindCheck(message);
        assertNotNull(decoded);
        assertTrue(decoded.deviceVerify);
        assertEquals(SePairing.BIND_RESULT_SUCCESS, decoded.result);
        assertEquals("D4:27:1D:30:44:9B", decoded.mac);
        assertEquals("SN123", decoded.serialNumber);
        assertEquals("1.2.3", decoded.firmwareVersion);
        assertEquals("ColorFit Ultra Buzz", decoded.deviceName);
    }

    @Test public void bindCheckRefusalAndMissingBodyAreReported() {
        SePairing.BindCheck refused = SePairing.bindCheck(
                reply(17, accountWith(2, new byte[] {0x18, 0x01})));
        assertNotNull(refused);
        assertEquals(SePairing.BIND_RESULT_REFUSE, refused.result);

        assertNull(SePairing.bindCheck(new byte[] {0x08, 0x11}));
        assertNull(SePairing.bindCheck(new byte[] {1, 2, 3}));
    }

    @Test public void bindResultTypeDefaultsToSuccessWhenTheFieldIsOmitted() {
        assertEquals(0, SePairing.bindResultType(reply(18, accountWith(3, new byte[0]))));
        assertEquals(SePairing.BIND_RESULT_REFUSE, SePairing.bindResultType(
                reply(18, accountWith(3, new byte[] {0x08, 0x01}))));
        assertEquals(-1, SePairing.bindResultType(new byte[] {0x08, 0x12}));
    }

    @Test public void verifyResultIsABoolAndTrueMeansVerified() {
        assertEquals(Boolean.FALSE, SePairing.verifyResult(reply(19, accountWith(7, new byte[0]))));
        assertEquals(Boolean.TRUE, SePairing.verifyResult(
                reply(19, accountWith(7, new byte[] {0x08, 0x01}))));
        assertEquals(Boolean.FALSE, SePairing.verifyResult(
                reply(19, accountWith(7, new byte[] {0x08, 0x00}))));
        assertNull(SePairing.verifyResult(new byte[] {0x08, 0x13}));
    }

    @Test public void refusedBindCheckDoesNotAllowTheBindResult() {
        SePairing.BindCheck refused = SePairing.bindCheck(
                reply(17, accountWith(2, new byte[] {0x18, 0x01})));
        assertFalse(SePairing.allowsBindResult(refused));
        assertFalse(SePairing.allowsBindResult(null));
    }

    @Test public void watchBeatBindCheckAllowsTheBindResult() {
        // Exact id-17 reply captured from a Noise Watch Beat (00:00:00:7A:55:0F, firmware 1.1.8).
        // The watch reports SUCCESS with device_verify clear and then waits for the id-18 result,
        // so the bind must continue instead of aborting on the flag.
        byte[] message = hex("08111A42124008001800220533303032362A1130303A30303A30303A37613A35353A3066"
                + "320531303030353A05312E312E3842124E6F69736546697420437265775F35353046");
        SePairing.BindCheck check = SePairing.bindCheck(message);
        assertNotNull(check);
        assertFalse(check.deviceVerify);
        assertEquals(SePairing.BIND_RESULT_SUCCESS, check.result);
        assertTrue(SePairing.allowsBindResult(check));
        assertEquals("00:00:00:7a:55:0f", check.mac);
        assertEquals("NoiseFit Crew_550F", check.deviceName);
        assertEquals("1.1.8", check.firmwareVersion);
    }

    @Test public void describeNeverThrowsOnMalformedReplies() {
        assertNotNull(SePairing.describe(new byte[0]));
        assertNotNull(SePairing.describe(new byte[] {(byte) 0xff, (byte) 0xff}));
        assertTrue(SePairing.describe(reply(17, accountWith(2,
                new byte[] {0x18, 0x01}))).contains("refused"));
        assertTrue(SePairing.describe(reply(16, account(1, 1))).contains("bound=true"));
    }

    private static byte[] reply(int id, byte[] account) {
        return SeWear.wearMessage(id, SeWear.FIELD_BIND_ACCOUNT, account);
    }

    private static byte[] account(int field, int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field(out, field, value);
        return out.toByteArray();
    }

    private static byte[] accountWith(int field, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SeWear.writeMessage(out, field, body);
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, int field, int value) {
        SeWear.writeTag(out, field, 0);
        SeWear.writeVarint(out, value);
    }

    private static void string(ByteArrayOutputStream out, int field, String value) {
        SeWear.writeMessage(out, field, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] hex(String value) {
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
