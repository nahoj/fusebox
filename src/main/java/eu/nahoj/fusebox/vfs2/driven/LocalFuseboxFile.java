package eu.nahoj.fusebox.vfs2.driven;

import eu.nahoj.fusebox.common.api.FileAttributes;
import eu.nahoj.fusebox.common.api.FileType;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.vfs2.FileObject;
import org.cryptomator.jfuse.api.FileModes;
import org.cryptomator.jfuse.api.FuseOperations.Operation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static eu.nahoj.fusebox.common.util.NullUtils.mapOrNull;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.cryptomator.jfuse.api.FuseOperations.Operation.READLINK;

/// Not named `LocalFile` to avoid confusion with vfs2's `LocalFile`.
@Accessors(fluent = true)
public class LocalFuseboxFile extends Vfs2File {

    public static final Set<Operation> IMPLEMENTED_OPERATIONS =
            SetUtils.union(Vfs2File.IMPLEMENTED_OPERATIONS, EnumSet.of(READLINK));

    @Getter
    private final LocalFS fs;

    public LocalFuseboxFile(LocalFS fs, Path path, FileObject fo) {
        super(fs, path, fo);
        this.fs = fs;
    }

    private Path getAbsolutePath() {
        return fo.getPath();
    }

    // Attributes

    private static final String ATTRIBUTE_KEYS = "unix:mode,uid,gid,size," +
            "creationTime,lastAccessTime,lastModifiedTime,ctime";

    @Override
    public FileAttributes getAttributes() throws IOException {
        Map<String, Object> attrs = Files.readAttributes(getAbsolutePath(), ATTRIBUTE_KEYS, NOFOLLOW_LINKS);
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

    @Override
    public void setPermissions(Set<PosixFilePermission> permissions) throws IOException {
        Files.setPosixFilePermissions(getAbsolutePath(), permissions);
    }

    // Links

    /// Read this symlink's target.
    /// The returned target is as stored in the link (often relative to the link's parent).
    @Override
    public String getTargetPath() throws IOException {
        return Files.readSymbolicLink(getAbsolutePath()).toString();
    }
}
