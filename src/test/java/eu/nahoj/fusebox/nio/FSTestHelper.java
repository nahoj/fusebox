package eu.nahoj.fusebox.nio;

import eu.nahoj.fusebox.TestDriver;
import eu.nahoj.fusebox.nio.driven.LocalFS;
import eu.nahoj.fusebox.nio.driving.FuseboxFSOperations;
import lombok.val;
import org.apache.commons.lang3.ArrayUtils;
import org.assertj.core.api.Assertions;
import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseMountFailedException;
import org.cryptomator.jfuse.examples.PosixMirrorFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

public class FSTestHelper {

    private static final Logger LOG = LoggerFactory.getLogger(FSTestHelper.class);

    private Path srcDir;
    private Path mntDir;
    private Fuse fuse;

    public Path srcDir() { return srcDir; }
    public Path mntDir() { return mntDir; }
    public Path mnt(String rel) { return mntDir.resolve(rel); }

    public void mountOnce() throws IOException, FuseMountFailedException, TimeoutException {
        Path target = Path.of("target");
        Files.createDirectories(target);
        srcDir = Files.createTempDirectory(target, "it-src-");
        mntDir = Files.createTempDirectory(target, "it-mnt-");

        // Seed content
        Files.createDirectories(srcDir.resolve("dir"));
        Files.writeString(srcDir.resolve("hello.txt"), "hello", CREATE, TRUNCATE_EXISTING);
        Files.writeString(srcDir.resolve("dir/nested.txt"), "nested", CREATE, TRUNCATE_EXISTING);

        // Mount
        fuse = mountFilesystem(srcDir, mntDir);

        // Safety net on abrupt JVM termination (e.g., IDE stop)
        Runtime.getRuntime().addShutdownHook(new Thread(this::safeUnmountAndCleanup));
    }

    public void unmountOnce() {
        safeUnmountAndCleanup();
    }

    private Fuse mountFilesystem(Path source, Path mount) throws FuseMountFailedException, TimeoutException {
        // Determine which FS implementation to use: default "my" or "posix" via -Dfusebox.it.fs=posix
        String kind = System.getProperty("fusebox.it.fs");
        if (kind == null || kind.isBlank()) kind = "my";

        // Common mount options for deterministic tests
        String fsname = "fusebox-it-" + ProcessHandle.current().pid();
        boolean singleThread = Boolean.getBoolean("fusebox.it.singleThread");
        String[] optsBase = new String[]{
                "-o", "auto_unmount",
                "-o", "fsname=" + fsname,
                "-o", "attr_timeout=1",
                "-o", "entry_timeout=1",
                "-o", "negative_timeout=1"
        };
        String[] opts = singleThread ? ArrayUtils.add(optsBase, "-s") : optsBase;

        return switch (kind.toLowerCase()) {
            case "posix" -> {
                LOG.info("Mounting PosixMirrorFileSystem at {} from {}", mount, source);
                yield TestDriver.buildAndMount(errno -> new PosixMirrorFileSystem(source, errno), mount, opts);
            }
            default -> {
                LOG.info("Mounting LocalFS at {} from {}", mount, source);
                Path root = source.toAbsolutePath().normalize();
                yield TestDriver.buildAndMount(errno -> new FuseboxFSOperations(new LocalFS(root), errno), mount, opts);
            }
        };
    }

    private void safeUnmountAndCleanup() {
        try {
            if (fuse != null) {
                try { fuse.close(); } catch (Exception ignore) {}
            }
            if (mntDir != null) {
                // wait for the mount to disappear
                if (!waitForUnmount(mntDir, Duration.ofSeconds(5))) {
                    // fallback unmount attempts
                    forceUnmount(mntDir);
                    waitForUnmount(mntDir, Duration.ofSeconds(3));
                }
            }
        } finally {
            // cleanup directories if not mounted
            if (mntDir != null && !isMounted(mntDir)) deleteRecursivelyQuiet(mntDir);
            if (srcDir != null) deleteRecursivelyQuiet(srcDir);
        }
    }

    private boolean waitForUnmount(Path mountPoint, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (!isMounted(mountPoint)) return true;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return !isMounted(mountPoint);
    }

    private boolean isMounted(Path mountPoint) {
        Path abs = mountPoint.toAbsolutePath().normalize();
        String needle = " " + abs + " ";
        Path mountinfo = Path.of("/proc/self/mountinfo");
        if (!Files.isReadable(mountinfo)) return false;
        try (BufferedReader br = Files.newBufferedReader(mountinfo)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains(needle)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private void forceUnmount(Path mountPoint) {
        runCmdQuiet("fusermount3", "-u", mountPoint.toString());
        if (isMounted(mountPoint)) runCmdQuiet("fusermount3", "-uz", mountPoint.toString());
        if (isMounted(mountPoint)) runCmdQuiet("umount", "-l", mountPoint.toString());
    }

    private void runCmdQuiet(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                while (r.readLine() != null) { /* drain */ }
            }
            p.waitFor();
        } catch (IOException | InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void deleteRecursivelyQuiet(Path dir) {
        try {
            if (Files.notExists(dir)) return;
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignore) {}
                });
            }
        } catch (IOException ignore) {}
    }

    public boolean isCmdAvailable(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-lc", "command -v " + cmd).start();
            int ec = p.waitFor();
            return ec == 0;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public void awaitTrue(BooleanSupplier cond, java.time.Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (cond.getAsBoolean()) return;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!cond.getAsBoolean()) {
            val _ = Assertions.fail("condition not met within " + timeout);
        }
    }
}
