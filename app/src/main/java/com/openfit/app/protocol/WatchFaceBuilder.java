package com.openfit.app.protocol;

/**
 * Builds a ZhBracelet "DIY" dial for the ColorFit Ultra Buzz from a bundled raw template plus a
 * photo. The watch accepts a whole {@code AF AF AF 02} dial and shows whatever images it contains,
 * so a DIY face is made by overwriting the template's background image with the user's photo and
 * re-wrapping the result. The dial's own clock/date layers are left untouched.
 *
 * <p><b>Where the background lives.</b> The template is the raw (uncompressed) dial that
 * {@code LzoUtils.mergeDialFile} wraps into a container. Its images are a packed atlas; the
 * full-screen backdrop is a {@value #BACKGROUND_WIDTH}&times;{@value #BACKGROUND_HEIGHT} image of
 * little-endian RGB565 starting at {@code 0x27000}. The two big-endian {@code u16} fields at
 * {@code 0x82}/{@code 0x84} in the raw header are {@code 320}/{@code 385}, matching this region
 * exactly (320 * 385 * 2 = {@value #BACKGROUND_BYTES} bytes).
 *
 * <p><b>Container format</b> (everything little-endian), byte-for-byte the layout
 * {@code LzoUtils.mergeDialFile} emits:
 *
 * <pre>
 *  0   4 B   magic AF AF AF 02
 *  4   4 B   raw length (LE32)
 *  8   4 B   CRC of the raw dial (the watch does not check it; 0 is accepted)
 * 12   4 B   total stored block length (LE32)
 * 16   4 B   CRC of the stored blocks (not checked; 0 is accepted)
 * 20   4 B   0
 * 24   2 B   block count (LE16)
 * 26   2*n B one LE16 per block: bit15 set = LZO, clear = stored uncompressed
 * 26+2n  N B stored block data, concatenated
 * </pre>
 *
 * <p>The template is split into {@value #BLOCK_BYTES}-byte blocks. This builder stores every block
 * <em>uncompressed</em> (bit15 clear), which is a legal choice the vendor also makes whenever a
 * block would not shrink — it only costs upload size, never correctness, and keeps the app free of
 * an LZO compressor. The watch decompresses a block only when bit15 is set.
 *
 * <p>Android-free, so the block maths and the RGB565 conversion can be unit tested on the JVM.
 */
public final class WatchFaceBuilder {
    /** Byte offset of the full-screen background image inside the raw dial. */
    public static final int BACKGROUND_OFFSET = 0x27000;
    /** Background image width in pixels. */
    public static final int BACKGROUND_WIDTH = 320;
    /** Background image height in pixels. */
    public static final int BACKGROUND_HEIGHT = 385;
    /** Background image size in bytes (RGB565, two bytes per pixel). */
    public static final int BACKGROUND_BYTES = BACKGROUND_WIDTH * BACKGROUND_HEIGHT * 2;
    /** Raw dial is cut into blocks of this size before wrapping. */
    public static final int BLOCK_BYTES = 4096;

    private WatchFaceBuilder() {}

    /**
     * Overwrites the template's background with {@code argb} and wraps it as an installable dial
     * container. {@code argb} must hold exactly width*height pixels of a
     * {@value #BACKGROUND_WIDTH}&times;{@value #BACKGROUND_HEIGHT} image (row-major, as returned by
     * {@code Bitmap.getPixels}). The template is not modified.
     */
    public static byte[] build(byte[] template, int[] argb, int width, int height) {
        if (template == null || template.length < BACKGROUND_OFFSET + BACKGROUND_BYTES) {
            throw new IllegalArgumentException("the dial template is missing or truncated");
        }
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) {
            throw new IllegalArgumentException("the photo has no pixels");
        }
        if (width != BACKGROUND_WIDTH || height != BACKGROUND_HEIGHT) {
            throw new IllegalArgumentException(
                    "the photo must be scaled to " + BACKGROUND_WIDTH + "x" + BACKGROUND_HEIGHT);
        }
        byte[] raw = template.clone();
        writeBackground(raw, argb, width, height);
        return wrap(raw);
    }

    /** Writes {@code argb} into the raw dial's background region as little-endian RGB565. */
    public static void writeBackground(byte[] raw, int[] argb, int width, int height) {
        int at = BACKGROUND_OFFSET;
        int pixels = width * height;
        for (int i = 0; i < pixels; i++) {
            int c = argb[i];
            int v = (((c >> 16) & 0xff) >> 3) << 11
                    | (((c >> 8) & 0xff) >> 2) << 5
                    | ((c & 0xff) >> 3);
            raw[at++] = (byte) v;
            raw[at++] = (byte) (v >> 8);
        }
    }

    /**
     * Wraps a raw dial into the {@code AF AF AF 02} container with every block stored uncompressed.
     * The result is what {@link BuzzUpload#isDialContainer} expects and what the Buzz uploader
     * streams as-is.
     */
    public static byte[] wrap(byte[] raw) {
        if (raw == null || raw.length == 0) {
            throw new IllegalArgumentException("the dial is empty");
        }
        int blocks = (raw.length + BLOCK_BYTES - 1) / BLOCK_BYTES;
        int header = BuzzUpload.DIAL_HEADER_BYTES + 2 * blocks;
        byte[] out = new byte[header + raw.length];
        out[0] = (byte) 0xaf;
        out[1] = (byte) 0xaf;
        out[2] = (byte) 0xaf;
        out[3] = 0x02;
        putLe32(out, 4, raw.length);   // raw length
        putLe32(out, 8, 0);            // raw CRC (not checked)
        putLe32(out, 12, raw.length);  // stored length: every block is uncompressed
        putLe32(out, 16, 0);           // stored CRC (not checked)
        putLe32(out, 20, 0);
        out[24] = (byte) blocks;
        out[25] = (byte) (blocks >> 8);
        for (int i = 0; i < blocks; i++) {
            int length = Math.min(BLOCK_BYTES, raw.length - i * BLOCK_BYTES);
            int p = BuzzUpload.DIAL_HEADER_BYTES + 2 * i;
            out[p] = (byte) length;              // bit15 clear: stored uncompressed
            out[p + 1] = (byte) (length >> 8);
        }
        System.arraycopy(raw, 0, out, header, raw.length);
        return out;
    }

    /** Container size for a raw dial of {@code rawLength} bytes, storing every block uncompressed. */
    public static int wrappedLength(int rawLength) {
        int blocks = (rawLength + BLOCK_BYTES - 1) / BLOCK_BYTES;
        return BuzzUpload.DIAL_HEADER_BYTES + 2 * blocks + rawLength;
    }

    private static void putLe32(byte[] out, int at, int value) {
        out[at] = (byte) value;
        out[at + 1] = (byte) (value >> 8);
        out[at + 2] = (byte) (value >> 16);
        out[at + 3] = (byte) (value >> 24);
    }
}
