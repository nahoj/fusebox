package eu.nahoj.fusebox.common.util;

import lombok.Data;
import org.cryptomator.jfuse.api.FileInfo;

import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Set;

/**
 * Minimal concrete {@link FileInfo} implementation for internal use.
 */
@Data
public class SimpleFileInfo implements FileInfo {
    private long fh;
    private Set<StandardOpenOption> openFlags = Collections.emptySet();
    private long lockOwner;
    private int flags;
}
