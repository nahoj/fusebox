package eu.nahoj.fusebox.nio.driven;

import eu.nahoj.fusebox.common.api.BadFileDescriptorException;
import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.FileType;
import eu.nahoj.fusebox.common.api.IsDirectoryException;
import eu.nahoj.fusebox.common.api.StatvfsData;
import eu.nahoj.fusebox.nio.transform.ChainingFS;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FileModes;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.Statvfs;
import org.cryptomator.jfuse.api.TimeSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static eu.nahoj.fusebox.common.util.ExceptionUtils.uncheckedIO;
import static eu.nahoj.fusebox.common.util.NullUtils.mapOrNull;
import static eu.nahoj.fusebox.common.util.SetUtils.enumSetCopy;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Objects.requireNonNullElseGet;

@Slf4j
public class LocalFS implements ChainingFS {

    private static final Logger LOG = LoggerFactory.getLogger(LocalFS.class);

    private final Path root;

    private record OpenHandle(Path path, FileChannel fc) {
    }

    private final ConcurrentMap<Long, OpenHandle> openFiles = new ConcurrentHashMap<>();
    private final AtomicLong fileHandleGen = new AtomicLong(1L);

    public LocalFS(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public static LocalFS at(String rootPath) {
        return new LocalFS(Path.of(rootPath));
    }

    @Override
    public Set<Operation> supportedOperations() {
        return EnumSet.of(
                Operation.ACCESS,
                Operation.CHMOD,
                Operation.CHOWN,
                Operation.CREATE,
                Operation.DESTROY,
                Operation.FLUSH,
                Operation.FSYNC,
                Operation.FSYNCDIR,
                Operation.GET_ATTR,
                Operation.GET_XATTR,
                Operation.INIT,
                Operation.LIST_XATTR,
                Operation.MKDIR,
                Operation.OPEN_DIR,
                Operation.READ_DIR,
                Operation.RELEASE_DIR,
                Operation.RENAME,
                Operation.RMDIR,
                Operation.OPEN,
                Operation.READ,
                Operation.READLINK,
                Operation.RELEASE,
                Operation.REMOVE_XATTR,
                Operation.SET_XATTR,
                Operation.STATFS,
                Operation.SYMLINK,
                Operation.TRUNCATE,
                Operation.UNLINK,
                Operation.UTIMENS,
                Operation.WRITE
        );
    }

    private Path resolve(String fusePath) {
        return resolve(fusePath, null);
    }

    private Path resolve(String fusePath, @Nullable FileInfo fi) {
        return Optional.ofNullable(fi)
                .map(fi_ -> openFiles.get(fi_.getFh()).path())
                .orElseGet(uncheckedIO(() -> root.resolve(StringUtils.stripStart(fusePath, "/"))));
    }

    // Start
    @Override
    public void init(FuseConnInfo conn, FuseConfig cfg) {
        conn.setWant(conn.want() | (conn.capable() & FuseConnInfo.FUSE_CAP_BIG_WRITES));
        conn.setMaxBackground(16);
        conn.setCongestionThreshold(4);
    }

    public Statvfs statfs(String path) throws IOException {
        LOG.trace("statfs");
        Path p = resolve(path);
        FileStore store = Files.getFileStore(p);
        long bsize = store.getBlockSize();
        return StatvfsData.builder()
                .bsize(bsize)
                .frsize(bsize)
                .blocks(store.getTotalSpace() / bsize)
                .bfree(store.getUnallocatedSpace() / bsize)
                .bavail(store.getUsableSpace() / bsize)
                .nameMax(255L)
                .build();
    }

    // Attributes
    private static final String ATTRIBUTE_KEYS = "unix:mode,uid,gid,size," +
            "creationTime,lastAccessTime,lastModifiedTime,ctime";
    public FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        LOG.trace("getattr {}", path);
        Path p = resolve(path, fi);
        Map<String, Object> attrs = Files.readAttributes(p, ATTRIBUTE_KEYS, NOFOLLOW_LINKS); // Throws enotsup
        int mode = (Integer) attrs.get("mode");
        return FileAttributes.builder()
                .type(FileType.fromMode(mode))
                .suid((mode & 04000) != 0)
                .sgid((mode & 02000) != 0)
                .sticky((mode & 01000) != 0)
                .permissions(FileModes.toPermissions(mode))
                .uid((Integer) attrs.get("uid"))
                .gid((Integer) attrs.get("gid"))
                .size((Long) attrs.get("size"))
                .creationTime(mapOrNull((FileTime) attrs.get("creationTime"), FileTime::toInstant))
                .lastAccessTime(((FileTime) attrs.get("lastAccessTime")).toInstant())
                .lastModifiedTime(((FileTime) attrs.get("lastModifiedTime")).toInstant())
                .lastChangeTime(((FileTime) attrs.get("ctime")).toInstant())
                .build();
    }

