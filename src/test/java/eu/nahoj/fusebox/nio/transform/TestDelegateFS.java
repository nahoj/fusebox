package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.DirEntry;
import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.FileType;
import eu.nahoj.fusebox.common.api.StatvfsData;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.Statvfs;
import org.cryptomator.jfuse.api.TimeSpec;
import org.springframework.lang.Nullable;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal in-memory FuseboxFS for unit tests.
 * - Allows presetting responses for getattr/readdir/statfs/xattr
 * - Records calls for verification
 */
class TestDelegateFS implements FuseboxFS {

    final List<String> calls = new ArrayList<>();

    final Map<String, FileAttributes> attrs = new HashMap<>();
    final Map<String, List<DirEntry>> dirEntries = new HashMap<>();

    final Map<String, String> xattrs = new HashMap<>();

    Set<Operation> supported = EnumSet.noneOf(Operation.class);
    Statvfs statvfs = StatvfsData.builder().build(); // not needed for current tests

    @Override
    public Set<Operation> supportedOperations() {
        return supported;
    }

    void setFile(String path, FileType type, int mode) {
        FileAttributes fa = FileAttributes.builder()
                .type(type)
                .permissions(org.cryptomator.jfuse.api.FileModes.toPermissions(mode))
                .uid(0).gid(0)
                .size(0)
                .lastAccessTime(Instant.now())
                .lastModifiedTime(Instant.now())
                .lastChangeTime(Instant.now())
                .build();
        attrs.put(path, fa);
    }

    void setDirEntries(String path, String... names) {
        dirEntries.put(path, Arrays.stream(names).map(DirEntry::new).toList());
    }

    @Override
    public Statvfs statfs(String path) {
        calls.add("statfs:" + path);
        return statvfs;
    }

    @Override
    public FileAttributes getattr(String path, @Nullable FileInfo fi) {
        calls.add("getattr:" + path);
        FileAttributes fa = attrs.get(path);
        if (fa == null) throw new IllegalArgumentException("No attrs for " + path);
        return fa;
    }

    @Override
    public String getxattr(String path, String name) {
        calls.add("getxattr:" + path + ":" + name);
        return xattrs.get(path + "@" + name);
    }

    @Override
    public void setxattr(String path, String name, ByteBuffer value) {
        calls.add("setxattr:" + path + ":" + name);
        xattrs.put(path + "@" + name, new String(value.array()));
    }

    @Override
    public List<String> listxattr(String path) {
        calls.add("listxattr:" + path);
        String prefix = path + "@";
        return xattrs.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> k.substring(prefix.length()))
                .toList();
    }

    @Override
    public void removexattr(String path, String name) {
        calls.add("removexattr:" + path + ":" + name);
        xattrs.remove(path + "@" + name);
    }

    @Override
    public void access(String path, int mask) {
        calls.add("access:" + path + ":" + mask);
    }

    @Override
    public void chmod(String path, int mode, @Nullable FileInfo fi) {
        calls.add("chmod:" + path + ":" + mode);
    }

    @Override
    public void chown(String path, int uid, int gid, @Nullable FileInfo fi) {
        calls.add("chown:" + path + ":" + uid + ":" + gid);
    }

    @Override
    public void utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) {
        calls.add("utimens:" + path);
    }

    @Override
    public String readlink(String path) {
        calls.add("readlink:" + path);
        return path; // echo
    }

    @Override
    public void symlink(String target, String linkname) {
        calls.add("symlink:" + linkname + "->" + target);
    }

    @Override
    public void mkdir(String path, int mode) {
        calls.add("mkdir:" + path);
    }

    @Override
    public void opendir(String path, FileInfo fi) {
        calls.add("opendir:" + path);
    }

    @Override
    public List<DirEntry> readdir(String path) {
        calls.add("readdir:" + path);
        return dirEntries.getOrDefault(path, List.of());
    }

    @Override
    public void releasedir(@Nullable String path, FileInfo fi) {
        calls.add("releasedir:" + path);
    }

    @Override
    public void rmdir(String path) {
        calls.add("rmdir:" + path);
    }

    @Override
    public void create(String path, int mode, FileInfo fi) {
        calls.add("create:" + path);
    }

    @Override
    public void open(String path, FileInfo fi) {
        calls.add("open:" + path);
    }

    @Override
    public int read(String path, ByteBuffer buf, long count, long offset, FileInfo fi) {
        calls.add("read:" + path);
        return 0;
    }

    @Override
    public int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi) {
        calls.add("write:" + path);
        return 0;
    }

    @Override
    public void truncate(String path, long size, @Nullable FileInfo fi) {
        calls.add("truncate:" + path);
    }

    @Override
    public void release(String path, FileInfo fi) {
        calls.add("release:" + path);
    }

    @Override
    public void unlink(String path) {
        calls.add("unlink:" + path);
    }

    @Override
    public void rename(String oldPath, String newPath, int flags) {
        calls.add("rename:" + oldPath + "->" + newPath);
    }

    @Override
    public void flush(String path, FileInfo fi) {
        calls.add("flush:" + path);
    }

    @Override
    public void fsync(String path, int datasync, FileInfo fi) {
        calls.add("fsync:" + path);
    }

    @Override
    public void fsyncdir(@Nullable String path, int datasync, FileInfo fi) {
        calls.add("fsyncdir:" + path);
    }
}
