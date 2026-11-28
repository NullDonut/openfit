package com.openfit.app.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Framing and block math for the ZhBracelet "big-data" custom-face upload (the ColorFit Ultra
 * Buzz / NavPlus stack). This is <b>not</b> the {@link SeGatt} command transport: the command
 * packet still goes through {@code 16186f02}, but the face bytes stream to
 * {@code 16186f04} with {@code WRITE_TYPE_NO_RESPONSE}, and the watch acknowledges block by
 * block on that same characteristic.
 *
 * <p>Decoded from NoiseFit's {@code ZhBraceletService.startUploadBigData(...)}. A file is first
 * wrapped in a fixed header plus a trailing CRC32:
 *
 * <pre>
 * offset  0    1 B   0
 * offset  1    1 B   type: 16 = "watch", 1 = "lto", 32 = anything else
 * offset  2   16 B   0
 * offset 18    4 B   payload length, LE32
 * offset 22    N B   payload (the face file)
 * offset 22+N  4 B   CRC32 (IEEE/zlib) of header+payload, LE32
 * </pre>
 *
 * <p>The framed bytes are then cut into blocks of chunks. At MTU {@code m} the usable chunk is
 * {@code chunk = max(20, m - 3)}; a block holds {@code 4000 / chunk} chunks and
 * {@code (chunksPerBlock - 1) * (chunk - 2) + (chunk - 6)} payload bytes. The first chunk of a
 * block carries a six-byte header naming the block, later chunks carry only their index:
 *
 * <pre>
 * chunk 1 of a block:  01 00 &lt;totalBlocks LE16&gt; &lt;blockIndex LE16&gt; &lt;data, up to chunk-6&gt;
 * chunk k &gt; 1:         &lt;k LE16&gt; &lt;data, up to chunk-2&gt;
 * </pre>
 *
 * <p>Handshake on {@code 16186f04}: the phone announces a block with
 * {@code 00 00 00 00 &lt;chunksInBlock LE16&gt;}, the watch answers {@code 00 00 01 01 00 00}
 * (send the chunks), then {@code 00 00 01 00 00 00} once the block is complete. A frame with a
 * non-zero LE16 in bytes 4..5 asks to resend that chunk.
 *
 * <p>Android-free so it can be unit tested on the JVM.
 */
public final class BuzzUpload {
    /** Fixed header in front of the payload. */
    public static final int HEADER_BYTES = 22;
    /** Trailing CRC32 length. */
    public static final int CRC_BYTES = 4;
    /** Header + CRC, i.e. how much longer the framed file is than the payload. */
    public static final int FRAME_OVERHEAD = HEADER_BYTES + CRC_BYTES; // 26

    /** {@code startUploadBigData("watch", ...)} framing type. */
    public static final int TYPE_WATCH = 16;

    /** The acknowledged results of one {@code 16186f04} frame. */
    public static final int ACK_NONE = 0;
    public static final int ACK_GO_AHEAD = 1;   // 00 00 01 01 00 00: send this block's chunks
    public static final int ACK_BLOCK_DONE = 2; // 00 00 01 00 00 00: block complete
    public static final int ACK_RESEND = 3;     // <chunk index LE16> at bytes 4..5

    private BuzzUpload() {}

    /** Wraps a face payload as a {@code "watch"} big-data file. */
    public static byte[] frameWatch(byte[] payload) {
        return frame(payload, TYPE_WATCH);
    }

    /** Wraps a payload in the 22-byte header plus CRC32 the uploader streams. */
    public static byte[] frame(byte[] payload, int typeByte) {
        byte[] body = payload == null ? new byte[0] : payload;
        byte[] frame = new byte[HEADER_BYTES + body.length + CRC_BYTES];
        frame[0] = 0;
        frame[1] = (byte) typeByte;
        // Bytes 2..17 stay zero.
        writeLe32(frame, 18, body.length);
        System.arraycopy(body, 0, frame, HEADER_BYTES, body.length);
        CRC32 crc = new CRC32();
        crc.update(frame, 0, HEADER_BYTES + body.length);
        writeLe32(frame, HEADER_BYTES + body.length, (int) crc.getValue());
        return frame;
    }

    /** Usable chunk length at the given GATT MTU: {@code max(20, mtu - 3)}. */
    public static int chunkSize(int mtu) {
        return Math.max(20, mtu - 3);
    }

    /** Chunks per block: {@code 4000 / chunk}, floored, and never below one. */
    public static int chunksPerBlock(int chunk) {
        return Math.max(1, 4000 / Math.max(20, chunk));
    }

