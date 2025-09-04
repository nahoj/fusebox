package eu.nahoj.fusebox.vfs2.transform;

import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import lombok.RequiredArgsConstructor;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.Statvfs;

import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

@RequiredArgsConstructor
public class MappedFilesFS implements FuseboxFS {

    private final FuseboxFS delegate;
    private final Predicate<String> pathSelector;
    private final UnaryOperator<FuseboxFile> mapper;

    @Override
    public Set<Operation> supportedOperations() {
        return delegate.supportedOperations();
    }

    @Override
    public Statvfs getStats(String path) throws IOException {
        return delegate.getStats(path);
    }

    @Override
    public FuseboxFile resolveFile(String path) throws IOException {
        FuseboxFile file = delegate.resolveFile(path);
        return pathSelector.test(path) ? mapper.apply(file) : file;
    }
}
