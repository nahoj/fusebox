package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.SetUtils;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.Statvfs;
import org.cryptomator.jfuse.api.TimeSpec;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.NoSuchFileException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.cryptomator.jfuse.api.FuseOperations.Operation.ACCESS;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.CHMOD;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.CHOWN;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.CREATE;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.DESTROY;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.FLUSH;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.FSYNC;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.FSYNCDIR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.GET_ATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.GET_XATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.INIT;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.LIST_XATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.MKDIR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.OPEN;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.OPEN_DIR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.READ;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.READLINK;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.READ_DIR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.RELEASE_DIR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.REMOVE_XATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.RMDIR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.SET_XATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.STATFS;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.TRUNCATE;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.UNLINK;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.UTIMENS;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.WRITE;

@Accessors(fluent = true)
@RequiredArgsConstructor
public class FilteredFS extends BaseFS {

    @Getter
    private final FuseboxFS delegate;

    /// The filter to apply to paths relative to the FS root (no leading '/')
    private final Predicate<String> pathFilter;

    private final EnumSet<Operation> supportedOps = EnumSet.noneOf(Operation.class);

    // Only expose write-affecting ops if the path is permitted. Keep other supported ops from delegate.
    @Override
    public Set<Operation> supportedOperations() {
        // Keep the delegate's declared ops; enforcement is done in the methods below
        return SetUtils.union(delegate().supportedOperations(), supportedOps);
    }

    // ---------- Helpers ----------

    private void checkPath(String path) throws NoSuchFileException {
        LOG.trace("Checking path: {}", path);
        assert path.charAt(0) == '/';
        if (!"/".equals(path) && !pathFilter.test(path.substring(1))) {
            throw new NoSuchFileException(path);
        }
    }

    // ---------- Filtering wrappers ----------

    // Start
    { supportedOps.add(INIT); }

    { supportedOps.add(STATFS); }
    @Override
    public Statvfs statfs(String path) throws IOException {
        checkPath(path);
        return delegate().statfs(path);
    }

    // Attributes
    { supportedOps.add(GET_ATTR); }
    @Override
    public FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        checkPath(path);
        return delegate().getattr(path, fi);
    }

    { supportedOps.add(GET_XATTR); }
    @Override
    public String getxattr(String path, String name) throws IOException {
        checkPath(path);
        return delegate().getxattr(path, name);
    }

    { supportedOps.add(SET_XATTR); }
    @Override
    public void setxattr(String path, String name, ByteBuffer value) throws IOException {
        checkPath(path);
        delegate().setxattr(path, name, value);
    }

    { supportedOps.add(LIST_XATTR); }
    @Override
    public List<String> listxattr(String path) throws IOException {
        checkPath(path);
        return delegate().listxattr(path);
    }

    { supportedOps.add(REMOVE_XATTR); }
    @Override
    public void removexattr(String path, String name) throws IOException {
        checkPath(path);
        delegate().removexattr(path, name);
    }

    { supportedOps.add(ACCESS); }
    @Override
    public void access(String path, int mask) throws IOException {
        checkPath(path);
        delegate().access(path, mask);
    }

    { supportedOps.add(CHMOD); }
    @Override
    public void chmod(String path, int mode, @Nullable FileInfo fi) throws IOException {
        checkPath(path);
        delegate().chmod(path, mode, fi);
    }

    { supportedOps.add(CHOWN); }
    @Override
    public void chown(String path, int uid, int gid, @Nullable FileInfo fi) throws IOException {
        checkPath(path);
        delegate().chown(path, uid, gid, fi);
    }

    { supportedOps.add(UTIMENS); }
    @Override
    public void utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) throws IOException {
        checkPath(path);
        delegate().utimens(path, atime, mtime, fi);
    }

    // Links
    { supportedOps.add(READLINK); }
    @Override
    public String readlink(String path) throws IOException {
        checkPath(path);
        return delegate().readlink(path);
    }

//    { supportedOps.add(SYMLINK); }
//    @Override
//    public void symlink(String target, String linkname) throws IOException {
//        if (!pathFilter.test(linkname)) throw new NoSuchFileException(linkname);
//        delegate().symlink(linkname, target);
//    }

    // Directories
    { supportedOps.add(MKDIR); }
    @Override
    public void mkdir(String path, int mode) throws IOException {
        checkPath(path);
        delegate().mkdir(path, mode);
    }

    { supportedOps.add(OPEN_DIR); }
    @Override
    public void opendir(String path, FileInfo fi) throws IOException {
        checkPath(path);
        delegate().opendir(path, fi);
    }

    { supportedOps.add(READ_DIR); }
    @Override
    public List<DirEntry> readdir(String path) throws IOException {
        checkPath(path);
        return delegate.readdir(path).stream()
                .filter(e -> pathFilter.test("/".equals(path)
                        ? e.name()
                        : path.substring(1) + "/" + e.name()
                ))
                .toList();
    }

    { supportedOps.add(RELEASE_DIR); }
    @Override
    public void releasedir(@Nullable String path, FileInfo fi) throws IOException {
        if (path != null) checkPath(path);
        delegate().releasedir(path, fi);
    }

    { supportedOps.add(RMDIR); }
    @Override
    public void rmdir(String path) throws IOException {
        checkPath(path);
        delegate().rmdir(path);
    }

    // Files
    { supportedOps.add(CREATE); }
    @Override
    public void create(String path, int mode, FileInfo fi) throws IOException {
        checkPath(path);
        delegate().create(path, mode, fi);
    }

    { supportedOps.add(OPEN); }
    @Override
    public void open(String path, FileInfo fi) throws IOException {
        checkPath(path);
        delegate().open(path, fi);
    }

    { supportedOps.add(READ); }
    @Override
    public int read(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        checkPath(path);
        return delegate().read(path, buf, count, offset, fi);
    }

    { supportedOps.add(WRITE); }
    @Override
    public int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        checkPath(path);
        return delegate().write(path, buf, count, offset, fi);
    }

    { supportedOps.add(UNLINK); }
    @Override
    public void unlink(String path) throws IOException {
        checkPath(path);
        delegate().unlink(path);
    }

    { supportedOps.add(TRUNCATE); }
    @Override
    public void truncate(String path, long size, @Nullable FileInfo fi) throws IOException {
        checkPath(path);
        delegate().truncate(path, size, fi);
    }

//    { supportedOps.add(RENAME); }
//    @Override
//    public void rename(String oldPath, String newPath, int flags) throws IOException {
//        // Deny renaming non-matching sources or into a non-matching destination
//        if (!pathFilter.test(oldPath) || !creationAllowedAt(newPath)) {
//            throw new NoSuchFileException(oldPath + " -> " + newPath);
//        }
//        delegate().rename(oldPath, newPath, flags);
//    }

    // Finish
    { supportedOps.add(DESTROY); }
    @Override
    public void destroy() {
        delegate().destroy();
    }

    { supportedOps.add(FLUSH); }
    @Override
    public void flush(String path, FileInfo fi) throws IOException {
        checkPath(path);
        delegate().flush(path, fi);
    }

    { supportedOps.add(FSYNC); }
    @Override
    public void fsync(String path, int datasync, FileInfo fi) throws IOException {
        checkPath(path);
        delegate().fsync(path, datasync, fi);
    }

    { supportedOps.add(FSYNCDIR); }
    @Override
    public void fsyncdir(@Nullable String path, int datasync, FileInfo fi) throws IOException {
        if (path != null) checkPath(path);
        delegate().fsyncdir(path, datasync, fi);
    }
}
