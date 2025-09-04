package eu.nahoj.fusebox.vfs2.driven;

import eu.nahoj.fusebox.vfs2.util.RandomAccessContentSeekableByteChannel;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.RandomAccessContent;
import org.apache.commons.vfs2.VFS;
import org.apache.commons.vfs2.util.RandomAccessMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RandomAccessContentSeekableByteChannelIT {

    private @Nullable FileObject rootFo;

    @AfterEach
    void tearDown() throws Exception {
        if (rootFo != null) rootFo.close();
    }

    @Test
    void full_read_large_file_returns_expected() throws Exception {
        // Prepare temp fs with >8KB content to force multiple read() iterations in contentAsString
        Path tmp = Files.createTempDirectory("rac-it-large");
        Path file = tmp.resolve("large.txt");
        String unit = "0123456789"; // 10 bytes
        String expected = unit.repeat(2000); // 20,000 bytes
        Files.writeString(file, expected, StandardCharsets.UTF_8);

        // Build VFS root
        FileSystemManager mgr = VFS.getManager();
        rootFo = mgr.resolveFile(tmp.toUri().toString());

        FileObject fo = rootFo.resolveFile("large.txt");
        RandomAccessContent rac = fo.getContent().getRandomAccessContent(RandomAccessMode.READ);
        try (RandomAccessContentSeekableByteChannel ch = new RandomAccessContentSeekableByteChannel(rac)) {
            String s = contentAsString(ch);
            assertThat(s).hasSize(expected.length());
            assertThat(s).isEqualTo(expected);
        }
    }

    @Test
    void read_then_seek_to_zero_then_full_read_returns_expected() throws Exception {
        // Prepare temp fs
        Path tmp = Files.createTempDirectory("rac-it-seek");
        Path file = tmp.resolve("seek.txt");
        String content = "abcdef";
        Files.writeString(file, content, StandardCharsets.UTF_8);

        // Build VFS root
        FileSystemManager mgr = VFS.getManager();
        rootFo = mgr.resolveFile(tmp.toUri().toString());

        FileObject fo = rootFo.resolveFile("seek.txt");
        RandomAccessContent rac = fo.getContent().getRandomAccessContent(RandomAccessMode.READ);
        try (RandomAccessContentSeekableByteChannel ch = new RandomAccessContentSeekableByteChannel(rac)) {
            // Read first 3 bytes
            ByteBuffer first = ByteBuffer.allocate(3);
            int n1 = ch.read(first);
            assertThat(n1).isEqualTo(3);
            first.flip();
            byte[] a1 = new byte[first.remaining()];
            first.get(a1);
            assertThat(new String(a1, StandardCharsets.UTF_8)).isEqualTo("abc");

            // Seek back to start
            ch.position(0);

            // Read 6 bytes from start again
            ByteBuffer second = ByteBuffer.allocate(6);
            int n2 = ch.read(second);
            assertThat(n2).isEqualTo(6);
            second.flip();
            byte[] a2 = new byte[second.remaining()];
            second.get(a2);
            assertThat(new String(a2, StandardCharsets.UTF_8)).isEqualTo("abcdef");
        }
    }

    @Test
    void eof_after_full_read_returns_minus_one() throws Exception {
        // Prepare temp fs
        Path tmp = Files.createTempDirectory("rac-it-eof");
        Path file = tmp.resolve("eof.txt");
        String content = "abcdef";
        Files.writeString(file, content, StandardCharsets.UTF_8);

        // Build VFS root
        FileSystemManager mgr = VFS.getManager();
        rootFo = mgr.resolveFile(tmp.toUri().toString());

        FileObject fo = rootFo.resolveFile("eof.txt");
        RandomAccessContent rac = fo.getContent().getRandomAccessContent(RandomAccessMode.READ);
        try (RandomAccessContentSeekableByteChannel ch = new RandomAccessContentSeekableByteChannel(rac)) {
            ByteBuffer buf = ByteBuffer.allocate(content.length());
            int n1 = ch.read(buf);
            assertThat(n1).isEqualTo(content.length());
            buf.clear();
            int n2 = ch.read(buf);
            assertThat(n2).isEqualTo(-1);
        }
    }

    @Test
    void seek_then_read_slice_returns_expected() throws Exception {
        // Prepare temp fs
        Path tmp = Files.createTempDirectory("rac-it-slice");
        Path file = tmp.resolve("slice.txt");
        String expected = "hello world";
        Files.writeString(file, expected, StandardCharsets.UTF_8);

        // Build VFS root
        FileSystemManager mgr = VFS.getManager();
        rootFo = mgr.resolveFile(tmp.toUri().toString());

        FileObject fo = rootFo.resolveFile("slice.txt");
        RandomAccessContent rac = fo.getContent().getRandomAccessContent(RandomAccessMode.READ);
        try (RandomAccessContentSeekableByteChannel ch = new RandomAccessContentSeekableByteChannel(rac)) {
            ch.position(6);
            ByteBuffer buf = ByteBuffer.allocate(5);
            int n = ch.read(buf); // read "world"
            assertThat(n).isEqualTo(5);
            buf.flip();
            byte[] arr = new byte[buf.remaining()];
            buf.get(arr);
            assertThat(new String(arr, StandardCharsets.UTF_8)).isEqualTo("world");
        }
    }

    private static String contentAsString(SeekableByteChannel ch) throws Exception {
        long original = ch.position();
        try {
            ch.position(0);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            while (true) {
                int n = ch.read(buffer);
                if (n <= 0) break;
                buffer.flip();
                out.write(buffer.array(), 0, buffer.limit());
                buffer.clear();
            }
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            ch.position(original);
        }
    }
}
