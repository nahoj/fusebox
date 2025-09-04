package eu.nahoj.fusebox.nio.driven;

import eu.nahoj.fusebox.nio.FSTestHelper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency tests that expose weaknesses of a RandomAccessContent (RAC)-based backend
 * when combined with simplistic try-with-resources lifetimes around a shared VFS FileContent.
 * Expectations follow POSIX semantics:
 * - Renaming a path must not invalidate already-open file descriptors/handles.
 * - Replacing a path by unlink+move should not break existing writers; they keep writing
 *   to the unlinked inode until they close.
 * The former "simple try + RAC" approach tended to share/close the underlying content on
 * unrelated path operations, so concurrent rename/replace could surface EIO/"stream closed".
 * The two tests below deliberately stress these scenarios and should NOT throw from the writer.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LocalFSConcurrencyIT {

    private final FSTestHelper helper = new FSTestHelper();

    @BeforeAll @SneakyThrows void mount() { helper.mountOnce(); }
    @AfterAll @SneakyThrows void unmount() { helper.unmountOnce(); }

    private Path mnt(String rel) { return helper.mnt(rel); }

    // Rename back-and-forth while a writer keeps an open handle.
    // Expectation: an open handle remains valid across atomic renames (typical POSIX behavior).
    // RAC/simple-try often ties the handle to a shared FileContent and closes it on rename,
    // which results in EIO/"Stream closed" during subsequent writes.
    @Test
    @SneakyThrows
    void write_while_rename_back_and_forth() {
        Path p = mnt("race4.bin");
        Path p2 = mnt("race4.bin.tmp");
        Files.deleteIfExists(p);
        Files.deleteIfExists(p2);
        Files.write(p, new byte[0], CREATE, TRUNCATE_EXISTING);

        AtomicReference<Throwable> err = new AtomicReference<>();

        // Writer holds an open descriptor on "p"; renames must not invalidate this handle.
        // If the implementation closes the shared content when the path is moved, writes below
        // will start failing (that is the failure we want to catch reliably).
        Thread writer = new Thread(() -> {
            try (SeekableByteChannel ch = Files.newByteChannel(p, EnumSet.of(WRITE, APPEND))) {
                byte[] data = new byte[]{1,2,3,4,5,6,7,8};
                for (int i = 0; i < 2000; i++) {
                    ch.write(ByteBuffer.wrap(data));
                    Thread.sleep(0, 500_000); // 0.5ms
                }
            } catch (Throwable t) { err.set(t); }
        }, "writer");

        Thread renamer = new Thread(() -> {
            try {
                // Toggle the path between p and p2 within the same directory so Files.move
                // remains an atomic rename. We ignore transient errors to keep pressure high;
                // the invariant is that the writer must not error.
                for (int i = 0; i < 2000; i++) {
                    try {
                        if (Files.exists(p)) {
                            Files.move(p, p2);
                        } else if (Files.exists(p2)) {
                            Files.move(p2, p);
                        }
                    } catch (IOException ignore) { /* best effort */ }
                    Thread.sleep(0, 500_000); // short nanos sleep to maximize interleavings
                }
            } catch (Throwable t) {
                // ignore; renamer is best-effort and may legitimately fail while writer holds handles
            }
        }, "renamer");

        writer.start();
        renamer.start();
        writer.join(Duration.ofSeconds(10).toMillis());
        renamer.join(Duration.ofSeconds(10).toMillis());

        assertThat(writer.isAlive()).isFalse();
        assertThat(renamer.isAlive()).isFalse();
        if (err.get() != null) {
            Assertions.fail("rename/write race caused exception: " + err.get());
        }

        // After the race, either p or p2 may be the final name. Validate one exists and has data.
        Path exists = Files.exists(p) ? p : (Files.exists(p2) ? p2 : null);
        assertThat(exists).isNotNull();
        assertThat(Files.size(exists)).isGreaterThan(0);
    }

    @Test
    @SneakyThrows
    void write_while_replaced_by_temp_moves() {
        // Long-lived writer while another thread repeatedly performs the classic editor save pattern:
        // write to a temp file, unlink the target, then move the temp over it. Semantically, existing
        // open writers should keep writing to the (now unlinked) inode. RAC/simple-try tends to share
        // a single underlying content and closes it on replacement, leading to EIO for the writer.
        Path p = mnt("race_replace.bin");
        Path tmp = mnt("race_replace.bin.tmp");
        Files.deleteIfExists(p);
        Files.deleteIfExists(tmp);
        Files.write(p, new byte[0], CREATE, TRUNCATE_EXISTING);

        AtomicReference<Throwable> err = new AtomicReference<>();

        // The writer appends small chunks continuously and must not fail regardless of replacements.
        Thread writer = new Thread(() -> {
            try (SeekableByteChannel ch = Files.newByteChannel(p, EnumSet.of(WRITE, APPEND))) {
                byte[] data = new byte[32];
                for (int i = 0; i < data.length; i++) data[i] = (byte)(i * 11);
                for (int i = 0; i < 4000; i++) {
                    ch.write(ByteBuffer.wrap(data));
                    Thread.sleep(0, 300_000);
                }
            } catch (Throwable t) { err.set(t); }
        }, "writer");

        Thread replacer = new Thread(() -> {
            try {
                // Replace the path by unlink + move (not a simple atomic rename of the same inode).
                // Many real-world tools do this to ensure durability and atomic path visibility.
                for (int i = 0; i < 2000; i++) {
                    try {
                        // Create a new tmp content
                        Files.write(tmp, ("content" + i).getBytes(), CREATE, TRUNCATE_EXISTING);
                        // Remove the current target, then move tmp to target
                        Files.deleteIfExists(p);
                        Files.move(tmp, p);
                    } catch (IOException ignore) { /* best effort */ }
                    Thread.sleep(0, 400_000); // keep cycles short to increase race probability
                }
            } catch (Throwable t) {
                // ignore; best-effort
            }
        }, "replacer");

        writer.start();
        replacer.start();
        writer.join(Duration.ofSeconds(12).toMillis());
        replacer.join(Duration.ofSeconds(12).toMillis());

        assertThat(writer.isAlive()).isFalse();
        assertThat(replacer.isAlive()).isFalse();
        if (err.get() != null) {
            Assertions.fail("replace-by-move/write race caused exception: " + err.get());
        }
        // The final path must exist (either last replacement or original survived) and, more
        // importantly, the writer must not have failed earlier.
        assertThat(Files.exists(p)).isTrue();
    }

}
