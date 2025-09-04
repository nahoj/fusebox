package org.cryptomator.jfuse.examples;

import org.cryptomator.jfuse.api.DirFiller;
import org.cryptomator.jfuse.api.Errno;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseMountFailedException;
import org.cryptomator.jfuse.api.FuseOperations;
import org.cryptomator.jfuse.api.Stat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;

public class HelloFuse implements FuseOperations {

    private static final String HELLO_STR = "Hello, jfuse World!\n";
    private static final Path HELLO_PATH = Paths.get("/hello.txt");

    // Provide platform errno
    @Override
    public Errno errno() {
        return Fuse.builder().errno();
    }

    // Advertise only the operations we actually implement
    @Override
    public Set<Operation> supportedOperations() {
        return EnumSet.of(Operation.GET_ATTR, Operation.READ_DIR, Operation.READ, Operation.OPEN);
    }

    @Override
    public int getattr(String path, Stat stat, FileInfo fi) {
        if (path.equals(HELLO_PATH.toString())) {
            stat.setMode(Stat.S_IFREG | 0444); // Regular file with read-only permissions
            stat.setNLink((short) 1);
            stat.setSize(HELLO_STR.getBytes().length);
            return 0;
        }
        if (path.equals("/")) {
            stat.setMode(Stat.S_IFDIR | 0755); // Directory with rwx-rx-rx
            stat.setNLink((short) 2);
            return 0;
        }
        return -errno().enoent();
    }

    @Override
    public int readdir(String path, DirFiller filler, long offset, FileInfo fi, int flags) {
        if (!path.equals("/")) {
            return -errno().enoent();
        }
        try {
            filler.fill(".");
            filler.fill("..");
            filler.fill(HELLO_PATH.getFileName().toString());
            return 0;
        } catch (IOException e) {
            return -errno().eio();
        }
    }

    @Override
    public int read(String path, ByteBuffer buf, long size, long offset, FileInfo fi) {
        if (!path.equals(HELLO_PATH.toString())) {
            return -errno().enoent();
        }
        byte[] bytes = HELLO_STR.getBytes();
        int length = bytes.length;
        if (offset >= length) {
            return 0; // Past the end of the file
        }
        int bytesToRead = (int) Math.min(length - offset, size);
        buf.put(bytes, (int) offset, bytesToRead);
        return bytesToRead;
    }

    @Override
    public int open(String path, FileInfo fi) {
        if (path.equals(HELLO_PATH.toString())) {
            return 0;
        }
        return -errno().enoent();
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -cp ... --enable-native-access=ALL-UNNAMED org.cryptomator.jfuse.examples.HelloFuse <mountpoint>");
            System.exit(1);
        }

        Path mountPoint = Paths.get(args[0]);
        HelloFuse fs = new HelloFuse();

        System.out.println("Mounting filesystem at " + mountPoint);
        System.out.println("Press Ctrl+C to unmount.");

        // Use try-with-resources for automatic unmount on exit
        try (Fuse fuse = Fuse.builder().build(fs)) {
            // Mount with a descriptive name
            fuse.mount("hello-fs", mountPoint);

            // Block until Ctrl+C
            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));
            latch.await();
        } catch (FuseMountFailedException e) {
            System.err.println("Mount failed: " + e.getMessage());
        } catch (TimeoutException e) {
            System.err.println("Unmount timed out: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}