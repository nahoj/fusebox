package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.vfs2.api.FuseboxContent;
import eu.nahoj.fusebox.vfs2.util.ThreadSafeSeekableByteChannel;
import lombok.Value;
import lombok.experimental.Accessors;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;

import java.io.IOException;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

@Value
@Accessors(fluent = true)
public class StringContent implements FuseboxContent {

    String content;
    long size;
    
    public StringContent(String content) {
        this.content = content;
        this.size = content.getBytes(UTF_8).length;
    }

    public String asString() {
        return content;
    }

    public <T> T withByteChannel(Function<? super ThreadSafeSeekableByteChannel, T> function) throws IOException {
        byte[] bytes = content.getBytes(UTF_8);
        try (var channel = new ThreadSafeSeekableByteChannel(new SeekableInMemoryByteChannel(bytes))) {
            return function.apply(channel);
        }
    }

    public void close() {
    }
}
