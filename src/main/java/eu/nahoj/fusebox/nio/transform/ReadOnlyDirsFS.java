package eu.nahoj.fusebox.nio.transform;

import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.FileType;
import eu.nahoj.fusebox.nio.api.FuseboxFS;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.SetUtils;
import org.cryptomator.jfuse.api.FileInfo;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.AccessDeniedException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

@Accessors(fluent = true)
@RequiredArgsConstructor
public class ReadOnlyDirsFS extends BaseFS implements SelectivelyDecoratedFS {

    @Getter
    private final FuseboxFS delegate;

    ///  Selector for paths relative to the root (no leading slash)
    private final Predicate<String> pathSelector;

    private boolean isSelectedDir(String path) throws IOException {
        return pathSelector.test(path.substring(1))
                && delegate().getattr(path, null).type() == FileType.DIRECTORY;
    }

    /// Use decorated methods for children of selected directories
    @Override
    public boolean shouldDecorate(String path) {
        if ("/".equals(path)) return false; // I don't know what to do with root
        String parent = path.substring(1, Math.max(1, path.lastIndexOf('/')));
        return pathSelector.test(parent);
    }

    // Attributes
    private static final Set<PosixFilePermission> WRITE_PERMISSIONS =
            EnumSet.of(OWNER_WRITE, GROUP_WRITE, OTHERS_WRITE);
    @Override
    public FileAttributes getattr(String path, @Nullable FileInfo fi) throws IOException {
        FileAttributes origAttr = delegate().getattr(path, fi);
        return isSelectedDir(path)
                ? origAttr.withPermissions(SetUtils.difference(origAttr.permissions(), WRITE_PERMISSIONS))
                : origAttr;
    }

    @Override
    public void setxattr(String path, String name, ByteBuffer value) throws IOException {
        if (isSelectedDir(path)) {
            throw new AccessDeniedException(path);
        } else {
            delegate().setxattr(path, name, value);
        }
    }

    @Override
    public void decoratedRemovexattr(String path, String name) throws IOException {
        if (isSelectedDir(path)) {
            throw new AccessDeniedException(path);
        } else {
            delegate().removexattr(path, name);
        }
    }

    @Override
    public void access(String path, int mask) throws IOException {
        // Deny write access requests (POSIX W_OK = 2)
        if (isSelectedDir(path) && (mask & 02) != 0) {
            throw new AccessDeniedException(path);
        }
        delegate().access(path, mask);
    }

    // Links
    @Override
    public void decoratedSymlink(String target, String linkname) throws IOException {
        throw new AccessDeniedException(linkname);
    }

    // Directories
    @Override
    public void decoratedMkdir(String path, int mode) throws IOException {
        throw new AccessDeniedException(path);
    }

    @Override
    public void decoratedRmdir(String path) throws IOException {
        throw new AccessDeniedException(path);
    }

    // Files
    @Override
    public void decoratedCreate(String path, int mode, FileInfo fi) throws IOException {
        throw new AccessDeniedException(path);
    }

    @Override
    public void decoratedUnlink(String path) throws IOException {
        throw new AccessDeniedException(path);
    }

    @Override
    public void decoratedRename(String oldPath, String newPath, int flags) throws IOException {
        throw new AccessDeniedException(oldPath, newPath, null);
    }
}
