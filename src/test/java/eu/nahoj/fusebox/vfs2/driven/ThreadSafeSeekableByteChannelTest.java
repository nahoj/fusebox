package eu.nahoj.fusebox.vfs2.driven;

import eu.nahoj.fusebox.vfs2.util.ThreadSafeSeekableByteChannel;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadSafeSeekableByteChannelTest {

    @Nested
    class ContentAsString {

        @Test
        void contentAsString_preserves_position() throws Exception {
            byte[] data = "abcdef".getBytes(StandardCharsets.UTF_8);
            ThreadSafeSeekableByteChannel ch = new ThreadSafeSeekableByteChannel(new SeekableInMemoryByteChannel(data));

            ch.position(3); // simulate caller position not at start
            long before = ch.position();
            String s = ch.contentAsString();
            long after = ch.position();

            assertThat(s).isEqualTo("abcdef");
            assertThat(after).isEqualTo(before);
        }
    }

    @Nested
    class ReadAt {

        @Test
        void readAt_reads_correct_slice_and_does_not_change_position() throws Exception {
            byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
            ThreadSafeSeekableByteChannel ch = new ThreadSafeSeekableByteChannel(new SeekableInMemoryByteChannel(data));

            ch.position(5);
            ByteBuffer buf = ByteBuffer.allocate(5);
            int n = ch.readAt(buf, 6); // read "world"
            assertThat(n).isEqualTo(5);
            buf.flip();
            byte[] arr = new byte[buf.remaining()];
            buf.get(arr);
            assertThat(new String(arr, StandardCharsets.UTF_8)).isEqualTo("world");
            assertThat(ch.position()).isEqualTo(5); // unchanged
        }

        @Test
        void concurrent_readAt_calls_return_correct_slices_and_preserve_position() throws Exception {
            String content = "0123456789".repeat(1000); // 10,000 bytes
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            ThreadSafeSeekableByteChannel ch = new ThreadSafeSeekableByteChannel(new SeekableInMemoryByteChannel(data));

            int tasks = 16;
            int slice = 256; // bytes per task
            List<Future<String>> futures;
            try (ExecutorService pool = Executors.newFixedThreadPool(tasks)) {
                List<Callable<String>> jobs = new ArrayList<>();
                for (int i = 0; i < tasks; i++) {
                    final int idx = i;
                    jobs.add(() -> {
                        long off = (long) idx * slice;
                        ByteBuffer buf = ByteBuffer.allocate(slice);
                        int n = ch.readAt(buf, off);
                        buf.flip();
                        byte[] arr = new byte[n];
                        buf.get(arr);
                        return new String(arr, StandardCharsets.UTF_8);
                    });
                }
                futures = pool.invokeAll(jobs);
            }

            // Validate each slice equals the substring of the source
            for (int i = 0; i < tasks; i++) {
                String got = get(futures.get(i));
                int start = i * slice;
                String exp = content.substring(start, start + slice);
                assertThat(got).isEqualTo(exp);
            }

            // Position must remain unchanged (still at initial 0)
            assertThat(ch.position()).isEqualTo(0);
        }
    }

    private static <T> T get(Future<T> f) throws ExecutionException, InterruptedException {
        return f.get();
    }
}
