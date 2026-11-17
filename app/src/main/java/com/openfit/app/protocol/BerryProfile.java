package com.openfit.app.protocol;

import java.util.UUID;

/**
 * GATT identifiers observed in NoiseFit 5.5.0 for the Berry family of watches.
 *
 * <p>The Berry service intentionally uses {@code 00807f9b34fb} as its base UUID (not the SIG
 * {@code 00805f9b34fb}), so the characters below cannot be shortened to 16-bit form.
 */
public final class BerryProfile {
    public static final UUID SERVICE = UUID.fromString("16186f00-0000-1000-8000-00807f9b34fb");
    public static final UUID CHAR_BULK = UUID.fromString("16186f01-0000-1000-8000-00807f9b34fb");
    public static final UUID CHAR_WRITE = UUID.fromString("16186f02-0000-1000-8000-00807f9b34fb");
    public static final UUID CHAR_LARGE = UUID.fromString("16186f03-0000-1000-8000-00807f9b34fb");
    public static final UUID CHAR_LARGE_DATA = UUID.fromString("16186f04-0000-1000-8000-00807f9b34fb");
    public static final UUID CHAR_SIDE = UUID.fromString("16186f05-0000-1000-8000-00807f9b34fb");

    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /** Base service of the alternate (Ryeex) family; useful for diagnostics only. */
    public static final UUID RYEEX_SERVICE = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb");
    public static final UUID RYEEX_WRITE = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb");
    public static final UUID RYEEX_READ = UUID.fromString("00000003-0000-1000-8000-00805f9b34fb");

    private BerryProfile() {}

    public static boolean isBerry(UUID serviceUuid) {
        return SERVICE.equals(serviceUuid);
    }

    public static boolean isRyeex(UUID serviceUuid) {
        return RYEEX_SERVICE.equals(serviceUuid);
    }
}
