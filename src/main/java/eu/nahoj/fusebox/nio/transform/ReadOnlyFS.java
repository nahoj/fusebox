package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.SetUtils;
import org.cryptomator.jfuse.api.FileInfo;
import org.cryptomator.jfuse.api.FuseOperations.Operation;
import org.cryptomator.jfuse.api.TimeSpec;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.CHMOD;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.CHOWN;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.CREATE;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.MKDIR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.REMOVE_XATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.RENAME;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.RMDIR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.SET_XATTR;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.SYMLINK;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.TRUNCATE;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.UNLINK;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.UTIMENS;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.WRITE;

@Accessors(fluent = true)
@RequiredArgsConstructor
public class ReadOnlyFS extends BaseFS implements DecoratedFS {

    @Getter
    private final FuseboxFS delegate;

    private final EnumSet<Operation> blockedOperations = EnumSet.noneOf(Operation.class);

    @Override
    public Set<Operation> supportedOperations() {
        return SetUtils.difference(delegate.supportedOperations(), blockedOperations);
    }

    private static final Set<PosixFilePermission> WRITE_PERMISSIONS =
            EnumSet.of(OWNER_WRITE, GROUP_WRITE, OTHERS_WRITE);

    @Override
    public FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        FileAttributes origAttr = delegate().getattr(path, fi);
        return origAttr.withPermissions(SetUtils.difference(origAttr.permissions(), WRITE_PERMISSIONS));
    }

    @Override
    public void access(String path, int mask) throws IOException {
        // Deny write access requests (POSIX W_OK = 2)
        if ((mask & 0b10) != 0) { // 2
            throw new ReadOnlyFileSystemException();
        }
        delegate().access(path, mask);
    }

    // Deny write-affecting operations on a read-only filesystem

    // Attributes
    { blockedOperations.add(SET_XATTR); }
    @Override
    public void setxattr(String path, String name, ByteBuffer value){
        throw new ReadOnlyFileSystemException();
    }

    { blockedOperations.add(REMOVE_XATTR); }
    @Override
    public void removexattr(String path, String name){
        throw new ReadOnlyFileSystemException();
    }

    { blockedOperations.add(CHMOD); }
    @Override
    public void chmod(String path, int mode, @Nullable FileInfo fi){
        throw new ReadOnlyFileSystemException();
    }

    { blockedOperations.add(CHOWN); }
    @Override
    public void chown(String path, int uid, int gid, @Nullable FileInfo fi){
        throw new ReadOnlyFileSystemException();
    }

    { blockedOperations.add(UTIMENS); }
    @Override
    public void utimens(String path, TimeSpec atime, TimeSpec mtime, @Nullable FileInfo fi) {
        throw new ReadOnlyFileSystemException();
    }

    // Links
    { blockedOperations.add(SYMLINK); }
    @Override
    public void symlink(String target, String linkname) {
        throw new ReadOnlyFileSystemException();
    }

    // Directories
    { blockedOperations.add(MKDIR); }
    @Override
    public void mkdir(String path, int mode){
        throw new ReadOnlyFileSystemException();
    }

    { blockedOperations.add(RMDIR); }
    @Override
    public void rmdir(String path){
        throw new ReadOnlyFileSystemException();
    }

    // Files
    { blockedOperations.add(CREATE); }
    @Override
    public void create(String path, int mode, FileInfo fi){
        throw new ReadOnlyFileSystemException();
    }

    private static final Set<StandardOpenOption> BLOCKED_OPEN_OPTIONS = EnumSet.of(
            StandardOpenOption.APPEND,
            StandardOpenOption.CREATE,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
    );

    @Override
    public void open(String path, FileInfo fi) throws IOException {
        // Reject write-intended modes
        if (!SetUtils.intersection(fi.getOpenFlags(), BLOCKED_OPEN_OPTIONS).isEmpty()) {
            throw new ReadOnlyFileSystemException();
        }
        delegate().open(path, fi);
    }

    { blockedOperations.add(WRITE); }
    @Override
    public int write(String path, ByteBuffer buf, long count, long offset, FileInfo fi){
        throw new ReadOnlyFileSystemException();
    }

    { blockedOperations.add(TRUNCATE); }
    @Override
    public void truncate(String path, long size, @Nullable FileInfo fi){
        throw new ReadOnlyFileSystemException();
    }

    { blockedOperations.add(UNLINK); }
    @Override
    public void unlink(String path){
        throw new ReadOnlyFileSystemException();
    }

    { blockedOperations.add(RENAME); }
    @Override
    public void rename(String oldPath, String newPath, int flags){
        throw new ReadOnlyFileSystemException();
    }
}
