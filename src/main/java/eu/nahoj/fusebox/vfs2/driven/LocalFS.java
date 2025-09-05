package eu.nahoj.fusebox.vfs2.driven;

import eu.nahoj.fusebox.common.api.StatvfsData;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import one.util.streamex.StreamEx;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.VFS;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.Statvfs;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static java.util.stream.Collectors.toCollection;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.STATFS;

public class LocalFS extends VfsFS {

    public LocalFS(FileObject root) {
        super(root);
    }

    public static LocalFS at(String path) throws IOException {
        return new LocalFS(VFS.getManager().resolveFile(Path.of(path).toUri()));
    }

    public FuseboxFile resolveFile(String path) throws IOException {
        return new LocalFuseboxFile(this, Path.of(path), root.resolveFile(path));
    }

    public Set<Operation> supportedOperations() {
        return StreamEx.of(LocalFuseboxFile.IMPLEMENTED_OPERATIONS)
                .filter(this::backingFsSupportsOperation)
                .append(STATFS)
                .collect(toCollection(() -> EnumSet.noneOf(Operation.class)));
    }

    /// Return stats for the given path's FileStore
    public Statvfs getStats(String path) throws IOException {
        var store = getFileStore(path);
        long blockSize = store.getBlockSize();
        return StatvfsData.builder()
                .bsize(blockSize)
                .frsize(blockSize)
                .blocks(store.getTotalSpace() / blockSize)
                .bfree(store.getUnallocatedSpace() / blockSize)
                .bavail(store.getUsableSpace() / blockSize)
                .nameMax(255)
                .build();
    }

    private FileStore getFileStore(String path) throws IOException {
        return Files.getFileStore(root.getPath().resolve(path));
    }
}
