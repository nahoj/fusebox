package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.stream.Streams;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.Statvfs;
import org.cryptomator.jfuse.api.TimeSpec;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

@Accessors(fluent = true)
@AllArgsConstructor
public class RenamedFS extends BaseFS implements DecoratedFS {

    private static final Path ROOT_PATH = Path.of("/");

    @Getter
    private final FuseboxFS delegate;

    private final Predicate<String> origPathSelector;
    private final Predicate<String> mountPathSelector;

    private final UnaryOperator<String> fileNameToMount;
    private final UnaryOperator<String> fileNameToOrig;

    // ---------- Path mapping helpers ----------

    private Path mountPathToOrigPath(String mountPath) {
        // Rename ALL path prefixes whose mount path matches the selector.
        Path relMountPrefix = Path.of("");
        Path absOrigPrefix = ROOT_PATH;

        for (Path part : Path.of(mountPath).normalize()) {
            String name = part.toString();

            relMountPrefix = relMountPrefix.resolve(name);
            String origName = mountPathSelector.test(relMountPrefix.toString())
                    ? fileNameToOrig.apply(name)
                    : name;
            absOrigPrefix = absOrigPrefix.resolve(origName);
        }
        return absOrigPrefix;
    }

    private String mountPathToOrig(String mountPath) {
        return mountPathToOrigPath(mountPath).toString();
    }

    private String childFileNameToMount(Path relOrigDirPath, String origFileName) {
        Path origPath = relOrigDirPath.resolve(origFileName);
        return origPathSelector.test(origPath.toString())
                ? fileNameToMount.apply(origFileName)
                : origFileName;
    }

    // Start
    @Override
    public Statvfs statfs(String path) throws IOException {
        return delegate().statfs(mountPathToOrig(path));
    }

    // Attributes
    @Override
    public FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        LOG.trace("getattr({}) {}", path, mountPathToOrig(path));
        return delegate().getattr(mountPathToOrig(path), fi);
    }

    @Override
    public String getxattr(String path, String name) throws IOException {
        return delegate().getxattr(mountPathToOrig(path), name);
    }

    @Override
    public void setxattr(String path, String name, ByteBuffer value) throws IOException {
        delegate().setxattr(mountPathToOrig(path), name, value);
    }

    @Override
    public List<String> listxattr(String path) throws IOException {
        return delegate().listxattr(mountPathToOrig(path));
    }

    @Override
    public void removexattr(String path, String name) throws IOException {
        delegate().removexattr(mountPathToOrig(path), name);
    }

    @Override
    public void access(String path, int mask) throws IOException {
        delegate().access(mountPathToOrig(path), mask);
    }

    @Override
    public void chmod(String path, int mode, @Nullable FileInfo fi) throws IOException {
        delegate().chmod(mountPathToOrig(path), mode, fi);
    }

    @Override
    public void chown(String path, int uid, int gid, @Nullable FileInfo fi) throws IOException {
        delegate().chown(mountPathToOrig(path), uid, gid, fi);
    }

    @Override
    public void utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) throws IOException {
        delegate().utimens(mountPathToOrig(path), atime, mtime, fi);
    }

    // Links
    @Override
    public String readlink(String path) throws IOException {
        // TODO Delegate raw; transformation of the link content is ambiguous -> leave as-is
        return delegate().readlink(mountPathToOrig(path));
    }

    @Override
    public void symlink(String target, String linkpath) throws IOException {
        // TODO Only map the link location; keep target as provided by user
        delegate().symlink(target, mountPathToOrig(linkpath));
    }

    // Directories
    @Override
    public void mkdir(String path, int mode) throws IOException {
        delegate().mkdir(mountPathToOrig(path), mode);
    }

    @Override
    public void opendir(String path, FileInfo fi) throws IOException {
        delegate().opendir(mountPathToOrig(path), fi);
    }

    @Override
    public List<DirEntry> readdir(String path) throws IOException {
        LOG.trace("readdir({})", path);
        Path origPath = mountPathToOrigPath(path);
        Path relOrigPath = origPath.relativize(ROOT_PATH);
        List<DirEntry> dirEntries = Streams.of(delegate().readdir(origPath.toString()))
                .map(e -> e.withName(childFileNameToMount(relOrigPath, e.name())))
                .peek(e -> LOG.trace("entry: {}", e))
                .collect(toList());
        Set<String> duplicateNames = dirEntries.stream()
                .collect(groupingBy(DirEntry::name, counting()))
                .entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(toSet());
        if (!duplicateNames.isEmpty()) {
            throw new IllegalStateException("Duplicate entries: " + dirEntries);
        }
        return dirEntries;
    }

    @Override
    public void releasedir(@Nullable String path, FileInfo fi) throws IOException {
        delegate().releasedir(path == null ? null : mountPathToOrig(path), fi);
    }

    @Override
    public void rmdir(String path) throws IOException {
        delegate().rmdir(mountPathToOrig(path));
    }

    // Files
    @Override
    public void create(String path, int mode, FileInfo fi) throws IOException {
        delegate().create(mountPathToOrig(path), mode, fi);
    }

    @Override
    public void open(String path, FileInfo fi) throws IOException {
        delegate().open(mountPathToOrig(path), fi);
    }

    @Override
    public int read(String path, ByteBuffer buf, long size, long offset, FileInfo fi) throws IOException {
        return delegate().read(mountPathToOrig(path), buf, size, offset, fi);
    }

    @Override
    public int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) throws IOException {
        return delegate().write(mountPathToOrig(path), buf, count, offset, fi);
    }

    @Override
    public void truncate(String path, long size, @Nullable FileInfo fi) throws IOException {
        delegate().truncate(mountPathToOrig(path), size, fi);
    }

    @Override
    public void release(String path, FileInfo fi) throws IOException {
        delegate().release(mountPathToOrig(path), fi);
    }

    @Override
    public void unlink(String path) throws IOException {
        delegate().unlink(mountPathToOrig(path));
    }

    @Override
    public void rename(String oldPath, String newPath, int flags) throws IOException {
        delegate().rename(mountPathToOrig(oldPath), mountPathToOrig(newPath), flags);
    }

    // Finish
    @Override
    public void flush(String path, FileInfo fi) throws IOException {
        delegate().flush(mountPathToOrig(path), fi);
    }

    @Override
    public void fsync(String path, int datasync, FileInfo fi) throws IOException {
        delegate().fsync(mountPathToOrig(path), datasync, fi);
    }

    @Override
    public void fsyncdir(@Nullable String path, int datasync, FileInfo fi) throws IOException {
        delegate().fsyncdir(path == null ? null : mountPathToOrig(path), datasync, fi);
    }
}
