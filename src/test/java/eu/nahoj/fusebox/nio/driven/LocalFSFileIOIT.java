package eu.nahoj.fusebox.nio.driven;

import eu.nahoj.fusebox.nio.FSTestHelper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalFSFileIOIT {

    private final FSTestHelper helper = new FSTestHelper();

    @BeforeAll @SneakyThrows void mount() { helper.mountOnce(); }
    @AfterAll @SneakyThrows void unmount() { helper.unmountOnce(); }

    private Path mnt(String rel) { return helper.mnt(rel); }

    @Test
    @SneakyThrows
    void create_open_write_read_truncate_release() {
        // Given a target file path on the mount
        Path p = mnt("io.bin");
        // When creating the file with initial content
        Files.write(p, new byte[]{1,2,3,4}, CREATE, TRUNCATE_EXISTING, WRITE);
        // And When opening and appending more bytes
        try (SeekableByteChannel ch = Files.newByteChannel(p, EnumSet.of(WRITE, APPEND))) {
            ch.write(ByteBuffer.wrap(new byte[]{5,6}));
            // Then release happens on close
        }
        // Then the content should reflect both writes
        byte[] content = Files.readAllBytes(p);
        assertThat(content).containsExactly(1,2,3,4,5,6);

        // When truncating the file to zero length
        try (SeekableByteChannel _ = Files.newByteChannel(p, EnumSet.of(WRITE, TRUNCATE_EXISTING))) {
            // truncate to 0
        }
        // Then the file size should be zero
        assertThat(Files.size(p)).isZero();
    }

    @Test
    @SneakyThrows
    void unlink() {
        // Given
        Path a = mnt("a.txt");
        Files.writeString(a, "aaa", CREATE, TRUNCATE_EXISTING);
        // When deleting a
        Files.delete(a);
        // Then a should no longer exist
        assertThat(Files.exists(a, NOFOLLOW_LINKS)).isFalse();
    }
}
