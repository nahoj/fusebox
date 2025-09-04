package eu.nahoj.fusebox;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.cryptomator.jfuse.api.FileInfo;

import java.nio.file.StandardOpenOption;
import java.util.Set;

@Data
@AllArgsConstructor
public class TestFileInfo implements FileInfo {
    private long fh;
    private int flags;
    private Set<StandardOpenOption> openFlags;
    private long lockOwner;
}
