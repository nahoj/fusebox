package eu.nahoj.fusebox.vfs2.util;

import org.apache.commons.vfs2.RandomAccessContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;

/**
 * Read-only SeekableByteChannel backed by Apache VFS2 RandomAccessContent.
 * Not thread-safe by itself; wrap with ThreadSafeSeekableByteChannel for safety.
 */
public final class RandomAccessContentSeekableByteChannel implements SeekableByteChannel {

    private static final Logger LOG = LoggerFactory.getLogger(RandomAccessContentSeekableByteChannel.class);

    private final RandomAccessContent rac;
    // Keep a single stream open for the channel's lifetime to avoid closing the underlying RAC
    private final InputStream in;
    private boolean open = true;
    private long position = 0L;

    public RandomAccessContentSeekableByteChannel(RandomAccessContent rac) {
        this.rac = rac;
        try {
            this.in = rac.getInputStream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void ensureOpen() throws ClosedChannelException {
        if (!open) throw new ClosedChannelException();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (!dst.hasRemaining()) return 0;
        rac.seek(position); // Affects the InputStream
        int total = 0;
        final int chunk = Math.min(dst.remaining(), 64 * 1024);
        byte[] buf = new byte[chunk];
        while (dst.hasRemaining()) {
            int toRead = Math.min(dst.remaining(), buf.length);
            int r = in.read(buf, 0, toRead);
            if (r == -1) {
                // EOF: if nothing was read, signal EOF; otherwise return bytes read
                LOG.trace("read() EOF after {} bytes at pos {}", total, position);
                return (total == 0) ? -1 : total;
            }
            dst.put(buf, 0, r);
            total += r;
            position += r;
            if (r < toRead) break;
        }
        LOG.trace("read() -> {} bytes, newPos={}", total, position);
        return total;
    }

    @Override
    public int write(ByteBuffer src) throws IOException { throw new NonWritableChannelException(); }

    @Override
    public long position() { return position; }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        if (newPosition < 0) throw new IllegalArgumentException("negative position");
        ensureOpen();
        position = newPosition;
        return this;
    }

    @Override
    public long size() throws IOException {
        ensureOpen();
        long len = rac.length();
        LOG.trace("size() -> {}", len);
        return len;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException { throw new NonWritableChannelException(); }

    @Override
    public boolean isOpen() { return open; }

    @Override
    public void close() throws IOException {
        if (open) {
            open = false;
            try {
                in.close();
            } finally {
                rac.close();
            }
            LOG.trace("close() done");
        }
    }
}
