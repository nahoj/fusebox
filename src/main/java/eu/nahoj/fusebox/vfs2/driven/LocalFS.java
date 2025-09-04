package eu.nahoj.fusebox.vfs2.driven;

import eu.nahoj.fusebox.common.api.StatvfsData;
import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
import lombok.RequiredArgsConstructor;
import org.apache.commons.vfs2.Capability;
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

@RequiredArgsConstructor
public class LocalFS implements FuseboxFS {

    private final FileObject root;

    public static LocalFS at(String path) throws IOException {
        return new LocalFS(VFS.getManager().resolveFile(Path.of(path).toUri()));
    }

    public FuseboxFile resolveFile(String path) throws IOException {
        return new LocalFuseboxFile(root.resolveFile(path));
    }

    public Set<Operation> supportedOperations() {
        EnumSet<Operation> operations = LocalFuseboxFile.IMPLEMENTED_OPERATIONS.stream()
                .filter(this::backingFsSupportsOperation)
                .collect(toCollection(() -> EnumSet.noneOf(Operation.class)));
        if (!"file".equals(root.getName().getScheme())) {
            operations.remove(STATFS);
        }
        return operations;
    }

    private boolean backingFsSupportsOperation(Operation op) {
        var fs = root.getFileSystem();
        return switch (op) {
            case GET_ATTR -> fs.hasCapability(Capability.GET_TYPE);
            case GET_XATTR, SET_XATTR, LIST_XATTR, REMOVE_XATTR -> fs.hasCapability(Capability.ATTRIBUTES);
            // ACCESS (check permissions), CHMOD/CHOWN (POSIX perms/ownership) ?
            case UTIMENS -> fs.hasCapability(Capability.SET_LAST_MODIFIED_FOLDER)
                    || fs.hasCapability(Capability.SET_LAST_MODIFIED_FILE);

            case Operation.CREATE, Operation.MKDIR -> fs.hasCapability(Capability.CREATE);

            case OPEN_DIR, READ_DIR, RELEASE_DIR -> fs.hasCapability(Capability.LIST_CHILDREN);
            // RANDOM_ACCESS_READ over READ_CONTENT since this implementation uses RandomAccessContent.
            case OPEN, READ, RELEASE -> fs.hasCapability(Capability.RANDOM_ACCESS_READ);

            // Ambiguity: WRITE could map to WRITE_CONTENT or RANDOM_ACCESS_WRITE.
            // We choose RANDOM_ACCESS_WRITE due to offset-based writes in FUSE.
            case Operation.WRITE -> fs.hasCapability(Capability.RANDOM_ACCESS_WRITE);
            case Operation.TRUNCATE -> fs.hasCapability(Capability.RANDOM_ACCESS_SET_LENGTH);

            case Operation.UNLINK, Operation.RMDIR -> fs.hasCapability(Capability.DELETE);
            case Operation.RENAME -> fs.hasCapability(Capability.RENAME);

            default -> true;
        };
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
        if (!"file".equals(root.getName().getScheme())) {
            throw new UnsupportedOperationException();
        }
        return Files.getFileStore(root.getPath().resolve(path));
    }
}
