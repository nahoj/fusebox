package eu.nahoj.fusebox.nio.api;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.cryptomator.jfuse.api.FuseOperations;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.Statvfs;
import org.cryptomator.jfuse.api.TimeSpec;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Set;

/// Like [FuseOperations], but methods that would normally return an errno
/// instead throw [IOException]. Defaults throw [UnsupportedOperationException].
public interface FuseboxFS {

    Set<Operation> supportedOperations();

    // Start
    default void init(FuseConnInfo conn, @Nullable FuseConfig cfg) {
        // no-op
    }

    default Statvfs statfs(String path) throws IOException {
        throw new UnsupportedOperationException("statfs");
    }

    // Attributes
    default FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("getattr");
    }

    default String getxattr(String path, String name) throws IOException {
        throw new UnsupportedOperationException("getxattr");
    }

    default void setxattr(String path, String name, ByteBuffer value) throws IOException {
        throw new UnsupportedOperationException("setxattr");
    }

    default List<String> listxattr(String path) throws IOException {
        throw new UnsupportedOperationException("listxattr");
    }

    default void removexattr(String path, String name) throws IOException {
        throw new UnsupportedOperationException("removexattr");
    }

    default void access(String path, int mask) throws IOException {
        throw new UnsupportedOperationException("access");
    }

    default void chmod(String path, int mode, @Nullable FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("chmod");
    }

    default void chown(String path, int uid, int gid, @Nullable FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("chown");
    }

    default void utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("utimens");
    }

    // Links
    default String readlink(String path) throws IOException {
        throw new UnsupportedOperationException("readlink");
    }

    default void symlink(String target, String linkname) throws IOException {
        throw new UnsupportedOperationException("symlink");
    }

    // Directories
    default void mkdir(String path, int mode) throws IOException {
        throw new UnsupportedOperationException("mkdir");
    }

    default void opendir(String path, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("opendir");
    }

    /// List a directory's children. The returned entries must not include '.' or '..'.
    default List<DirEntry> readdir(String path) throws IOException {
        throw new UnsupportedOperationException("readdir");
    }

    default void releasedir(@Nullable String path, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("releasedir");
    }

    default void rmdir(String path) throws IOException {
        throw new UnsupportedOperationException("rmdir");
    }

    // Files
    default void create(String path, int mode, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("create");
    }

    default void open(String path, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("open");
    }

    default int read(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("read");
    }

    default int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("write");
    }

    default void truncate(String path, long size, @Nullable FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("truncate");
    }

    default void release(String path, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("release");
    }

    default void unlink(String path) throws IOException {
        throw new UnsupportedOperationException("unlink");
    }

    // This is never called with paths outside the filesystem
    default void rename(String oldPath, String newPath, int flags) throws IOException {
        throw new UnsupportedOperationException("rename");
    }

    // Finish
    default void destroy() {
        // no-op
    }

    default void flush(String path, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("flush");
    }

    default void fsync(String path, int datasync, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("fsync");
    }

    default void fsyncdir(@Nullable String path, int datasync, FileInfo fi) throws IOException {
        throw new UnsupportedOperationException("fsyncdir");
    }
}
