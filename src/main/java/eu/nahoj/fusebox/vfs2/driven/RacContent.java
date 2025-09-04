package eu.nahoj.fusebox.vfs2.driven;

import eu.nahoj.fusebox.vfs2.api.FuseboxContent;
import eu.nahoj.fusebox.vfs2.util.RandomAccessContentSeekableByteChannel;
import eu.nahoj.fusebox.vfs2.util.ThreadSafeSeekableByteChannel;
import org.apache.commons.vfs2.RandomAccessContent;

import java.io.IOException;
import java.util.function.Function;

final class RacContent implements FuseboxContent {

    private final ThreadSafeSeekableByteChannel channel;
    private volatile boolean closed = false;

    RacContent(RandomAccessContent rac) {
        this.channel = new ThreadSafeSeekableByteChannel(new RandomAccessContentSeekableByteChannel(rac));
    }

    @Override
    public void close() throws IOException {
        if (!closed) {
            closed = true;
            channel.close(); // closes RAC via the channel
        }
    }

    @Override
    public <T> T withByteChannel(Function<? super ThreadSafeSeekableByteChannel, T> function) {
        return function.apply(channel);
    }
}
