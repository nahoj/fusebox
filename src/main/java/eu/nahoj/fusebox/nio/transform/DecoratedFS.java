package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.cryptomator.jfuse.api.FuseOperations;
import org.cryptomator.jfuse.api.Statvfs;
import org.cryptomator.jfuse.api.TimeSpec;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

/// Convenience interface to make decorating [FuseboxFS] easier.
public interface DecoratedFS extends ChainingFS {

    FuseboxFS delegate();

    @Override
    default Set<FuseOperations.Operation> supportedOperations() {
        return delegate().supportedOperations();
    }

    // Start
    @Override
    default void init(FuseConnInfo conn, @Nullable FuseConfig cfg) {
        delegate().init(conn, cfg);
    }

    @Override
    default Statvfs statfs(String path) throws IOException {
        return delegate().statfs(path);
    }

    // Attributes
    @Override
    default FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        return delegate().getattr(path, fi);
    }

    @Override
    default String getxattr(String path, String name) throws IOException {
        return delegate().getxattr(path, name);
    }

    @Override
    default void setxattr(String path, String name, ByteBuffer value) throws IOException {
        delegate().setxattr(path, name, value);
    }

    @Override
    default List<String> listxattr(String path) throws IOException {
        return delegate().listxattr(path);
    }

    @Override
    default void removexattr(String path, String name) throws IOException {
        delegate().removexattr(path, name);
    }

    @Override
    default void access(String path, int mask) throws IOException {
        delegate().access(path, mask);
    }

    @Override
    default void chmod(String path, int mode, @Nullable FileInfo fi) throws IOException {
        delegate().chmod(path, mode, fi);
    }

    @Override
    default void chown(String path, int uid, int gid, @Nullable FileInfo fi) throws IOException {
        delegate().chown(path, uid, gid, fi);
    }

    @Override
    default void utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) throws IOException {
        delegate().utimens(path, atime, mtime, fi);
    }

    // Links
    @Override
    default String readlink(String path) throws IOException {
        return delegate().readlink(path);
    }

    /// - `target` is not normalized
    @Override
    default void symlink(String target, String linkname) throws IOException {
        delegate().symlink(target, linkname);
    }

    // Directories
    @Override
    default void mkdir(String path, int mode) throws IOException {
        delegate().mkdir(path, mode);
    }

    @Override
    default void opendir(String path, FileInfo fi) throws IOException {
        delegate().opendir(path, fi);
    }

    @Override
    default List<DirEntry> readdir(String path) throws IOException {
        return delegate().readdir(path);
    }

    @Override
    default void releasedir(@Nullable String path, FileInfo fi) throws IOException {
        delegate().releasedir(path, fi);
    }

    @Override
    default void rmdir(String path) throws IOException {
        delegate().rmdir(path);
    }

    // Files
    @Override
    default void create(String path, int mode, FileInfo fi) throws IOException {
        delegate().create(path, mode, fi);
    }

    @Override
    default void open(String path, FileInfo fi) throws IOException {
        delegate().open(path, fi);
    }

    @Override
    default int read(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        return delegate().read(path, buf, count, offset, fi);
    }

    @Override
    default void truncate(String path, long size, @Nullable FileInfo fi) throws IOException {
        delegate().truncate(path, size, fi);
    }

    @Override
    default void release(String path, FileInfo fi) throws IOException {
        delegate().release(path, fi);
    }

    @Override
    default void unlink(String path) throws IOException {
        delegate().unlink(path);
    }

    @Override
    default void rename(String oldPath, String newPath, int flags) throws IOException {
        delegate().rename(oldPath, newPath, flags);
    }

    // Finish
    @Override
    default void destroy() {
        delegate().destroy();
    }

    @Override
    default void flush(String path, FileInfo fi) throws IOException {
        delegate().flush(path, fi);
    }

    @Override
    default void fsync(String path, int datasync, FileInfo fi) throws IOException {
        delegate().fsync(path, datasync, fi);
    }

    @Override
    default void fsyncdir(@Nullable String path, int datasync, FileInfo fi) throws IOException {
        delegate().fsyncdir(path, datasync, fi);
    }
}
