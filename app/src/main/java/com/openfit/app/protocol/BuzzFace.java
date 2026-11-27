package com.openfit.app.protocol;

import java.io.ByteArrayOutputStream;
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
 * shapes are accepted. Only read-only discovery is wired here.
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

    private static final int FACE_FIELD_WATCH_FACE_LIST = 1;
    private static final int FACE_FIELD_INFO_LIST = 8;

    private static final int ITEM_FIELD_ID = 1;
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
                boolean current = SeWear.readVarintField(item, ITEM_FIELD_IS_CURRENT) == 1;
                boolean removable = SeWear.readVarintField(item, ITEM_FIELD_CAN_REMOVE) == 1;
                faces.add(new SeCommands.WatchFace(id, current, removable));
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
