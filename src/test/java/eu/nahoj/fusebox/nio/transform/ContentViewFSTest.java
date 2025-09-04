package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.TestFileInfo;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import eu.nahoj.fusebox.nio.driven.HelloHiddenFS;
import org.cryptomator.jfuse.api.FileInfo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentViewFSTest {

    private static final String README = "/.docs/.readme.txt";
    private static final String HELLO = "/.hello.txt";

    @Test
    void transforms_selected_path_and_updates_size_after_open() throws Exception {
        // Delegate with hidden files
        HelloHiddenFS delegate = new HelloHiddenFS();

        // Compute source bytes directly from delegate
        byte[] source = readAll(delegate, README);

        // Generator that wraps content in minimal HTML (changes size)
        ContentGenerator gen = (path, src) ->
                ("<html><body><pre>" + new String(src, UTF_8) + "</pre></body></html>")
                        .getBytes(UTF_8);

        ContentViewFS fs = new ContentViewFS(delegate, README::equals, gen);

        // Before open: size is not smaller than transformed size (prevents truncation)
        FileAttributes before = fs.getattr(README, null);
        byte[] expected = gen.generate(README, source);
        assertThat(before.size()).isGreaterThanOrEqualTo(expected.length);

        // Open (materialize transformed content in memory)
        FileInfo fi = new TestFileInfo(0, 0, Set.of(StandardOpenOption.READ), 0L);
        fs.open(README, fi);

        // After open: size is exact transformed size
        FileAttributes after = fs.getattr(README, null);
        assertThat(after.size()).isEqualTo(expected.length);

        // Read should return transformed bytes
        byte[] actual = readAll(fs, README, fi);
        assertThat(actual).isEqualTo(expected);

        // Writes are blocked on transformed handles
        assertThatThrownBy(() -> fs.write(README, ByteBuffer.wrap(new byte[]{'X'}), 1, 0, fi))
                .isInstanceOf(ReadOnlyFileSystemException.class);

        // Release closes transformed view
        fs.release(README, fi);
    }

    @Test
    void non_matching_paths_are_passthrough() throws Exception {
        HelloHiddenFS delegate = new HelloHiddenFS();
        ContentViewFS fs = new ContentViewFS(delegate, README::equals, (p, s) -> s);

        FileAttributes attr = fs.getattr(HELLO, null);
        byte[] fromFs = readAll(fs, HELLO);
        byte[] fromDelegate = readAll(delegate, HELLO);

        // Size matches delegate and remains unchanged
        assertThat(attr.size()).isEqualTo(fromDelegate.length);
        assertThat(fromFs).isEqualTo(fromDelegate);
    }

    // ---- helpers ----

    private static byte[] readAll(FuseboxFS fs, String path) throws IOException {
        FileInfo fi = new TestFileInfo(0, 0, Set.of(StandardOpenOption.READ), 0L);
        fs.open(path, fi);
        try {
            return readAll(fs, path, fi);
        } finally {
            try {
                fs.release(path, fi);
            } catch (UnsupportedOperationException ignore) {
                // Underlying delegate (HelloHiddenFS) doesn't implement release(); ignore in unit test
            }
        }
    }

    private static byte[] readAll(FuseboxFS fs, String path, FileInfo fi) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(64 * 1024);
        long offset = 0;
        while (true) {
            int r = fs.read(path, buf, buf.remaining(), offset, fi);
            if (r <= 0) break;
            offset += r;
            if (!buf.hasRemaining()) break;
        }
        byte[] out = new byte[(int) offset];
        buf.flip();
        buf.get(out);
        return out;
    }
}