    /** Payload bytes carried by a full block. */
    public static int blockBytes(int chunk) {
        int c = Math.max(20, chunk);
        int per = chunksPerBlock(c);
        return (per - 1) * (c - 2) + (c - 6);
    }

    /** Number of blocks the framed file occupies. */
    public static int totalBlocks(int framedLength, int chunk) {
        int per = blockBytes(chunk);
        if (framedLength <= 0) return 1;
        return Math.max(1, (framedLength + per - 1) / per);
    }

    /** Payload bytes carried by one 1-based block (the last block may be short). */
    public static int blockDataLength(int framedLength, int chunk, int blockIndex) {
        int per = blockBytes(chunk);
        int start = (blockIndex - 1) * per;
        return Math.max(0, Math.min(per, framedLength - start));
    }

    /** Chunks one block of {@code dataLength} payload bytes is split into. */
    public static int chunksInBlock(int dataLength, int chunk) {
        int c = Math.max(20, chunk);
        int first = c - 6;
        if (dataLength <= first) return 1;
        return 1 + (dataLength - first + (c - 2) - 1) / (c - 2);
    }

    /** The block announce frame, byte-identical to {@link SeGatt#announce(int)}. */
    public static byte[] announceBlock(int chunksInBlock) {
        return SeGatt.announce(chunksInBlock);
    }

    /**
     * Builds every chunk frame for one 1-based block, in order. Chunk 1 carries the block header,
     * the rest carry their own 1-based index; together they carry exactly
     * {@link #blockDataLength} bytes of {@code framed}.
     */
    public static List<byte[]> blockChunks(byte[] framed, int chunk, int blockIndex) {
        List<byte[]> out = new ArrayList<>();
        if (framed == null || framed.length == 0) return out;
        int c = Math.max(20, chunk);
        int blocks = totalBlocks(framed.length, c);
        int per = blockBytes(c);
        int at = (blockIndex - 1) * per;
        int remaining = blockDataLength(framed.length, c, blockIndex);
        int count = chunksInBlock(remaining, c);
        for (int k = 1; k <= count && remaining > 0; k++) {
            int cap = k == 1 ? c - 6 : c - 2;
            int length = Math.min(cap, remaining);
            out.add(chunkFrame(blocks, blockIndex, k, framed, at, length));
            at += length;
            remaining -= length;
        }
        return out;
    }

    /**
     * One chunk frame. {@code blockIndex} and {@code chunkIndex} are 1-based; the first chunk of a
     * block is prefixed {@code 01 00 <totalBlocks LE16> <blockIndex LE16>}, later chunks only their
     * own index.
     */
    public static byte[] chunkFrame(int totalBlocks, int blockIndex, int chunkIndex,
                                    byte[] framed, int offset, int length) {
        int header = chunkIndex == 1 ? 6 : 2;
        byte[] out = new byte[header + length];
        if (chunkIndex == 1) {
            out[0] = 1;
            out[1] = 0;
            out[2] = (byte) totalBlocks;
            out[3] = (byte) (totalBlocks >> 8);
            out[4] = (byte) blockIndex;
            out[5] = (byte) (blockIndex >> 8);
        } else {
            out[0] = (byte) chunkIndex;
            out[1] = (byte) (chunkIndex >> 8);
        }
        System.arraycopy(framed, offset, out, header, length);
        return out;
    }

    /** Classifies an inbound {@code 16186f04} frame. */
    public static int classifyAck(byte[] value) {
        if (value == null || value.length < 6) return ACK_NONE;
        if (resendIndex(value) != 0) return ACK_RESEND;
        if (!SeGatt.isControl(value)) return ACK_NONE;
        int code = SeGatt.controlCode(value);
        if (code == SeGatt.CODE_READY) return ACK_GO_AHEAD;
        if (code == SeGatt.CODE_RECEIVED) return ACK_BLOCK_DONE;
        return ACK_NONE;
    }

    /** The 1-based chunk the watch asked to resend (0 when the frame is not a resend). */
    public static int resendIndex(byte[] value) {
        if (value == null || value.length < 6) return 0;
        return (value[4] & 0xff) | ((value[5] & 0xff) << 8);
    }

    private static void writeLe32(byte[] out, int offset, int value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >> 8);
        out[offset + 2] = (byte) (value >> 16);
        out[offset + 3] = (byte) (value >> 24);
    }
}