    public String getxattr(String path, String name) throws IOException {
        LOG.trace("getxattr {} {}", path, name);
        val view = getXattrViewOrThrow(path);
        int size = view.size(name);
        ByteBuffer buf = ByteBuffer.allocate(size);
        view.read(name, buf);
        buf.flip();
        byte[] bytes = new byte[buf.remaining()]; // should use buf.array() ?
        buf.get(bytes);
        return new String(bytes, UTF_8);
    }

    public void setxattr(String path, String name, ByteBuffer value) throws IOException {
        LOG.trace("setxattr {} {}", path, name);
        getXattrViewOrThrow(path).write(name, value);
    }

    public List<String> listxattr(String path) throws IOException {
        LOG.trace("listxattr {}", path);
        return getXattrViewOrThrow(path).list();
    }

    public void removexattr(String path, String name) throws IOException {
        LOG.trace("removexattr {} {}", path, name);
        getXattrViewOrThrow(path).delete(name);
    }

    public void access(String path, int mask) throws IOException {
        LOG.trace("access {}", path);
        Path p = resolve(path);
        if ((mask & 0x01) == 0x01 && !Files.isExecutable(p) ||
                (mask & 0x02) == 0x02 && !Files.isWritable(p) ||
                (mask & 0x04) == 0x04 && !Files.isReadable(p)
        ) {
            throw new AccessDeniedException(path);
        }
    }

    public void chmod(String path, int mode, @Nullable FileInfo fi) throws IOException {
        LOG.trace("chmod {}", path);
        Path p = resolve(path, fi);
        Files.setPosixFilePermissions(p, FileModes.toPermissions(mode)); // Throws enotsup
    }

    public void chown(String path, int uid, int gid, @Nullable FileInfo fi) throws IOException {
        LOG.trace("chown {} uid={} gid={}", path, uid, gid);
        Path p = resolve(path, fi);
        Files.setAttribute(p, "unix:uid", uid, NOFOLLOW_LINKS); // Throws enotsup
        Files.setAttribute(p, "unix:gid", gid, NOFOLLOW_LINKS); // Throws enotsup
    }

