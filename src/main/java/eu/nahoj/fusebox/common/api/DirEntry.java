package eu.nahoj.fusebox.common.api;

import lombok.With;

@With
public record DirEntry(
        String name
        // Additional info could include:
        // - file type → st_mode
        // - not st_ino (handled automatically by JFuse?)
        // - all other Stat info (all or nothing), if requested with readdir flag, then set flag FUSE_FILL_DIR_PLUS
        // - offset
) {
}
