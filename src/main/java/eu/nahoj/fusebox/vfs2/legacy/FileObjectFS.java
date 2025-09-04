package eu.nahoj.fusebox.vfs2.legacy;

import eu.nahoj.fusebox.common.api.BadFileDescriptorException;
import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.FileType;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FileModes;
import org.cryptomator.jfuse.api.FuseConfig;
import org.cryptomator.jfuse.api.FuseConnInfo;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
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
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.DESTROY;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.GET_ATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.INIT;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.OPEN;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.READ;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.READ_DIR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.RELEASE;

@Slf4j
@RequiredArgsConstructor
public class FileObjectFS implements FuseboxFS {

    private static final Logger LOG = LoggerFactory.getLogger(FileObjectFS.class);

    private final FileObject root;

    private record OpenHandle(FileObject fo, FileChannel fc) {
    }

    private final ConcurrentMap<Long, OpenHandle> openFiles = new ConcurrentHashMap<>();
    private final AtomicLong fileHandleGen = new AtomicLong(1L);

    @Override
    public Set<Operation> supportedOperations() {
        return EnumSet.of(DESTROY, GET_ATTR, INIT, READ_DIR, OPEN, READ, RELEASE);
    }

    private FileObject resolve(String fusePath) {
        return resolve(fusePath, null);
    }

    private FileObject resolve(String fusePath, @Nullable FileInfo fi) {
        return Optional.ofNullable(fi)
                .map(fi_ -> openFiles.get(fi_.getFh()).fo())
                .orElseGet(uncheckedIO(() ->
                        root.resolveFile(StringUtils.stripStart(fusePath, "/"))));
    }

    // Start
    @Override
    public void init(FuseConnInfo conn, FuseConfig cfg) {
        conn.setWant(conn.want() | (conn.capable() & FuseConnInfo.FUSE_CAP_BIG_WRITES));
        conn.setMaxBackground(16);
        conn.setCongestionThreshold(4);
    }

    // Attributes
    private static final String ATTRIBUTE_KEYS = "unix:mode,uid,gid,size," +
            "creationTime,lastAccessTime,lastModifiedTime,ctime";
    public FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        LOG.trace("getattr {}", path);
        Path p = resolve(path, fi).getPath();
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

    // Directories
    public List<DirEntry> readdir(String path) throws IOException {
        LOG.trace("readdir {}", path);
        try (FileObject fo = resolve(path)) {
            if (!fo.isFolder()) throw new NotDirectoryException(path);
            List<DirEntry> result = new ArrayList<>();
            for (val child : fo.getChildren()) {
                result.add(new DirEntry(child.getName().getBaseName()));
            }
            return result;
        }
    }

    // Files
    public void open(String path, FileInfo fi) throws IOException {
        LOG.trace("open {}", path);
        createOrOpen(path, fi, false);
    }

    private void createOrOpen(String path, FileInfo fi, boolean createIfMissing) throws IOException {
        if (fi.getFh() != 0) {
            LOG.warn("create() or open() called with FileInfo that already has fh={}", fi.getFh());
        }
        FileObject fo = null;
        FileChannel fc = null;
        boolean success = false;
        Set<StandardOpenOption> flags = enumSetCopy(fi.getOpenFlags(), StandardOpenOption.class);
        if (createIfMissing) {
            // Ensure we allow creation when create() is used
            flags.add(StandardOpenOption.CREATE);
        }
        try {
            fo = resolve(path);
            if (LOG.isDebugEnabled()) {
                boolean writable = flags.contains(StandardOpenOption.WRITE)
                        || flags.contains(StandardOpenOption.APPEND)
                        || flags.contains(StandardOpenOption.TRUNCATE_EXISTING)
                        || flags.contains(StandardOpenOption.CREATE)
                        || flags.contains(StandardOpenOption.CREATE_NEW);
                try {
                    LOG.debug("open/create {} backing {} flags={} writable={} createIfMissing={}",
                            path, fo.getPath(), flags, writable, createIfMissing);
                } catch (Exception _) {
                    LOG.debug("open/create {} flags={} writable={} createIfMissing={}",
                            path, flags, writable, createIfMissing);
                }
            }
            // Open native FileChannel on the underlying POSIX path to avoid VFS content closing our stream.
            fc = FileChannel.open(fo.getPath(), flags);
            long fh = fileHandleGen.incrementAndGet();
            fi.setFh(fh);
            openFiles.put(fh, new OpenHandle(fo, fc));
            LOG.trace("open ok {} fh={}", path, fh);
            success = true;
        } catch (IOException e) {
            if (LOG.isWarnEnabled()) {
                try {
                    LOG.warn("open/create failed for {} backing {} flags {}", path, fo.getPath(), flags, e);
                } catch (Exception _) {
                    LOG.warn("open/create failed for {} flags {}", path, flags, e);
                }
            }
            throw e;
        } finally {
            if (!success) {
                if (fc != null) {
                    try { fc.close(); } catch (IOException ignore) {}
                }
                if (fo != null) {
                    try { fo.close(); } catch (IOException ignore) {}
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

    public void release(String path, FileInfo fi) {
        LOG.trace("release {}", path);
        val h = openFiles.remove(fi.getFh());
        if (h == null) return; // nothing to do
        try { h.fc.close(); } catch (IOException ignore) {}
        try { h.fo.close(); } catch (Exception ignore) {}
    }

    // Finish
    @Override
    public void destroy() {
        if (!openFiles.isEmpty()) {
            LOG.warn("Found unclosed files when unmounting...");
        }
        openFiles.forEach((_, h) -> {
            try { h.fc.close(); } catch (IOException ignore) {}
        });
    }

    public void flush(String path, FileInfo fi) {
        LOG.trace("flush {}", path);
        // No fsync equivalent in VFS; best-effort success
    }

    public void fsync(String path, int datasync, FileInfo fi) {
        LOG.trace("fsync {}", path);
    }

    public void fsyncdir(@Nullable String path, int datasync, FileInfo fi) {
        LOG.trace("fsyncdir {}", path);
    }
}