    public void utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) throws IOException {
        LOG.trace("utimens {}", path);
        val view = getAttrViewOrThrow(path, BasicFileAttributeView.class);
        FileTime lastModified = mtime.getOptional().map(FileTime::from).orElse(null);
        FileTime lastAccess = atime.getOptional().map(FileTime::from).orElse(null);
        view.setTimes(lastModified, lastAccess, null);
    }

    private UserDefinedFileAttributeView getXattrViewOrThrow(String path) {
        return getAttrViewOrThrow(path, UserDefinedFileAttributeView.class);
    }

    private <V extends FileAttributeView> V getAttrViewOrThrow(String path, Class<V> viewClass) {
        Path p = resolve(path);
        return requireNonNullElseGet(
                Files.getFileAttributeView(p, viewClass, NOFOLLOW_LINKS),
                () -> { throw new UnsupportedOperationException(); }
        );
    }

    // Links
    public String readlink(String path) throws IOException {
        LOG.trace("readlink {}", path);
        Path p = resolve(path);
        return Files.readSymbolicLink(p).toString(); // Throws enotsup
    }

    public void symlink(String target, String linkname) throws IOException {
        LOG.trace("symlink {} -> {}", linkname, target);
        Path link = resolve(linkname);
        LOG.debug("creating symlink at {} target {}", link, target);
        Files.createSymbolicLink(link, Path.of(target)); // Throws enotsup
    }

    // Directories
    public void mkdir(String path, int mode) throws IOException {
        LOG.trace("mkdir {}", path);
        Path dir = resolve(path);
        if (Files.exists(dir, NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(path);
        Files.createDirectory(dir);
        Files.setPosixFilePermissions(dir, FileModes.toPermissions(mode)); // Throws enotsup
    }

    public void opendir(String path, FileInfo fi) throws IOException {
        LOG.trace("opendir {}", path);
        Path p = resolve(path);
        if (!Files.isDirectory(p, NOFOLLOW_LINKS)) throw new NotDirectoryException(path);
    }

    public List<DirEntry> readdir(String path) throws IOException {
        LOG.trace("readdir {}", path);
        Path dir = resolve(path);
        try (var s = Files.list(dir)) {
            return s.map(p -> new DirEntry(p.getFileName().toString())).toList();
        }
    }

    public void releasedir(String path, @Nullable FileInfo fi) {
        // no-op
    }

    public void rmdir(String path) throws IOException {
        LOG.trace("rmdir {}", path);
        Path dir = resolve(path);
        if (!Files.isDirectory(dir, NOFOLLOW_LINKS)) throw new NotDirectoryException(path);
        Files.delete(dir);
    }

    // Files
    public void create(String path, int mode, FileInfo fi) throws IOException {
        LOG.trace("create {}", path);
        createOrOpen(path, fi, true);
    }

    public void open(String path, FileInfo fi) throws IOException {
        LOG.trace("open {}", path);
        createOrOpen(path, fi, false);
    }

    private void createOrOpen(String path, FileInfo fi, boolean createIfMissing) throws IOException {
        if (fi.getFh() != 0) {
            LOG.warn("create() or open() called with FileInfo that already has fh={}", fi.getFh());
        }
        Path p = resolve(path);
        Set<StandardOpenOption> flags = enumSetCopy(fi.getOpenFlags(), StandardOpenOption.class);
        if (createIfMissing) {
            // Ensure we allow creation when create() is used
            flags.add(StandardOpenOption.CREATE);
        }
        if (LOG.isDebugEnabled()) {
            boolean writable = flags.contains(StandardOpenOption.WRITE)
                    || flags.contains(StandardOpenOption.APPEND)
                    || flags.contains(StandardOpenOption.TRUNCATE_EXISTING)
                    || flags.contains(StandardOpenOption.CREATE)
                    || flags.contains(StandardOpenOption.CREATE_NEW);
            LOG.debug("open/create {} backing {} flags={} writable={} createIfMissing={}",
                    path, p, flags, writable, createIfMissing);
        }
        FileChannel fc = null;
        boolean success = false;
        try {
            fc = FileChannel.open(p, flags);
            long fh = fileHandleGen.incrementAndGet();
            fi.setFh(fh);
            openFiles.put(fh, new OpenHandle(p, fc));
            LOG.trace("open ok {} fh={}", path, fh);
            success = true;
        } catch (IOException e) {
            LOG.warn("open/create failed for {} backing {} flags {}", path, p, flags, e);
            throw e;
        } finally {
            if (!success) {
                if (fc != null) {
                    try { fc.close(); } catch (IOException ignore) {}
                }
            }
        }
    }

    @Override
    public int read(String path, ByteBuffer buf, long size, long offset, FileInfo fi) throws IOException {
        LOG.trace("read {} at pos {}", path, offset);
        val h = openFiles.get(fi.getFh());
        if (h == null) throw new BadFileDescriptorException(path);
        int read = 0;
        int toRead = (int) Math.min(size, buf.remaining());
        while (read < toRead) {
            int r = h.fc.read(buf, offset + read);
            if (r == -1) break;
            read += r;
        }
        return read;
    }

    @Override
    public int write(String path, ByteBuffer buf, long size, long offset, FileInfo fi) throws IOException {
        LOG.trace("write {} at pos {}", path, offset);
        val h = openFiles.get(fi.getFh());
        if (h == null) throw new BadFileDescriptorException(path);
        int toWrite = (int) Math.min(size, buf.remaining());
        // Write only the requested amount without consuming more from the buffer
        ByteBuffer slice = buf.slice();
        slice.limit(toWrite);
        int written = 0;
        while (written < toWrite) {
            written += h.fc.write(slice, offset + written);
        }
        buf.position(buf.position() + toWrite);
        if (LOG.isTraceEnabled()) {
            LOG.trace("write ok {} fh={} wrote={} at offset={}", path, fi.getFh(), toWrite, offset);
        }
        return toWrite;
    }

    public void truncate(String path, long size, @Nullable FileInfo fi) throws IOException {
        LOG.trace("truncate {} to size {}", path, size);
        // `FuseOperations` says "This method doubles as ftruncate in libfuse2".
        // Is this correct in libfuse3?
        val h = fi != null ? openFiles.get(fi.getFh()) : null;
        if (h != null) {
            h.fc.truncate(size);
        } else {
            Path p = resolve(path);
            try (var fc = FileChannel.open(p, EnumSet.of(StandardOpenOption.WRITE))) {
                fc.truncate(size);
            }
        }
    }

    public void release(String path, FileInfo fi) {
        LOG.trace("release {}", path);
        val h = openFiles.remove(fi.getFh());
        if (h == null) return; // nothing to do
        try { h.fc.close(); } catch (IOException ignore) {}
    }

    public void unlink(String path) throws IOException {
        LOG.trace("unlink {}", path);
        Path p = resolve(path);
        if (Files.isDirectory(p, NOFOLLOW_LINKS)) throw new IsDirectoryException();
        Files.delete(p);
    }

    private static final int RENAME_NOREPLACE = 1;

    public void rename(String oldpath, String newpath, int flags) throws IOException {
        LOG.trace("rename {} -> {} flags=0x{}", oldpath, newpath, Integer.toHexString(flags));

        boolean noReplace = (flags & RENAME_NOREPLACE) != 0;
        // Reject any flag other than RENAME_NOREPLACE (namely, RENAME_EXCHANGE)
        int unsupported = flags & ~RENAME_NOREPLACE;
        if (unsupported != 0) {
            throw new UnsupportedOperationException("rename flags not supported: 0x" + Integer.toHexString(unsupported));
        }
        Path src = resolve(oldpath);
        Path dst = resolve(newpath);
        try {
            // Prefer atomic move when supported
            if (noReplace) {
                Files.move(src, dst, ATOMIC_MOVE);
            } else {
                Files.move(src, dst, ATOMIC_MOVE, REPLACE_EXISTING);
            }
        } catch (AtomicMoveNotSupportedException e) {
            // In theory, we should not catch this but return EXDEV to Fuse and
            // callers would handle the cross-device fallback. However:
            // - EXDEV is missing from Errno at this time
            // - I just saw an app that does not handle EXDEV (trashy)
            if (noReplace) {
                Files.move(src, dst);
            } else {
                Files.move(src, dst, REPLACE_EXISTING);
            }
        }
    }

    // Finish
    @Override
    public void destroy() {
        if (!openFiles.isEmpty()) {
            LOG.warn("Found unclosed files when unmounting...");
        }
        openFiles.values().forEach(h -> {
            try { h.fc.close(); } catch (IOException ignore) {}
        });
    }

    public void flush(String path, FileInfo fi) {
        LOG.trace("flush {}", path);
        // No-op; kernel may call close+fsync on its own
    }

    public void fsync(String path, int datasync, FileInfo fi) {
        LOG.trace("fsync {}", path);
    }

    public void fsyncdir(@Nullable String path, int datasync, FileInfo fi) {
        LOG.trace("fsyncdir {}", path);
    }
}
