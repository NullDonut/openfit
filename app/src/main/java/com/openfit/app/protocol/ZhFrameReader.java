package com.openfit.app.protocol;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reassembles ZhBracelet frames from notifications on {@code 00000003}. The watch sends one
 * frame per notification in practice, but the vendor SDK still buffers by the declared length,
 * so this does the same: it keeps a partial frame until the rest arrives and resynchronises on
 * the {@code 0xAB} magic if a byte was lost.
 */
public final class ZhFrameReader {
    private byte[] buffer = new byte[0];

    public void reset() {
        buffer = new byte[0];
    }

    public List<byte[]> append(byte[] data) {
        List<byte[]> frames = new ArrayList<>();
        if (data == null || data.length == 0) return frames;
        byte[] combined = new byte[buffer.length + data.length];
        System.arraycopy(buffer, 0, combined, 0, buffer.length);
        System.arraycopy(data, 0, combined, buffer.length, data.length);
        int offset = 0;
        while (offset < combined.length) {
            // Find the next magic byte if the stream is out of sync.
            if ((combined[offset] & 0xFF) != ZhBraceletBinding.MAGIC) {
                offset++;
                continue;
            }
            int available = combined.length - offset;
            if (available < 4) break;
            int total = ZhBraceletBinding.declaredLength(
                    Arrays.copyOfRange(combined, offset, combined.length));
            if (total < 0 || total > 4096) {
                offset++;
                continue;
            }
            if (available < total) break;
            frames.add(Arrays.copyOfRange(combined, offset, offset + total));
            offset += total;
        }
        buffer = offset >= combined.length
                ? new byte[0]
                : Arrays.copyOfRange(combined, offset, combined.length);
        return frames;
    }
}
