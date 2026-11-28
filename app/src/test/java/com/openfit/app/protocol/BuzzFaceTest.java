package com.openfit.app.protocol;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BuzzFaceTest {
    /** A ZhBracelet GET_INSTALLED_LIST request: WearPacket{type=4, id=0}. */
    @Test public void requestIsTypeWatchFaceAndGetInstalledList() {
        assertArrayEquals(new byte[] {0x08, 0x04, 0x10, 0x00}, BuzzFace.requestInstalledList());
    }

    @Test public void typeMustBeWatchFace() {
        assertTrue(BuzzFace.isWatchFace(hex("08 04 10 00")));
        assertFalse(BuzzFace.isWatchFace(hex("08 02 10 00")));
        assertFalse(BuzzFace.isWatchFace(null));
    }

    /**
     * Reply with {@code WatchFace.watch_face_list} (field 1), the shape the bundled SDK's own
     * handler reads: two items with id, name, is_current and can_remove.
     */
    @Test public void parsesWatchFaceItemList() {
        byte[] reply = hex(
                "08 04 10 00 32 26 0a 24"
                + " 0a 11 0a 02 77 31 12 07 43 6c 61 73 73 69 63 18 01 20 00"
                + " 0a 0f 0a 02 77 32 12 05 53 70 6f 72 74 18 00 20 01");
        List<SeCommands.WatchFace> faces = BuzzFace.parseInstalledList(reply);
        assertEquals(2, faces.size());
        assertEquals("w1", faces.get(0).id);
        assertEquals("Classic", faces.get(0).name);
        assertTrue(faces.get(0).current);
        assertFalse(faces.get(0).removable);
        assertEquals("w2", faces.get(1).id);
        assertEquals("Sport", faces.get(1).name);
        assertFalse(faces.get(1).current);
        assertTrue(faces.get(1).removable);
    }

    /** A firmware that reports ids only may use {@code WatchFace.info_list} (field 8). */
    @Test public void parsesWatchFaceInfoList() {
        byte[] reply = hex(
                "08 04 10 00 32 1c 42 1a"
                + " 0a 0b 0a 09 62 75 69 6c 74 69 6e 2d 31"
                + " 0a 0b 0a 09 62 75 69 6c 74 69 6e 2d 32");
        List<SeCommands.WatchFace> faces = BuzzFace.parseInstalledList(reply);
        assertEquals(2, faces.size());
        assertEquals("builtin-1", faces.get(0).id);
        assertEquals("", faces.get(0).name);
        assertEquals("builtin-2", faces.get(1).id);
        assertEquals("", faces.get(1).name);
        assertFalse(faces.get(0).current);
        assertFalse(faces.get(1).removable);
    }

    /** The Buzz sends the literal string "null" for faces without a vendor label. */
    @Test public void literalNullNameIsTreatedAsEmpty() {
        byte[] reply = hex(
                "08 04 10 00 32 13"
                + " 0a 11"
                + " 0a 0f 0a 03 61 62 63 12 04 6e 75 6c 6c 18 00 20 00");
        List<SeCommands.WatchFace> faces = BuzzFace.parseInstalledList(reply);
        assertEquals(1, faces.size());
        assertEquals("abc", faces.get(0).id);
        assertEquals("", faces.get(0).name);
    }

    @Test public void unexpectedOrMalformedRepliesYieldNoFaces() {
        assertTrue(BuzzFace.parseInstalledList(new byte[0]).isEmpty());
        assertTrue(BuzzFace.parseInstalledList(null).isEmpty());
        // A WatchFace with no payload oneof: just type and id.
        assertTrue(BuzzFace.parseInstalledList(hex("08 04 10 00")).isEmpty());
        // Not a watch-face packet at all.
        assertTrue(BuzzFace.parseInstalledList(hex("08 02 10 01")).isEmpty());
        // Truncated nested list: skipped rather than thrown.
        assertTrue(BuzzFace.parseInstalledList(hex("08 04 10 00 32 04 0a 02 0a 00")).isEmpty());
    }

    /** SET_WATCH_FACE carries the target id in {@code WatchFace.id} (field 2, string). */
    @Test public void setFaceCarriesTheTargetId() {
        assertArrayEquals(new byte[] {0x08, 0x04, 0x10, 0x01, 0x32, 0x04, 0x12, 0x02, 0x77, 0x32},
                BuzzFace.requestSetFace("w2"));
        // An empty id still produces a valid type/id packet with no watch_face payload.
        assertArrayEquals(new byte[] {0x08, 0x04, 0x10, 0x01}, BuzzFace.requestSetFace(""));
        assertArrayEquals(new byte[] {0x08, 0x04, 0x10, 0x01}, BuzzFace.requestSetFace(null));
    }

    @Test public void parsesTheSetSuccessFlag() {
        // WatchFace{ success: true } — field 4.
        assertEquals(Boolean.TRUE, BuzzFace.parseSetSuccess(hex("08 04 10 01 32 02 20 01")));
        assertEquals(Boolean.FALSE, BuzzFace.parseSetSuccess(hex("08 04 10 01 32 02 20 00")));
        // No success field at all: unknown, not "false".
        assertNull(BuzzFace.parseSetSuccess(hex("08 04 10 01 32 00")));
        // Not a watch-face packet.
        assertNull(BuzzFace.parseSetSuccess(hex("08 02 10 01")));
        assertNull(BuzzFace.parseSetSuccess(null));
    }

    /** PREPARE_INSTALL_WATCH_FACE carries WatchFace.prepare_info (field 6). */
    @Test public void prepareInstallCarriesIdSizeAndVersion() {
        assertArrayEquals(hex("08 04 10 04 32 0b 32 09 0a 03 31 37 30 10 64 18 01"),
                BuzzFace.requestPrepareInstall("170", 100));
        // An empty id drops the id field but keeps size and version_code.
        assertArrayEquals(hex("08 04 10 04 32 06 32 04 10 64 18 01"),
                BuzzFace.requestPrepareInstall("", 100));
    }

    @Test public void parsesThePrepareStatus() {
        // WatchFace{ prepare_status: READY(0) } — field 5, tag 0x28.
        assertEquals(BuzzFace.PREPARE_STATUS_READY,
                BuzzFace.parsePrepareStatus(hex("08 04 10 04 32 02 28 00")));
        assertEquals(BuzzFace.PREPARE_STATUS_DUPLICATED,
                BuzzFace.parsePrepareStatus(hex("08 04 10 04 32 02 28 02")));
        assertEquals("already installed", BuzzFace.prepareStatusLabel(BuzzFace.PREPARE_STATUS_DUPLICATED));
        // No status field: absent, not READY.
        assertEquals(-1, BuzzFace.parsePrepareStatus(hex("08 04 10 04 32 00")));
        assertEquals(-1, BuzzFace.parsePrepareStatus(hex("08 02 10 04")));
        assertEquals(-1, BuzzFace.parsePrepareStatus(null));
    }

    @Test public void parsesTheInstallResult() {
        // WatchFace{ install_result: 0 } — field 7, tag 0x38.
        assertEquals(0, BuzzFace.parseInstallResult(hex("08 04 10 04 32 02 38 00")));
        assertEquals(-1, BuzzFace.parseInstallResult(hex("08 04 10 04 32 00")));
    }

    private static byte[] hex(String text) {
        String clean = text.replace(" ", "");
        byte[] out = new byte[clean.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}