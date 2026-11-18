package com.aegis.code_sandbox.util;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * In-memory output sink with a hard byte cap.
 *
 * <p>A malicious submission can print forever. The Docker stream must still be
 * drained or the executor blocks on its stdout pipe, so bytes beyond the cap
 * are counted and discarded instead of being buffered.</p>
 */
public final class BoundedOutputStream extends OutputStream {

    private final ByteArrayOutputStream buffer;
    private final int maxBytes;
    private long totalBytes;
    private boolean truncated;

    public BoundedOutputStream(int maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
        this.buffer = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
    }

    @Override
    public synchronized void write(int b) {
        totalBytes++;
        if (buffer.size() < maxBytes) {
            buffer.write(b);
        } else {
            truncated = true;
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
        if (length <= 0) {
            return;
        }
        totalBytes += length;
        int remaining = maxBytes - buffer.size();
        if (remaining > 0) {
            buffer.write(bytes, offset, Math.min(remaining, length));
        }
        if (length > remaining) {
            truncated = true;
        }
    }

    public synchronized String text() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    public synchronized boolean isTruncated() {
        return truncated;
    }

    public synchronized long getTotalBytes() {
        return totalBytes;
    }

    public synchronized int getRetainedBytes() {
        return buffer.size();
    }
}
