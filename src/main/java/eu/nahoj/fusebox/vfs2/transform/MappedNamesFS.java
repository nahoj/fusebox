package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import lombok.RequiredArgsConstructor;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.Statvfs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNullElse;

@RequiredArgsConstructor
public class MappedNamesFS implements FuseboxFS {

    private static final Path EMPTY_PATH = Path.of("");

    private final FuseboxFS delegate;
    private final Predicate<String> origPathSelector;
    private final Predicate<String> mountPathSelector;

    private final UnaryOperator<String> origNameToMount;
    private final UnaryOperator<String> mountNameToOrig;

    // ---------- Path mapping helpers ----------

    private Path mountPathToOrigPath(String mountPath) {
        // Rename ALL path prefixes whose mount path matches the selector.
        Path relMountPrefix = EMPTY_PATH;
        Path relOrigPrefix = EMPTY_PATH;
        for (Path part : Path.of(mountPath)) {
            String mountName = part.toString();
            relMountPrefix = relMountPrefix.resolve(mountName);
            String origName = mountPathSelector.test(relMountPrefix.toString())
                    ? mountNameToOrig.apply(mountName)
                    : mountName;
            relOrigPrefix = relOrigPrefix.resolve(origName);
        }
        return relOrigPrefix;
    }

    private String mountPathToOrig(String mountPath) {
        return mountPathToOrigPath(mountPath).toString();
    }

    @Override
    public Set<Operation> supportedOperations() {
        return delegate.supportedOperations();
    }

    @Override
    public Statvfs getStats(String path) throws IOException {
        return delegate.getStats(mountPathToOrig(path));
    }

    @Override
    public FuseboxFile resolveFile(String path) throws IOException {
        Path origPath = mountPathToOrigPath(path);
        FuseboxFile origFile = delegate.resolveFile(origPath.toString());
        Path relOrigDirPath = requireNonNullElse(origPath.getParent(), EMPTY_PATH);
        return new MappedNamesFile(origFile, relOrigDirPath, origPathSelector, origNameToMount);
    }
}
