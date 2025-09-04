package eu.nahoj.fusebox.vfs2.driving;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.StatvfsData;
import eu.nahoj.fusebox.vfs2.api.FuseboxContent;
import eu.nahoj.fusebox.vfs2.api.FuseboxFS;
import eu.nahoj.fusebox.vfs2.api.FuseboxFile;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static eu.nahoj.fusebox.common.ExceptionHandler.catchErrno;

@Accessors(fluent = true)
@RequiredArgsConstructor
public class FuseboxFSOperations implements FuseOperations {

    private static final Logger LOG = LoggerFactory.getLogger(FuseboxFSOperations.class);

    private final FuseboxFS delegate;
    private final Errno errno;

    // Manage open readable handles per file handle ID
    private final Map<Long, FuseboxContent> handles = new ConcurrentHashMap<>();
    private final AtomicLong nextFh = new AtomicLong(1);

    @Override
    public Errno errno() { return errno; }

    /// All paths given to the FuseboxFS must be normalized
    ///
    /// This consists in removing the leading slash and the optional trailing slash.
    ///
    /// We assume that given paths do not contain ".", "..", or repeated slashes.
    /// Source (via AI): Linux kernel code: `handle_dots` function and "no more slashes" comment in
    /// [fs/namei.c](https://github.com/torvalds/linux/blob/master/fs/namei.c)
    ///
    private static String normalizePath(String path) {
        int length = path.length();
        return path.substring(1, length > 1 && path.endsWith("/") ? length - 1 : length);
    }

    @Override
    public Set<Operation> supportedOperations() {
        return delegate.supportedOperations();
    }

    // Lifecycle
    public void init(FuseConnInfo conn, @Nullable FuseConfig cfg) {
        LOG.trace("init(conn={}, cfg={})", conn, cfg);
        LOG.debug("Initializing VFS2 FuseboxFSOperations. Supported operations: {}", supportedOperations());
    }

    public int statfs(String path, Statvfs out) {
        LOG.trace("statfs(path={})", path);
        return catchErrno(errno, () -> {
            StatvfsData.copy(delegate.getStats(normalizePath(path)), out);
            LOG.trace("statfs(path={}) -> 0", path);
            return 0;
        });
    }

    // Attributes
    public int getattr(String path, Stat stat, @Nullable FileInfo fi) {
        LOG.trace("getattr(path={}, fh={})", path, fi != null ? fi.getFh() : null);
        return catchErrno(errno, () -> {
            FuseboxFile f = delegate.resolveFile(normalizePath(path));
            FileAttributes attr = f.getAttributes();
            stat.setMode(attr.mode());
            stat.setUid(attr.uid());
            stat.setGid(attr.gid());
            stat.aTime().set(attr.lastAccessTime());
            stat.cTime().set(attr.lastChangeTime());
            stat.mTime().set(attr.lastModifiedTime());
            if (attr.creationTime() != null) stat.birthTime().set(attr.creationTime());
            stat.setNLink((short) (attr.isDirectory() ? 2 : 1));
            stat.setSize(attr.size());
            LOG.trace("getattr(path={}) -> mode={}, size={}, dir={}",
                    path, Integer.toOctalString(attr.mode()), attr.size(), attr.isDirectory());
            return 0;
        });
    }

    // Links
    public int readlink(String path, ByteBuffer buf, long len) { return -errno.enosys(); }
    public int symlink(String target, String linkname) { return -errno.erofs(); }

    // Directories
    public int opendir(String path, FileInfo fi) {
        LOG.trace("opendir(path={})", path);
        return catchErrno(errno, () -> {
            // Ensure the directory exists
            delegate.resolveFile(normalizePath(path)).existsAndIsDirectory();
            LOG.trace("opendir(path={}) -> 0", path);
            return 0;
        });
    }

    public int readdir(String path, DirFiller filler, long offset, FileInfo fi, int flags) {
        LOG.trace("readdir(path={}, offset={}, flags={})", path, offset, flags);
        return catchErrno(errno, () -> {
            int rc = filler.fill(".", Consumers.nop(), 0, 0);
            if (rc != 0) return -errno.eio();
            rc = filler.fill("..", Consumers.nop(), 0, 0);
            if (rc != 0) return -errno.eio();

            for (DirEntry child : delegate.resolveFile(normalizePath(path)).getEntries()) {
                rc = filler.fill(child.name(), Consumers.nop(), 0, 0);
                if (rc != 0) return -errno.eio();
            }
            return 0;
        });
    }

    public int releasedir(@Nullable String path, FileInfo fi) {
        LOG.trace("releasedir(path={}, fh={})", path, fi.getFh());
        return 0; // no-op
    }

    public int rmdir(String path) { return -errno.erofs(); }

    // Files
    public int open(String path, FileInfo fi) {
        LOG.trace("open(path={}, flags={})", path, fi.getFlags());
        return catchErrno(errno, () -> {
            FuseboxFile f = delegate.resolveFile(normalizePath(path));
            FuseboxContent r = f.openReadable();
            long fh = nextFh.getAndIncrement();
            handles.put(fh, r);
            fi.setFh(fh);
            LOG.trace("open(path={}) -> fh={}", path, fh);
            return 0;
        });
    }

    public int read(String path, ByteBuffer buf, long count, long offset, FileInfo fi) {
        LOG.trace("read(path={}, fh={}, count={}, offset={}, bufRemaining={})", path, fi.getFh(), count, offset, buf.remaining());
        return catchErrno(errno, () -> {
            FuseboxContent r = handles.get(fi.getFh());
            if (r == null) return -errno.ebadf();
            int cap = (int) Math.min(count, buf.remaining());
            ByteBuffer dst;
            if (cap == buf.remaining()) {
                dst = buf;
            } else {
                dst = buf.duplicate();
                dst.limit(dst.position() + cap);
            }
            // Read
            int n = r.withByteChannelUncheckedIO(channel -> channel.readAt(dst, offset));
            if (dst != buf) buf.position(buf.position() + Math.max(0, n));
            LOG.trace("read(path={}, fh={}) -> {} bytes (requested {})",
                    path, fi.getFh(), n, Math.min(count, buf.remaining()));
            return n;
        });
    }

    public int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) {
        return -errno.erofs();
    }

    public int truncate(String path, long size, @Nullable FileInfo fi) { return -errno.erofs(); }

    public int release(String path, FileInfo fi) {
        LOG.trace("release(path={}, fh={})", path, fi.getFh());
        return catchErrno(errno, () -> {
            FuseboxContent r = handles.remove(fi.getFh());
            if (r != null) r.close();
            LOG.trace("release(path={}, fh={}) -> 0", path, fi.getFh());
            return 0;
        });
    }

    public int unlink(String path) { return -errno.erofs(); }

    public int rename(String oldpath, String newpath, int flags) { return -errno.erofs(); }


    // Finish
    public void destroy() {
        LOG.trace("destroy() - closing {} leaked handles if any", handles.size());
        // close any leaked handles just in case
        handles.values().forEach(h -> {
            try { h.close(); } catch (Exception ignore) {}
        });
        handles.clear();
    }

    public int flush(String path, FileInfo fi) { return 0; }
    public int fsync(String path, int datasync, FileInfo fi) { return 0; }
    public int fsyncdir(@Nullable String path, int datasync, FileInfo fi) { return 0; }
}
