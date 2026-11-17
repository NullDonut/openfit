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

    @Test public void bindingStatusRequestIsWearId16() {
        assertArrayEquals(new byte[] {0x10, 0x10}, SePairing.bindingStatusRequest());
    }

    @Test public void bindCheckRequestAsksTheWatchToVerifyThisPhone() {
        assertArrayEquals(new byte[] {
                0x10, 0x11,             // SEWear.id = 17
                0x22, 0x04,             // bindAccount, 4 bytes
                0x12, 0x02,             // bindCheck, 2 bytes
                0x08, 0x01              // deviceVerify = true
        }, SePairing.bindCheckRequest());
    }

    @Test public void bindResultCarriesOnlyTheUserId() {
        assertArrayEquals(new byte[] {
                0x10, 0x12,             // SEWear.id = 18
                0x22, 0x07,             // bindAccount, 7 bytes
                0x1A, 0x05,             // bindResult, 5 bytes
                0x12, 0x03, 'a', 'b', 'c'
        }, SePairing.bindResult("abc"));
    }

    @Test public void verifyUserNumberUsesFieldSix() {
        assertArrayEquals(new byte[] {
                0x10, 0x13,             // SEWear.id = 19
                0x22, 0x05,             // bindAccount, 5 bytes
                0x32, 0x03, 'a', 'b', 'c'
        }, SePairing.verifyUserNumber("abc"));
    }

    @Test public void bindCheckKeyVariantCarriesTheRandomKey() {
        byte[] message = SePairing.bindCheckKeyRequest("key");
        assertEquals(17, BerrySewear.readId(message));
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
        assertNull(SePairing.bindingStatus(new byte[] {0x10, 0x10}));
    }

    @Test public void bindCheckDecodesTheWatchDescription() {
        ByteArrayOutputStream check = new ByteArrayOutputStream();
        field(check, 3, 0);                                  // bindCheckResult = SUCCESS (omitted)
        string(check, 5, "D4:27:1D:30:44:9B");
        string(check, 6, "SN123");
        string(check, 7, "1.2.3");
        string(check, 8, "ColorFit Ultra Buzz");
        byte[] message = reply(17, accountWith(2, check.toByteArray()));

        SePairing.BindCheck decoded = SePairing.bindCheck(message);
        assertNotNull(decoded);
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

        assertNull(SePairing.bindCheck(new byte[] {0x10, 0x11}));
        assertNull(SePairing.bindCheck(new byte[] {1, 2, 3}));
    }

    @Test public void bindResultTypeDefaultsToSuccessWhenTheFieldIsOmitted() {
        assertEquals(0, SePairing.bindResultType(reply(18, accountWith(3, new byte[0]))));
        assertEquals(SePairing.BIND_RESULT_REFUSE, SePairing.bindResultType(
                reply(18, accountWith(3, new byte[] {0x08, 0x01}))));
        assertEquals(-1, SePairing.bindResultType(new byte[] {0x10, 0x12}));
    }

    @Test public void verifyResultDecodesTheEnum() {
        assertEquals(0, SePairing.verifyResultType(reply(19, accountWith(7, new byte[0]))));
        assertEquals(1, SePairing.verifyResultType(
                reply(19, accountWith(7, new byte[] {0x08, 0x01}))));
        assertEquals(-1, SePairing.verifyResultType(new byte[] {0x10, 0x13}));
    }

    @Test public void describeNeverThrowsOnMalformedReplies() {
        assertNotNull(SePairing.describe(new byte[0]));
        assertNotNull(SePairing.describe(new byte[] {(byte) 0xff, (byte) 0xff}));
        assertTrue(SePairing.describe(reply(17, accountWith(2,
                new byte[] {0x18, 0x01}))).contains("refused"));
    }

    private static byte[] reply(int id, byte[] account) {
        return BerrySewear.wearWithMessage(0, id, BerrySewear.FIELD_BIND_ACCOUNT, account);
    }

    private static byte[] account(int field, int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        field(out, field, value);
        return out.toByteArray();
    }

    private static byte[] accountWith(int field, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BerrySewear.writeTag(out, field, 2);
        BerrySewear.writeVarint(out, body.length);
        out.write(body, 0, body.length);
        return out.toByteArray();
    }

    private static void field(ByteArrayOutputStream out, int field, int value) {
        BerrySewear.writeTag(out, field, 0);
        BerrySewear.writeVarint(out, value);
    }

    private static void string(ByteArrayOutputStream out, int field, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        BerrySewear.writeTag(out, field, 2);
        BerrySewear.writeVarint(out, bytes.length);
        out.write(bytes, 0, bytes.length);
    }
}
