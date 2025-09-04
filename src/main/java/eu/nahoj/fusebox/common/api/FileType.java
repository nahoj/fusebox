package eu.nahoj.fusebox.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * Enum representing Unix file types with their corresponding mode masks.
 * Provides type-safe file type handling instead of raw integer constants.
 */
@Accessors(fluent = true)
@AllArgsConstructor
@Getter
public enum FileType {
    FIFO(0010000),
    CHARACTER_DEVICE(0020000),
    DIRECTORY(0040000),
    BLOCK_DEVICE(0060000),
    REGULAR_FILE(0100000),
    SYMBOLIC_LINK(0120000),
    SOCKET(0140000);

    /// File type mask to extract file type from mode
    public static final int S_IFMT = 0170000;

    private final int mask;

    public static FileType fromMode(int mode) {
        int fileType = mode & S_IFMT;
        for (FileType type : values()) {
            if (type.mask == fileType) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown file type: " + fileType);
    }
}
