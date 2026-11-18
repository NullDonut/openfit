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

    /**
     * Berry over BLE. The vendor's {@code Berry-Test} stack writes Berry frames to
     * {@link #AA01_WRITE} and reads the replies from a notify characteristic of the same
     * service ({@link #AA01_READ}, plus aa05 on models that have it). The frames are identical
     * to the classic SPP ones; only the bearer changes.
     */
    public static final UUID AA01_SERVICE = UUID.fromString("0000aa01-0000-1000-8000-00805f9b34fb");
    public static final UUID AA01_WRITE = UUID.fromString("0000aa02-0000-1000-8000-00805f9b34fb");
    public static final UUID AA01_READ = UUID.fromString("0000aa03-0000-1000-8000-00805f9b34fb");
    public static final UUID AA01_05_READ = UUID.fromString("0000aa05-0000-1000-8000-00805f9b34fb");

    /**
     * ZhBracelet ("NavPlus") script channel. NoiseFit binds ColorFit Ultra Buzz, Vision Buzz and
     * Ultra 2 through the bundled {@code com.zjw.zhbraceletsdk}, whose base service is this one:
     * requests go to {@link #RYEEX_WRITE} and replies arrive on {@link #RYEEX_READ}.
     */
    public static final UUID RYEEX_SERVICE = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb");
    public static final UUID RYEEX_WRITE = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb");
    public static final UUID RYEEX_READ = UUID.fromString("00000003-0000-1000-8000-00805f9b34fb");

    /**
     * An extra vendor channel seen on the Noise Watch Beat (2026-11-18) next to the SE service:
     * {@code 77d1} takes writes and {@code 77d2} notifies. Its frames are not known yet; the Beat
     * binds over SE, so OpenFit only reports its presence.
     */
    public static final UUID SERVICE_77D0 = UUID.fromString("000077d0-0000-1000-8000-00805f9b34fb");

    private BerryProfile() {}

    public static boolean isBerry(UUID serviceUuid) {
        return SERVICE.equals(serviceUuid);
    }

    public static boolean isRyeex(UUID serviceUuid) {
        return RYEEX_SERVICE.equals(serviceUuid);
    }
}
