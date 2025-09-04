package eu.nahoj.fusebox.common.api;

import lombok.Builder;
import lombok.With;
import org.apache.commons.collections4.SetUtils;
import org.cryptomator.jfuse.api.FileModes;
import org.springframework.lang.Nullable;

import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

@Builder
@With
public record FileAttributes(
        // Mode
        FileType type,
        boolean suid,
        boolean sgid,
        boolean sticky,
        Set<PosixFilePermission> permissions,
        // Other
        int uid,
        int gid,
        long size,
        @Nullable Instant creationTime,
        Instant lastAccessTime,
        Instant lastModifiedTime,
        Instant lastChangeTime
) {
    public static FileAttributes minimal(FileType type, long size) {
        Instant now = Instant.now();
        return FileAttributes.builder()
                .type(type)
                .suid(false)
                .sgid(false)
                .sticky(false)
                .permissions(FileModes.toPermissions(0555))
                .uid(0)
                .gid(0)
                .size(size)
                .lastAccessTime(now)
                .lastModifiedTime(now)
                .lastChangeTime(now)
                .build();
    }

    public boolean isDirectory() {
        return type == FileType.DIRECTORY;
    }

    public FileAttributes readOnly() {
        return this.withPermissions(
                SetUtils.difference(permissions, EnumSet.of(OWNER_WRITE, GROUP_WRITE, OTHERS_WRITE))
        );
    }

    public int mode() {
        return type.mask()
                | (suid ? 04000 : 0)
                | (sgid ? 02000 : 0)
                | (sticky ? 01000 : 0)
                | FileModes.fromPermissions(permissions);
    }
}
