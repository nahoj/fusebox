package eu.nahoj.fusebox.vfs2.util;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safety wrapper for a SeekableByteChannel that serializes all operations
 * using the provided lock. Useful when the underlying channel isn't thread-safe
 * or when multiple entry points must coordinate (e.g. readAt vs channel reads).
 */
@RequiredArgsConstructor
public final class ThreadSafeSeekableByteChannel implements SeekableByteChannel {

    private static final Logger LOG = LoggerFactory.getLogger(ThreadSafeSeekableByteChannel.class);

    private final SeekableByteChannel delegate;
    private final Lock lock = new ReentrantLock();

    /**
     * Atomically reads at the given absolute offset without exposing composite operations
     * to callers. Restores the original position afterward.
     */
    public int readAt(ByteBuffer dst, long offset) throws IOException {
        lock.lock();
        try {
            long originalPosition = delegate.position();
            try {
                delegate.position(offset);
                return delegate.read(dst);
            } finally {
                try { delegate.position(originalPosition); } catch (IOException ignore) {}
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reads the entire content of a SeekableByteChannel into a String
     * without altering the channel's final position.
     */
    public String contentAsString() throws IOException {
        lock.lock();
        try {
            long originalPosition = delegate.position();
            try {
                delegate.position(0);
                InputStream inputStream = Channels.newInputStream(delegate);
                byte[] bytes = inputStream.readAllBytes();
                LOG.trace("contentAsString() -> {} bytes", bytes.length);
                return new String(bytes, StandardCharsets.UTF_8);
            } finally {
                delegate.position(originalPosition);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        lock.lock();
        try {
            return delegate.read(dst);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        lock.lock();
        try {
            return delegate.write(src);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long position() throws IOException {
        lock.lock();
        try {
            return delegate.position();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        lock.lock();
        try {
            delegate.position(newPosition);
            return this;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long size() throws IOException {
        lock.lock();
        try {
            return delegate.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        lock.lock();
        try {
            delegate.truncate(size);
            return this;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isOpen() {
        // No locking necessary for a simple check
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        lock.lock();
        try {
            LOG.trace("close()" );
            delegate.close();
        } finally {
            lock.unlock();
        }
    }
}
