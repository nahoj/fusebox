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
public interface SelectivelyDecoratedFS extends ChainingFS {

    /// Get the to-be-decorated FuseboxFS object.
    ///
    /// @return the undecorated operations
    FuseboxFS delegate();

    default boolean shouldDecorate(String path) {
        return true;
    }

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
        return shouldDecorate(path) ? decoratedStatfs(path) : delegate().statfs(path);
    }

    default Statvfs decoratedStatfs(String path) throws IOException {
        return delegate().statfs(path);
    }

    // Attributes
    @Override
    default FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        return shouldDecorate(path) ? decoratedGetattr(path, fi) : delegate().getattr(path, fi);
    }

    default FileAttributes decoratedGetattr(String path, @Nullable FileInfo fi) throws IOException {
        return delegate().getattr(path, fi);
    }

    @Override
    default String getxattr(String path, String name) throws IOException {
        return shouldDecorate(path) ? decoratedGetxattr(path, name) : delegate().getxattr(path, name);
    }

    default String decoratedGetxattr(String path, String name) throws IOException {
        return delegate().getxattr(path, name);
    }

    @Override
    default void setxattr(String path, String name, ByteBuffer value) throws IOException {
        if (shouldDecorate(path)) {
            decoratedSetxattr(path, name, value);
        } else {
            delegate().setxattr(path, name, value);
        }
    }

    default void decoratedSetxattr(String path, String name, ByteBuffer value) throws IOException {
        delegate().setxattr(path, name, value);
    }

    @Override
    default List<String> listxattr(String path) throws IOException {
        return shouldDecorate(path) ? decoratedListxattr(path) : delegate().listxattr(path);
    }

    default List<String> decoratedListxattr(String path) throws IOException {
        return delegate().listxattr(path);
    }

    @Override
    default void removexattr(String path, String name) throws IOException {
        if (shouldDecorate(path)) decoratedRemovexattr(path, name); else delegate().removexattr(path, name);
    }

    default void decoratedRemovexattr(String path, String name) throws IOException {
        delegate().removexattr(path, name);
    }

    @Override
    default void access(String path, int mask) throws IOException {
        if (shouldDecorate(path)) decoratedAccess(path, mask); else delegate().access(path, mask);
    }

    default void decoratedAccess(String path, int mask) throws IOException {
        delegate().access(path, mask);
    }

    @Override
    default void chmod(String path, int mode, @Nullable FileInfo fi) throws IOException {
        if (shouldDecorate(path)) decoratedChmod(path, mode, fi); else delegate().chmod(path, mode, fi);
    }

    default void decoratedChmod(String path, int mode, @Nullable FileInfo fi) throws IOException {
        delegate().chmod(path, mode, fi);
    }

    @Override
    default void chown(String path, int uid, int gid, @Nullable FileInfo fi) throws IOException {
        if (shouldDecorate(path)) decoratedChown(path, uid, gid, fi); else delegate().chown(path, uid, gid, fi);
    }

    default void decoratedChown(String path, int uid, int gid, @Nullable FileInfo fi) throws IOException {
        delegate().chown(path, uid, gid, fi);
    }

    @Override
    default void utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) throws IOException {
        if (shouldDecorate(path)) {
            decoratedUtimens(path, atime, mtime, fi);
        } else {
            delegate().utimens(path, atime, mtime, fi);
        }
    }

    default void decoratedUtimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi)
            throws IOException {
        delegate().utimens(path, atime, mtime, fi);
    }

    // Links
    @Override
    default String readlink(String path) throws IOException {
        return shouldDecorate(path) ? decoratedReadlink(path) : delegate().readlink(path);
    }

    default String decoratedReadlink(String path) throws IOException {
        return delegate().readlink(path);
    }

    /// - `target` is not normalized
    @Override
    default void symlink(String target, String linkname) throws IOException {
        if (shouldDecorate(linkname)) decoratedSymlink(target, linkname); else delegate().symlink(target, linkname);
    }

    default void decoratedSymlink(String target, String linkname) throws IOException {
        delegate().symlink(target, linkname);
    }

    // Directories
    @Override
    default void mkdir(String path, int mode) throws IOException {
        if (shouldDecorate(path)) decoratedMkdir(path, mode); else delegate().mkdir(path, mode);
    }

    default void decoratedMkdir(String path, int mode) throws IOException {
        delegate().mkdir(path, mode);
    }

    @Override
    default void opendir(String path, FileInfo fi) throws IOException {
        if (shouldDecorate(path)) decoratedOpendir(path, fi); else delegate().opendir(path, fi);
    }

    default void decoratedOpendir(String path, FileInfo fi) throws IOException {
        delegate().opendir(path, fi);
    }

    @Override
    default List<DirEntry> readdir(String path) throws IOException {
        return shouldDecorate(path) ? decoratedReaddir(path) : delegate().readdir(path);
    }

    default List<DirEntry> decoratedReaddir(String path) throws IOException {
        return delegate().readdir(path);
    }

    @Override
    default void releasedir(@Nullable String path, FileInfo fi) throws IOException {
        if (path == null || shouldDecorate(path)) decoratedReleasedir(path, fi); else delegate().releasedir(path, fi);
    }

    default void decoratedReleasedir(@Nullable String path, FileInfo fi) throws IOException {
        delegate().releasedir(path, fi);
    }

    @Override
    default void rmdir(String path) throws IOException {
        if (shouldDecorate(path)) decoratedRmdir(path); else delegate().rmdir(path);
    }

    default void decoratedRmdir(String path) throws IOException {
        delegate().rmdir(path);
    }

    // Files
    @Override
    default void create(String path, int mode, FileInfo fi) throws IOException {
        if (shouldDecorate(path)) decoratedCreate(path, mode, fi); else delegate().create(path, mode, fi);
    }

    default void decoratedCreate(String path, int mode, FileInfo fi) throws IOException {
        delegate().create(path, mode, fi);
    }

    @Override
    default void open(String path, FileInfo fi) throws IOException {
        if (shouldDecorate(path)) decoratedOpen(path, fi); else delegate().open(path, fi);
    }

    default void decoratedOpen(String path, FileInfo fi) throws IOException {
        delegate().open(path, fi);
    }

    @Override
    default int read(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        return shouldDecorate(path)
                ? decoratedRead(path, buf, count, offset, fi)
                : delegate().read(path, buf, count, offset, fi);
    }

    default int decoratedRead(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        return delegate().read(path, buf, count, offset, fi);
    }

    @Override
    default int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        return shouldDecorate(path)
                ? decoratedWrite(path, buf, count, offset, fi)
                : delegate().write(path, buf, count, offset, fi);
    }

    default int decoratedWrite(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        return delegate().write(path, buf, count, offset, fi);
    }

    @Override
    default void truncate(String path, long size, @Nullable FileInfo fi) throws IOException {
        if (shouldDecorate(path)) decoratedTruncate(path, size, fi); else delegate().truncate(path, size, fi);
    }

    default void decoratedTruncate(String path, long size, @Nullable FileInfo fi) throws IOException {
        delegate().truncate(path, size, fi);
    }

    @Override
    default void release(String path, FileInfo fi) throws IOException {
        if (shouldDecorate(path)) decoratedRelease(path, fi); else delegate().release(path, fi);
    }

    default void decoratedRelease(String path, FileInfo fi) throws IOException {
        delegate().release(path, fi);
    }

    @Override
    default void unlink(String path) throws IOException {
        if (shouldDecorate(path)) decoratedUnlink(path); else delegate().unlink(path);
    }

    default void decoratedUnlink(String path) throws IOException {
        delegate().unlink(path);
    }

    @Override
    default void rename(String oldPath, String newPath, int flags) throws IOException {
        if (shouldDecorate(oldPath) || shouldDecorate(newPath)) {
            decoratedRename(oldPath, newPath, flags);
        } else {
            delegate().rename(oldPath, newPath, flags);
        }
    }

    default void decoratedRename(String oldPath, String newPath, int flags) throws IOException {
        delegate().rename(oldPath, newPath, flags);
    }

    // Finish
    @Override
    default void destroy() {
        delegate().destroy();
    }

    @Override
    default void flush(String path, FileInfo fi) throws IOException {
        if (shouldDecorate(path)) decoratedFlush(path, fi); else delegate().flush(path, fi);
    }

    default void decoratedFlush(String path, FileInfo fi) throws IOException {
        delegate().flush(path, fi);
    }

    @Override
    default void fsync(String path, int datasync, FileInfo fi) throws IOException {
        if (shouldDecorate(path)) decoratedFsync(path, datasync, fi); else delegate().fsync(path, datasync, fi);
    }

    default void decoratedFsync(String path, int datasync, FileInfo fi) throws IOException {
        delegate().fsync(path, datasync, fi);
    }

    @Override
    default void fsyncdir(@Nullable String path, int datasync, FileInfo fi) throws IOException {
        if (path == null || shouldDecorate(path)) {
            decoratedFsyncdir(path, datasync, fi);
        } else {
            delegate().fsyncdir(path, datasync, fi);
        }
    }

    default void decoratedFsyncdir(@Nullable String path, int datasync, FileInfo fi) throws IOException {
        delegate().fsyncdir(path, datasync, fi);
    }
}
