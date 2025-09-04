package eu.nahoj.fusebox.common;

import org.cryptomator.jfuse.api.Fuse;
import org.cryptomator.jfuse.api.FuseMountFailedException;
import org.cryptomator.jfuse.api.FuseOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.TimeoutException;

public class Driver {

    private static final Logger LOG = LoggerFactory.getLogger(Driver.class);

    public static void mount(String progName, FuseOperations fuseOperations, String mountPoint) {
        LOG.info("Mounting Fuse filesystem at {}", mountPoint);

        try (Fuse fuse = Fuse.builder().build(fuseOperations)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    fuse.close();
                } catch (TimeoutException e) {
                    LOG.error("Unmount timed out", e);
                }
            }));
            fuse.mount(
                    progName,
                    Path.of(mountPoint),
                    "-o", "auto_unmount",
                    "-o", "attr_timeout=1",
                    "-o", "entry_timeout=1",
                    "-o", "negative_timeout=1"
            );
            LOG.info("Mounted");
//            LOG.info("Press Enter to unmount...");
//            val _ = System.in.read();
            Thread.sleep(Long.MAX_VALUE);
        } catch (FuseMountFailedException e) {
            LOG.error("Mount failed", e);
        } catch (TimeoutException e) {
            LOG.error("Unmount timed out", e);
//        } catch (IOException e) {
//            LOG.error("IO error", e);
        } catch (InterruptedException e) {
            LOG.info("Interrupted");
        }
    }
}
