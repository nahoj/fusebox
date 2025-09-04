package eu.nahoj.fusebox.nio.driven;

import eu.nahoj.fusebox.nio.FSTestHelper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.time.Duration;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalFSLinksIT {

    private final FSTestHelper helper = new FSTestHelper();

    @BeforeAll @SneakyThrows void mount() { helper.mountOnce(); }
    @AfterAll @SneakyThrows void unmount() { helper.unmountOnce(); }

    private Path mnt(String rel) { return helper.mnt(rel); }

    @Test
    @SneakyThrows
    void symlink_and_readlink() {
        // Given a unique link name and an existing target file
        Path link = mnt("hl-" + System.nanoTime());
        // When creating the symbolic link to hello.txt
        Files.createSymbolicLink(link, Path.of("hello.txt"));
        // Then it should appear in the filesystem
        helper.awaitTrue(() -> Files.exists(link, NOFOLLOW_LINKS), Duration.ofMillis(500));
        // And Then readlink should return the expected target
        assertThat(Files.readSymbolicLink(link)).isEqualTo(Path.of("hello.txt"));
        // And Then reading through the symlink yields the same content as the source
        String expected = Files.readString(helper.srcDir().resolve("hello.txt"));
        assertThat(Files.readString(link)).isEqualTo(expected);
    }

    @Test
    @SneakyThrows
    void readlink_on_nonexistent_path_returns_NoSuchFile() {
        // Given a path that doesn't exist
        Path missing = mnt("no-such-symlink-" + System.nanoTime());
        // When invoking readlink
        assertThatThrownBy(() -> Files.readSymbolicLink(missing))
                // Then Java surfaces NoSuchFileException (FUSE maps ENOENT)
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    @SneakyThrows
    void readlink_on_non_symlink_returns_NotLinkException() {
        // Given a regular file
        Path p = mnt("notalink.txt");
        Files.writeString(p, "x", CREATE, TRUNCATE_EXISTING);
        // When trying to readlink a non-symlink
        assertThatThrownBy(() -> Files.readSymbolicLink(p))
                // Then Java should surface NotLinkException (backend may map ENOENT/EIO, but API is NotLink)
                .isInstanceOf(NotLinkException.class);
    }
}
