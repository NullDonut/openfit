package com.openfit.app.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The ZhBracelet ("NavPlus") watch-face protobuf, the {@code com.zh.wear.protobuf} family the
 * ColorFit Ultra Buzz answers. Face traffic is a protobuf {@code WearPacket} on the same
 * {@code 16186f00} chunked transport the SE command set uses ({@link SeGatt}); it is <b>not</b> a
 * 13-byte {@code 0xAB} script frame and <b>not</b> the SE {@code SEWear} envelope
 * ({@link SeWear}). The two protobuf packages are different ({@code com.zh.wear.protobuf} here,
 * {@code com.zh.ble.wear.protobuf} for SE) and must not be mixed.
 *
 * <p>Field numbers decoded from NoiseFit's bundled {@code ZhBraceletService} (SDK classes9.dex):
 *
 * <pre>
 * WearPacket {                       // wear.proto
 *   1  Type type = WATCH_FACE(4)     // required
 *   2  uint32 id                     // required; the WatchFace.WatchFaceID selector
 *   6  WatchFace watch_face
 * }
 * WatchFace {                        // wear_watch_face.proto
 *   1  WatchFaceItem.List watch_face_list
 *   2  string id
 *   8  WatchFaceInfo.List info_list
 *   ...
 * }
 * WatchFaceItem { 1 id, 2 name, 3 is_current, 4 can_remove, 5 version_code, 6 can_edit, ... }
 * WatchFaceItem.List { repeated WatchFaceItem list = 1 }
 * WatchFaceInfo     { 1 id }
 * WatchFaceInfo.List { repeated WatchFaceInfo list = 1 }
 * WatchFaceID { GET_INSTALLED_LIST = 0, SET_WATCH_FACE = 1, ... PREPARE_INSTALL_WATCH_FACE = 4 }
 * </pre>
 *
 * <p>The SDK's own reply handler reads the installed list from {@code watch_face_list} (field 1);
 * a firmware that only reports ids may answer with {@code info_list} (field 8) instead, so both
 * shapes are accepted. Discovery, a set-face probe and the custom-install prepare packet are
 * coded here; the file bytes themselves stream through {@link BuzzUpload}.
 */
public final class BuzzFace {
    private BuzzFace() {}

    /** {@code WearPacket.Type.WATCH_FACE}. */
    public static final int TYPE_WATCH_FACE = 4;

    private static final int WEAR_FIELD_TYPE = 1;
    private static final int WEAR_FIELD_ID = 2;
    private static final int WEAR_FIELD_WATCH_FACE = 6;

    /** {@code WatchFace.WatchFaceID.GET_INSTALLED_LIST}. */
    public static final int WATCH_FACE_ID_GET_INSTALLED_LIST = 0;

    /** {@code WatchFace.WatchFaceID.SET_WATCH_FACE}. */
    public static final int WATCH_FACE_ID_SET_WATCH_FACE = 1;

    /** {@code WatchFace.WatchFaceID.PREPARE_INSTALL_WATCH_FACE}. */
    public static final int WATCH_FACE_ID_PREPARE_INSTALL = 4;

    /** {@code WatchFace.PrepareStatus}; only {@link #PREPARE_STATUS_READY} proceeds. */
    public static final int PREPARE_STATUS_READY = 0;
    public static final int PREPARE_STATUS_BUSY = 1;
    public static final int PREPARE_STATUS_DUPLICATED = 2;
    public static final int PREPARE_STATUS_LOW_STORAGE = 3;
    public static final int PREPARE_STATUS_LOW_BATTERY = 4;
    public static final int PREPARE_STATUS_DOWNGRADE = 5;

    private static final int FACE_FIELD_WATCH_FACE_LIST = 1;
    private static final int FACE_FIELD_ID = 2;
    private static final int FACE_FIELD_SUCCESS = 4;
    private static final int FACE_FIELD_PREPARE_STATUS = 5;
    private static final int FACE_FIELD_PREPARE_INFO = 6;
    private static final int FACE_FIELD_INSTALL_RESULT = 7;
    private static final int FACE_FIELD_INFO_LIST = 8;

    private static final int PREPARE_INFO_FIELD_ID = 1;
    private static final int PREPARE_INFO_FIELD_SIZE = 2;
    private static final int PREPARE_INFO_FIELD_VERSION_CODE = 3;

