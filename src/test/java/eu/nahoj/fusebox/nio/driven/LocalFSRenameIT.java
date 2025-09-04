package eu.nahoj.fusebox.nio.driven;

import eu.nahoj.fusebox.nio.FSTestHelper;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalFSRenameIT {

    private final FSTestHelper helper = new FSTestHelper();

    @BeforeAll @SneakyThrows void mount() { helper.mountOnce(); }
    @AfterAll @SneakyThrows void unmount() { helper.unmountOnce(); }

    private Path mnt(String rel) { return helper.mnt(rel); }

    @Test
    @SneakyThrows
    void rename_succeeds_when_destination_missing() {
        Path a = mnt("rn-a.txt");
        Path b = mnt("rn-b.txt");
        Files.writeString(a, "aaa", CREATE, TRUNCATE_EXISTING);
        if (Files.exists(b)) Files.delete(b);
        Files.move(a, b, ATOMIC_MOVE);
        assertThat(Files.exists(a)).isFalse();
        assertThat(Files.readString(b)).isEqualTo("aaa");
    }

    @Test
    @SneakyThrows
    void should_overwrite_existing() {
        // Given two paths a and b on the mount
        Path a = mnt("a.txt");
        Path b = mnt("b.txt");
        Files.writeString(a, "aaa", CREATE, TRUNCATE_EXISTING);
        // When renaming a -> b atomically
        Files.move(a, b, ATOMIC_MOVE);
        // Then a should not exist and b should hold the content
        assertThat(Files.exists(a, NOFOLLOW_LINKS)).isFalse();
        assertThat(Files.readString(b)).isEqualTo("aaa");
    }

    @Test
    @SneakyThrows
    void rename_noreplace_does_not_overwrite_existing() {
        Path a = mnt("rn2-a.txt");
        Path b = mnt("rn2-b.txt");
        Files.writeString(a, "aaa", CREATE, TRUNCATE_EXISTING);
        Files.writeString(b, "bbb", CREATE, TRUNCATE_EXISTING);
        // Use mv -n to request NOREPLACE semantics via the kernel
        val p = new ProcessBuilder("mv", "-n", a.toString(), b.toString())
                .redirectErrorStream(true)
                .start();
        int ec = p.waitFor();
        assertThat(ec).isZero();
        // Destination should remain unchanged and source should still exist
        assertThat(Files.readString(b)).isEqualTo("bbb");
        assertThat(Files.exists(a)).isTrue();
    }
}