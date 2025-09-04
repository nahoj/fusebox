package eu.nahoj.fusebox.nio.driving;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.StatvfsData;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.function.Consumers;
import org.cryptomator.jfuse.api.DirFiller;
import org.cryptomator.jfuse.api.Errno;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.cryptomator.jfuse.api.FuseOperations;
import org.cryptomator.jfuse.api.Stat;
import org.cryptomator.jfuse.api.Statvfs;
import org.cryptomator.jfuse.api.TimeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static eu.nahoj.fusebox.common.ExceptionHandler.catchErrno;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.stripEnd;

@Accessors(fluent = true)
@RequiredArgsConstructor
public class FuseboxFSOperations implements FuseOperations {

    private static final Logger LOG = LoggerFactory.getLogger(FuseboxFSOperations.class);

    private final FuseboxFS delegate;

    @Getter
    private final Errno errno;

    public Set<FuseOperations.Operation> supportedOperations() {
        return delegate.supportedOperations();
    }

    /// All paths given to the FuseboxFS must be normalized
    ///
    /// This only removes any trailing slashes.
    ///
    /// We assume that given paths do not contain ".", "..", or repeated slashes.
    /// Source (for Linux): kernel code via AI: `handle_dots` function and "no more slashes" comment in
    /// [fs/namei.c](https://github.com/torvalds/linux/blob/master/fs/namei.c)
    ///
    private String normalizePath(String path) {
        // Trim trailing slashes but keep root as "/"
        return defaultIfEmpty(stripEnd(path, "/"), "/");
    }

    // Start
    public void init(FuseConnInfo conn, @Nullable FuseConfig cfg) {
        delegate.init(conn, cfg);
        LOG.debug("Initializing FuseboxFSOperations. Supported operations: {}", supportedOperations());
    }

    public int statfs(String path, Statvfs statvfs) {
        return catchErrno(errno, () -> {
            StatvfsData.copy(delegate.statfs(normalizePath(path)), statvfs);
            return 0;
        });
    }

    // Attributes
    public int getattr(String path, Stat stat, @Nullable FileInfo fi) {
        return catchErrno(errno, () -> {
            FileAttributes attr = delegate.getattr(normalizePath(path), fi);
            stat.aTime().set(attr.lastAccessTime());
            stat.cTime().set(attr.lastChangeTime());
            stat.mTime().set(attr.lastModifiedTime());
            if (attr.creationTime() != null) stat.birthTime().set(attr.creationTime());
            stat.setMode(attr.mode());
            stat.setUid(attr.uid());
            stat.setGid(attr.gid());
            // Should be 2 + subdir count for dirs, but it is probably not worth computing
            stat.setNLink((short) (attr.isDirectory() ? 2 : 1));
            stat.setSize(attr.size());
            return 0;
        });
    }

    public int getxattr(String path, String name, ByteBuffer value) {
        return catchErrno(errno, () -> {
            String s = delegate.getxattr(normalizePath(path), name);
            byte[] bytes = s.getBytes(UTF_8);
            int size = bytes.length;
            if (value.capacity() == 0) {
                return size;
            } else if (value.remaining() < size) {
                return -errno.erange();
            } else {
                value.put(bytes);
                return size;
            }
        });
    }

    public int setxattr(String path, String name, ByteBuffer value, int flags) {
        return catchErrno(errno, () -> {
            delegate.setxattr(normalizePath(path), name, value);
            return 0;
        });
    }

    public int listxattr(String path, ByteBuffer list) {
        return catchErrno(errno, () -> {
            List<String> names = delegate.listxattr(normalizePath(path));

            // Copied from https://github.com/cryptomator/jfuse/blob/develop/jfuse-examples AbstractMirrorFileSystem
            try {
                if (list.capacity() == 0) {
                    int contentBytes = names.stream()
                            .map(StandardCharsets.UTF_8::encode)
                            .mapToInt(ByteBuffer::remaining)
                            .sum();
                    int nulBytes = names.size();
                    return contentBytes + nulBytes; // attr1\0aattr2\0attr3\0
                } else {
                    int startpos = list.position();
                    for (String name : names) {
                        list.put(StandardCharsets.UTF_8.encode(name)).put((byte) 0x00);
                    }
                    return list.position() - startpos;
                }
            } catch (BufferOverflowException e) {
                return -errno.erange();
            }
        });
    }