    private static final int ITEM_FIELD_ID = 1;
    private static final int ITEM_FIELD_NAME = 2;
    private static final int ITEM_FIELD_IS_CURRENT = 3;
    private static final int ITEM_FIELD_CAN_REMOVE = 4;

    private static final int INFO_FIELD_ID = 1;

    /** Both nested lists repeat their entry at field 1. */
    private static final int LIST_FIELD = 1;

    /**
     * {@code WearPacket{type: WATCH_FACE, id: GET_INSTALLED_LIST}}. Both fields are proto2
     * {@code required}, so the id is written even though it is zero: the frame is {@code 08 04 10 00}.
     */
    public static byte[] requestInstalledList() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SeWear.writeVarintField(out, WEAR_FIELD_TYPE, TYPE_WATCH_FACE);
        SeWear.writeTag(out, WEAR_FIELD_ID, 0);
        SeWear.writeVarint(out, WATCH_FACE_ID_GET_INSTALLED_LIST);
        return out.toByteArray();
    }

    /**
     * {@code WearPacket{type: WATCH_FACE, id: SET_WATCH_FACE, watch_face{ id: <face> }}} — asks the
     * watch to make an installed face current. {@code WatchFace.id} is field 2 (string), so this
     * is the phone-to-watch payloadCase 2. The watch answers with {@code watch_face_list} carrying
     * the new {@code is_current}, or with {@code success} (field 4).
     *
     * <p>The vendor app never sends {@code SET_WATCH_FACE}, but the Buzz firmware accepts it
     * (hardware-confirmed 2026-11-28): it answers {@code watch_face{success}} and the new current
     * face is visible on the next list read. It only changes which face is shown, which the user
     * can undo on the watch.
     */
    public static byte[] requestSetFace(String id) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SeWear.writeVarintField(out, WEAR_FIELD_TYPE, TYPE_WATCH_FACE);
        SeWear.writeTag(out, WEAR_FIELD_ID, 0);
        SeWear.writeVarint(out, WATCH_FACE_ID_SET_WATCH_FACE);
        if (id != null && !id.isEmpty()) {
            ByteArrayOutputStream face = new ByteArrayOutputStream();
            SeWear.writeMessage(face, FACE_FIELD_ID, id.getBytes(StandardCharsets.UTF_8));
            SeWear.writeMessage(out, WEAR_FIELD_WATCH_FACE, face.toByteArray());
        }
        return out.toByteArray();
    }

    /**
     * Reads the {@code success} flag (WatchFace field 4) from a set-face reply. Returns
     * {@code null} when the packet is not a {@code WatchFace} or carries no success field, so the
     * caller can distinguish "the watch said yes/no" from "this was a different reply".
     */
    public static Boolean parseSetSuccess(byte[] wearPacket) {
        if (!isWatchFace(wearPacket)) return null;
        byte[] face = SeWear.readMessageField(wearPacket, WEAR_FIELD_WATCH_FACE);
        if (face == null) return null;
        long success = SeWear.readVarintField(face, FACE_FIELD_SUCCESS);
        if (success < 0) return null;
        return success == 1;
    }

    /**
     * {@code WearPacket{type: WATCH_FACE, id: PREPARE_INSTALL_WATCH_FACE, watch_face{ prepare_info{
     * id, size, version_code: 1 }}}} — the first step of a custom-face install. The watch answers
     * with {@code watch_face.prepare_status} (field 5); only {@link #PREPARE_STATUS_READY} proceeds
     * to the {@link BuzzUpload} file transfer. The vendor uses id {@code "170"} and
     * {@code version_code = 1}; {@code size} is the length of the <b>unframed</b> file that will be
     * uploaded. For id {@code "170"} and size 100 the frame is
     * {@code 08 04 10 04 32 0b 32 09 0a 03 31 37 30 10 64 18 01}.
     */
    public static byte[] requestPrepareInstall(String id, long size) {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        if (id != null && !id.isEmpty()) {
            SeWear.writeMessage(info, PREPARE_INFO_FIELD_ID, id.getBytes(StandardCharsets.UTF_8));
        }
        if (size != 0) {
            // uint32, but face files are far below 2^31, so the int overload is exact here.
            SeWear.writeVarintField(info, PREPARE_INFO_FIELD_SIZE, (int) size);
        }
        SeWear.writeVarintField(info, PREPARE_INFO_FIELD_VERSION_CODE, 1);

        ByteArrayOutputStream face = new ByteArrayOutputStream();
        SeWear.writeMessage(face, FACE_FIELD_PREPARE_INFO, info.toByteArray());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SeWear.writeVarintField(out, WEAR_FIELD_TYPE, TYPE_WATCH_FACE);
        SeWear.writeVarintField(out, WEAR_FIELD_ID, WATCH_FACE_ID_PREPARE_INSTALL);
        SeWear.writeMessage(out, WEAR_FIELD_WATCH_FACE, face.toByteArray());
        return out.toByteArray();
    }

    /**
     * Reads {@code watch_face.prepare_status} (field 5) from a prepare reply, or -1 when the packet
     * is not a {@code WatchFace} or carries no status field. {@link #PREPARE_STATUS_READY} is 0, so
     * a present-but-READY status is distinguishable from an absent one.
     */
    public static int parsePrepareStatus(byte[] wearPacket) {
        if (!isWatchFace(wearPacket)) return -1;
        byte[] face = SeWear.readMessageField(wearPacket, WEAR_FIELD_WATCH_FACE);
        if (face == null) return -1;
        long status = SeWear.readVarintField(face, FACE_FIELD_PREPARE_STATUS);
        return status < 0 ? -1 : (int) status;
    }

    /**
     * Reads {@code watch_face.install_result} (field 7) if the watch reports one after a transfer,
     * or -1 when absent. The vendor never sends {@code REPORT_INSTALL_RESULT}, so this is a
     * defensive read only.
     */
    public static int parseInstallResult(byte[] wearPacket) {
        if (!isWatchFace(wearPacket)) return -1;
        byte[] face = SeWear.readMessageField(wearPacket, WEAR_FIELD_WATCH_FACE);
        if (face == null) return -1;
        long result = SeWear.readVarintField(face, FACE_FIELD_INSTALL_RESULT);
        return result < 0 ? -1 : (int) result;
    }

    /** Human-readable {@code WatchFace.PrepareStatus} label. */
    public static String prepareStatusLabel(int status) {
        switch (status) {
            case PREPARE_STATUS_READY: return "ready";
            case PREPARE_STATUS_BUSY: return "busy";
            case PREPARE_STATUS_DUPLICATED: return "already installed";
            case PREPARE_STATUS_LOW_STORAGE: return "low storage";
            case PREPARE_STATUS_LOW_BATTERY: return "low battery";
            case PREPARE_STATUS_DOWNGRADE: return "downgrade";
            default: return "status " + status;
        }
    }

    /** True when the reassembled packet is a {@code WATCH_FACE} WearPacket. */
    public static boolean isWatchFace(byte[] wearPacket) {
        return wearPacket != null
                && SeWear.readVarintField(wearPacket, WEAR_FIELD_TYPE) == TYPE_WATCH_FACE;
    }

    /**
     * Reads the installed-face list from a GET_INSTALLED_LIST reply. Returns an empty list for any
     * other packet shape, so a partial or unexpected reply never throws.
     */
    public static List<SeCommands.WatchFace> parseInstalledList(byte[] wearPacket) {
        List<SeCommands.WatchFace> faces = new ArrayList<>();
        if (!isWatchFace(wearPacket)) return faces;
        byte[] face = SeWear.readMessageField(wearPacket, WEAR_FIELD_WATCH_FACE);
        if (face == null) return faces;

        byte[] items = SeWear.readMessageField(face, FACE_FIELD_WATCH_FACE_LIST);
        if (items != null) {
            for (byte[] item : SeWear.readMessages(items, LIST_FIELD)) {
                String id = SeWear.readStringField(item, ITEM_FIELD_ID);
                if (id == null || id.isEmpty()) continue;
                String name = SeWear.readStringField(item, ITEM_FIELD_NAME);
                boolean current = SeWear.readVarintField(item, ITEM_FIELD_IS_CURRENT) == 1;
                boolean removable = SeWear.readVarintField(item, ITEM_FIELD_CAN_REMOVE) == 1;
                faces.add(new SeCommands.WatchFace(id, name == null ? "" : name, current, removable));
            }
            return faces;
        }

        byte[] infos = SeWear.readMessageField(face, FACE_FIELD_INFO_LIST);
        if (infos != null) {
            for (byte[] info : SeWear.readMessages(infos, LIST_FIELD)) {
                String id = SeWear.readStringField(info, INFO_FIELD_ID);
                if (id == null || id.isEmpty()) continue;
                // This shape carries ids only, with no current/removable flags.
                faces.add(new SeCommands.WatchFace(id, false, false));
            }
        }
        return faces;
    }
}
