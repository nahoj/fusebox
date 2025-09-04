package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.Statvfs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
public class MappedNamesFS implements FuseboxFS {

    public static final Path EMPTY_PATH = Path.of("");

    private final FuseboxFS delegate;
    private final Predicate<String> origPathSelector;
    private final Predicate<String> mountPathSelector;

    private final UnaryOperator<String> origNameToMount;
    private final UnaryOperator<String> mountNameToOrig;

    // ---------- Path mapping helpers ----------

    private static Pair<Path, Path> resolveBeforeAndAfter(
            Pair<Path, Path> parentBeforeAndAfter,
            String beforeName,
            Predicate<String> beforePathSelector,
            UnaryOperator<String> beforeNameToAfter
    ) {
        Path newBeforePath = parentBeforeAndAfter.getLeft().resolve(beforeName);
        String afterName = beforePathSelector.test(newBeforePath.toString())
                ? beforeNameToAfter.apply(beforeName)
                : beforeName;
        Path newAfterPath = parentBeforeAndAfter.getRight().resolve(afterName);
        return Pair.of(newBeforePath, newAfterPath);
    }

    static Path translatePath(
            Path beforePath,
            Predicate<String> beforePathSelector,
            UnaryOperator<String> beforeNameToAfter
    ) {
        // Rename ALL path prefixes whose before-path matches the selector.
        Pair<Path, Path> beforeAndAfterPrefixes = Pair.of(EMPTY_PATH, EMPTY_PATH);
        for (Path part : beforePath) {
            beforeAndAfterPrefixes = resolveBeforeAndAfter(beforeAndAfterPrefixes, part.toString(),
                    beforePathSelector, beforeNameToAfter);
        }
        return beforeAndAfterPrefixes.getRight();
    }

    private Path mountPathToOrig(Path mountPath) {
        return translatePath(mountPath, mountPathSelector, mountNameToOrig);
    }

    @Override
    public Set<Operation> supportedOperations() {
        return delegate.supportedOperations();
    }

    @Override
    public Statvfs getStats(String path) throws IOException {
        return delegate.getStats(mountPathToOrig(Path.of(path)).toString());
    }

    @Override
    public FuseboxFile resolveFile(String path) throws IOException {
        Path mountPath = Path.of(path);
        Path origPath = this.mountPathToOrig(mountPath);
        FuseboxFile origFile = delegate.resolveFile(origPath.toString());
        return new MappedNamesFile(this, mountPath, origFile, origPathSelector, origNameToMount);
    }
}
