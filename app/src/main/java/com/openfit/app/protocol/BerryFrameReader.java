package com.openfit.app.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reassembles Berry frames from GATT notifications.
 *
 * <p>A notification is not guaranteed to contain one complete frame: the watch (and the phone,
 * when a frame is larger than the negotiated MTU) may split a frame across writes. The reader
 * keeps a small buffer, skips noise before the next magic, consumes the 8-byte ack frames the
 * watch sends for every request, and returns only complete command frames.
 *
 * <p>Android-free so the stream handling can be unit tested.
 */
public final class BerryFrameReader {
    /** Largest command frame accepted; the length field itself allows 65535 bytes. */
    private static final int MAX_FRAME = 8192;
    /** Keep enough tail bytes to complete a magic split across notifications. */
    private static final int MAGIC_TAIL = 3;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public List<BerryFrame> append(byte[] chunk) {
        if (chunk != null && chunk.length > 0) buffer.write(chunk, 0, chunk.length);
        byte[] data = buffer.toByteArray();
        List<BerryFrame> frames = new ArrayList<>();
        int pos = 0;
        while (true) {
            int start = findHeader(data, pos);
            if (start < 0) {
                // No magic: keep only a possible partial magic at the tail.
                pos = Math.max(pos, data.length - MAGIC_TAIL);
                break;
            }
            if (data.length - start < 4) {
                pos = start;
                break;
            }
            int head = headerAt(data, start);
            if (head == BerryFrame.HEAD_ASK) {
                if (data.length - start < 8) {
                    pos = start;
                    break;
                }
                pos = start + 8; // ack: head + serial + error + two placeholders
                continue;
            }
            if (data.length - start < BerryFrame.HEADER_SIZE) {
                pos = start;
                break;
            }
            int declared = (data[start + 7] & 0xff) | ((data[start + 8] & 0xff) << 8);
            if (declared > MAX_FRAME) {
                // Corrupt length: drop one byte and look for the next magic.
                pos = start + 1;
                continue;
            }
            int total = BerryFrame.HEADER_SIZE + declared;
            if (data.length - start < total) {
                pos = start;
                break;
            }
            frames.add(BerryFrame.decode(Arrays.copyOfRange(data, start, start + total)));
            pos = start + total;
        }
        buffer.reset();
        if (pos < data.length) buffer.write(data, pos, data.length - pos);
        // If a corrupt stream keeps a huge unterminated frame, do not grow without bound.
        if (buffer.size() > MAX_FRAME + BerryFrame.HEADER_SIZE) buffer.reset();
        return frames;
    }

    /** Drops any partial frame, for example after a reconnect. */
    public void reset() {
        buffer.reset();
    }

    private static int findHeader(byte[] data, int from) {
        for (int i = Math.max(0, from); i + 4 <= data.length; i++) {
            int head = headerAt(data, i);
            if (head == BerryFrame.HEAD_APP_REQ_DEV
                    || head == BerryFrame.HEAD_DEV_REQ_APP
                    || head == BerryFrame.HEAD_ASK
                    || head == BerryFrame.HEAD_DEV_LARGE_FILES) {
                return i;
            }
        }
        return -1;
    }

    private static int headerAt(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }
}
