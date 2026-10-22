package com.securechat.server;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads newline-terminated lines with a hard byte limit.
 * - Throws LineTooLongException as soon as the limit is exceeded (no unbounded buffer).
 * - Strips trailing '\r' before returning (handles CRLF).
 * - Returns null on EOF without data.
 * - Decodes as UTF-8. Byte limit applies to raw bytes, not chars.
 */
public final class BoundedLineReader implements Closeable {

    private final InputStream in;
    private final int maxBytes;
    private final byte[] buf = new byte[8192];
    private int bufPos = 0;
    private int bufLen = 0;

    public BoundedLineReader(InputStream in, int maxBytes) {
        this.in = in;
        this.maxBytes = maxBytes;
    }

    /** @return the line without terminator, or null at EOF with no data. */
    public String readLine() throws IOException {
        ByteArrayOutputStream accum = new ByteArrayOutputStream(256);
        while (true) {
            int b = readByte();
            if (b == -1) {
                return accum.size() == 0 ? null : decode(accum.toByteArray());
            }
            if (b == '\n') {
                return decode(accum.toByteArray());
            }
            if (accum.size() >= maxBytes) {
                throw new LineTooLongException(maxBytes);
            }
            accum.write(b);
        }
    }

    private int readByte() throws IOException {
        if (bufPos >= bufLen) {
            bufLen = in.read(buf, 0, buf.length);
            bufPos = 0;
            if (bufLen == -1) return -1;
        }
        return buf[bufPos++] & 0xFF;
    }

    private static String decode(byte[] bytes) {
        int len = bytes.length;
        if (len > 0 && bytes[len - 1] == '\r') len--;
        return new String(bytes, 0, len, StandardCharsets.UTF_8);
    }

    @Override public void close() throws IOException { in.close(); }

    public static final class LineTooLongException extends IOException {
        public LineTooLongException(int max) {
            super("Line exceeds " + max + " bytes");
        }
    }
}