    public int removexattr(String path, String name) {
        return catchErrno(errno, () -> {
            delegate.removexattr(normalizePath(path), name);
            return 0;
        });
    }

    public int access(String path, int mask) {
        return catchErrno(errno, () -> {
            delegate.access(normalizePath(path), mask);
            return 0;
        });
    }

    public int chmod(String path, int mode, @Nullable FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.chmod(normalizePath(path), mode, fi);
            return 0;
        });
    }

    public int chown(String path, int uid, int gid, @Nullable FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.chown(normalizePath(path), uid, gid, fi);
            return 0;
        });
    }

    public int utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.utimens(normalizePath(path), atime, mtime, fi);
            return 0;
        });
    }

    // Links
    public int readlink(String path, ByteBuffer buf, long len) {
        return catchErrno(errno, () -> {
            String target = delegate.readlink(normalizePath(path));
            byte[] bytes = target.getBytes(StandardCharsets.UTF_8);
            int cap = (int) Math.min(len, buf.remaining());
            int n = cap > 0 ? Math.min(bytes.length, cap - 1) : 0; // reserve NUL if possible
            if (n > 0) buf.put(bytes, 0, n);
            if (cap - n > 0) buf.put((byte) 0);
            return 0;
        });
    }

    // jfuse passes (target, linkname) (contrary to FuseOperations parameter naming)
    public int symlink(String target, String linkname) {
        return catchErrno(errno, () -> {
            delegate.symlink(target, normalizePath(linkname));
            return 0;
        });
    }

    // Directories
    public int mkdir(String path, int mode) {
        return catchErrno(errno, () -> {
            delegate.mkdir(normalizePath(path), mode);
            return 0;
        });
    }

    public int opendir(String path, FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.opendir(normalizePath(path), fi);
            return 0;
        });
    }

    public int readdir(String path, DirFiller filler, long offset, FileInfo fi, int flags) {
        LOG.trace("readdir({})", path);
        return catchErrno(errno, () -> {
            int rc = filler.fill(".", Consumers.nop(), 0, 0);
            if (rc != 0) return -errno.eio();

            rc = filler.fill("..", Consumers.nop(), 0, 0);
            if (rc != 0) return -errno.eio();

            for (DirEntry e : delegate.readdir(normalizePath(path))) {
                rc = filler.fill(e.name(), Consumers.nop(), 0, 0);
                if (rc != 0) return -errno.eio();
            }
            return 0;
        });
    }

    public int releasedir(@Nullable String path, FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.releasedir(path == null ? null : normalizePath(path), fi);
            return 0;
        });
    }

    public int rmdir(String path) {
        return catchErrno(errno, () -> {
            delegate.rmdir(normalizePath(path));
            return 0;
        });
    }

    // Files
    public int create(String path, int mode, FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.create(normalizePath(path), mode, fi);
            return 0;
        });
    }

    public int open(String path, FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.open(normalizePath(path), fi);
            return 0;
        });
    }

    public int read(String path, ByteBuffer buf, long count, long offset, FileInfo fi) {
        return catchErrno(errno, () ->
                delegate.read(normalizePath(path), buf, count, offset, fi)
        );
    }

    public int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) {
        return catchErrno(errno, () ->
                delegate.write(normalizePath(path), buf, count, offset, fi)
        );
    }

    public int truncate(String path, long size, @Nullable FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.truncate(normalizePath(path), size, fi);
            return 0;
        });
    }

    public int release(String path, FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.release(normalizePath(path), fi);
            return 0;
        });
    }

    public int unlink(String path) {
        return catchErrno(errno, () -> {
            delegate.unlink(normalizePath(path));
            return 0;
        });
    }

    public int rename(String oldpath, String newpath, int flags) {
        return catchErrno(errno, () -> {
            delegate.rename(normalizePath(oldpath), normalizePath(newpath), flags);
            return 0;
        });
    }

    // Finish
    public void destroy() {
        delegate.destroy();
    }

    public int flush(String path, FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.flush(normalizePath(path), fi);
            return 0;
        });
    }

    public int fsync(String path, int datasync, FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.fsync(normalizePath(path), datasync, fi);
            return 0;
        });
    }

    public int fsyncdir(@Nullable String path, int datasync, FileInfo fi) {
        return catchErrno(errno, () -> {
            delegate.fsyncdir(path == null ? null : normalizePath(path), datasync, fi);
            return 0;
        });
    }
}
