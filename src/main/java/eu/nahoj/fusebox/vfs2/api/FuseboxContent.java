package eu.nahoj.fusebox.vfs2.api;

import eu.nahoj.fusebox.common.util.ExceptionUtils.ThrowingFunction;
import eu.nahoj.fusebox.vfs2.transform.StringContent;
import eu.nahoj.fusebox.vfs2.util.ThreadSafeSeekableByteChannel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static eu.nahoj.fusebox.common.util.ExceptionUtils.uncheckedIO;

/**
 * Offset-addressable readable handle used by Fusebox's VFS2 layer.
 * Provides a thread-safe SeekableByteChannel that supports atomic offset reads.
 */
public interface FuseboxContent extends AutoCloseable {

    <T> T withByteChannel(Function<? super ThreadSafeSeekableByteChannel, T> function) throws IOException;

    default <T> T withByteChannelUncheckedIO(ThrowingFunction<? super ThreadSafeSeekableByteChannel, T> function) {
        try {
            return withByteChannel(uncheckedIO(function));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Always close resources. */
    @Override
    void close() throws IOException;

    /**
     * Returns the size in bytes if known, or -1 if unknown.
     */
    default long size() {
        return withByteChannelUncheckedIO(ThreadSafeSeekableByteChannel::size);
    }

    default String asString() {
        return withByteChannelUncheckedIO(ThreadSafeSeekableByteChannel::contentAsString);
    }

    default FuseboxContent mapAsString(UnaryOperator<String> mapper) {
        return new StringContent(mapper.apply(asString()));
    }
}
