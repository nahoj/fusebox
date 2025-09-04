package eu.nahoj.fusebox;

import lombok.val;
import org.cryptomator.jfuse.api.Errno;
import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseMountFailedException;
import org.cryptomator.jfuse.api.FuseOperations;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

/**
 * Centralizes common jfuse initialization and mounting so it can be reused by Main and tests.
 */
public final class TestDriver {

    private TestDriver() {}

    @FunctionalInterface
    public interface FsProvider {
        FuseOperations create(Errno errno) throws Exception;
    }

    /**
     * Builds a Fuse instance with the provided filesystem operations and mounts it at the given mount point.
     * The caller is responsible for closing the returned Fuse (e.g., try-with-resources).
     */
    public static Fuse buildAndMount(FsProvider fsProvider, Path mountPoint, String... mountOptions)
            throws FuseMountFailedException, TimeoutException {
        val builder = Fuse.builder();
        val libPath = System.getProperty("fuse.lib.path");
        if (libPath != null && !libPath.isEmpty()) builder.setLibraryPath(libPath);
        try {
            val ops = fsProvider.create(builder.errno());
            val fuse = builder.build(ops);
            // Ensure unmount on JVM shutdown as a safety net
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    fuse.close();
                } catch (Exception ignored) {
                }
            }));
            fuse.mount("jfuse", mountPoint, mountOptions);
            return fuse;
        } catch (FuseMountFailedException | TimeoutException e) {
            throw e;
        } catch (Exception e) {
            // Wrap any other exception (e.g., IO during fs construction) as a runtime to avoid leaking checked types.
            throw new RuntimeException(e);
        }
    }
}
